// Exchanges a 6-digit code (from the Telegram bot) for a real Supabase session.
import { serve } from "https://deno.land/std@0.177.0/http/server.ts";
import { corsHeaders, jsonResponse, getAdminClient, enforceRateLimit, getClientIp } from "../_shared/utils.ts";
import { codeHash } from "../_shared/tg.ts";

serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  try {
    const token = Deno.env.get("TELEGRAM_BOT_TOKEN");
    if (!token) return jsonResponse({ success: false, code: "BOT_NOT_CONFIGURED", error: "Bot not configured" }, 503);
    const admin = getAdminClient();
    const ip = getClientIp(req);
    if (!(await enforceRateLimit(admin, `tglogin:${ip}`, 10, 600))) {
      return jsonResponse({ success: false, code: "RATE_LIMITED", error: "Too many attempts" }, 429);
    }
    const body = await req.json().catch(() => ({}));
    const code = String(body.code || "").trim();
    if (!/^\d{6}$/.test(code)) {
      return jsonResponse({ success: false, code: "BAD_CODE", error: "Invalid code" }, 400);
    }
    const hash = await codeHash(code, token);
    const nowIso = new Date().toISOString();
    // Atomically consume: only one request can flip used_at from null
    const { data: rows } = await admin
      .from("telegram_login_codes")
      .select("*").eq("code_hash", hash).is("used_at", null).gt("expires_at", nowIso).limit(1);
    const row = rows?.[0];
    if (!row) return jsonResponse({ success: false, code: "BAD_CODE", error: "Invalid or expired code" }, 400);
    const { data: consumed } = await admin
      .from("telegram_login_codes").update({ used_at: nowIso })
      .eq("id", row.id).is("used_at", null).select("id");
    if (!consumed || consumed.length === 0) {
      return jsonResponse({ success: false, code: "BAD_CODE", error: "Invalid or expired code" }, 400);
    }

    const tgId = Number(row.telegram_user_id);
    const display = row.telegram_username ? `@${row.telegram_username}` : (row.telegram_first_name || `tg${tgId}`);
    const email = `tg${tgId}@tg.invalid`;

    const { data: existing } = await admin
      .from("user_profiles").select("id").eq("telegram_user_id", tgId).limit(1);
    let userId = existing?.[0]?.id as string | undefined;
    if (!userId) {
      const { data: created, error: cErr } = await admin.auth.admin.createUser({
        email, email_confirm: true,
        user_metadata: { display_name: display, telegram_user_id: tgId },
      });
      if (cErr || !created?.user) {
        // user may exist already (profile row missing telegram id)
        const { data: list } = await admin.auth.admin.listUsers({ page: 1, perPage: 1000 });
        userId = list?.users?.find((u) => u.email === email)?.id;
        if (!userId) throw cErr || new Error("create user failed");
      } else {
        userId = created.user.id;
      }
    }
    await admin.from("user_profiles").update({
      telegram_user_id: tgId,
      telegram_username: row.telegram_username || null,
      telegram_verified: true,
      telegram_verified_at: nowIso,
      display_name: display,
      updated_at: nowIso,
    }).eq("id", userId);

    const { data: prof } = await admin.from("user_profiles").select("is_banned, account_status").eq("id", userId).maybeSingle();
    if (prof?.is_banned || prof?.account_status === "banned") {
      return jsonResponse({ success: false, code: "ACCOUNT_BANNED", error: "Account is banned" }, 403);
    }

    const { data: au } = await admin.auth.admin.getUserById(userId!);
    const loginEmail = au?.user?.email || email;
    const { data: link, error: lErr } = await admin.auth.admin.generateLink({ type: "magiclink", email: loginEmail });
    if (lErr || !link?.properties?.hashed_token) throw lErr || new Error("link failed");

    const r = await fetch(`${Deno.env.get("SUPABASE_URL")}/auth/v1/verify`, {
      method: "POST",
      headers: { "Content-Type": "application/json", apikey: Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")! },
      body: JSON.stringify({ type: "magiclink", token_hash: link.properties.hashed_token }),
    });
    const session = await r.json();
    if (!r.ok || !session.access_token) throw new Error("session mint failed");

    await admin.from("user_activity_logs").insert({
      user_id: userId, action: "TELEGRAM_CODE_LOGIN", details: { telegram_user_id: tgId },
    });

    return jsonResponse({
      success: true,
      access_token: session.access_token,
      refresh_token: session.refresh_token,
      expires_in: session.expires_in,
      user: { id: userId, email: loginEmail },
    });
  } catch (e) {
    console.error("telegram-login error", e);
    return jsonResponse({ success: false, code: "SERVER_ERROR", error: "Login failed" }, 500);
  }
});
