// Telegram webhook for @Kharbash0bot: checks channel membership, sends a 6-digit login code.
import { serve } from "https://deno.land/std@0.177.0/http/server.ts";
import { getAdminClient, enforceRateLimit } from "../_shared/utils.ts";
import { codeHash, webhookSecret } from "../_shared/tg.ts";

const DEFAULT_CHANNEL = "@Kharbash_anas";
const DEFAULT_CHANNEL_URL = "https://t.me/Kharbash_anas";

async function tg(token: string, method: string, payload: Record<string, unknown>) {
  const r = await fetch(`https://api.telegram.org/bot${token}/${method}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  return await r.json().catch(() => ({ ok: false }));
}

function randomCode(): string {
  const a = new Uint32Array(1);
  crypto.getRandomValues(a);
  return String(a[0] % 1000000).padStart(6, "0");
}

serve(async (req: Request) => {
  const token = Deno.env.get("TELEGRAM_BOT_TOKEN");
  if (!token) return new Response("not configured", { status: 503 });
  if (req.headers.get("x-telegram-bot-api-secret-token") !== (await webhookSecret(token))) {
    return new Response("forbidden", { status: 403 });
  }
  let update: any;
  try { update = await req.json(); } catch { return new Response("ok"); }

  try {
    const admin = getAdminClient();
    const channel = Deno.env.get("TELEGRAM_CHANNEL_ID") || DEFAULT_CHANNEL;
    const cb = update.callback_query;
    const msg = update.message;
    const from = cb?.from || msg?.from;
    const chatId = cb?.message?.chat?.id ?? msg?.chat?.id;
    if (!from || !chatId || msg?.chat?.type && msg.chat.type !== "private") return new Response("ok");

    const text: string = (msg?.text || "").trim();
    const isCodeRequest = !!cb && cb.data === "get_code" || /^\/(code|login)/.test(text);

    if (cb) await tg(token, "answerCallbackQuery", { callback_query_id: cb.id });

    const keyboard = {
      inline_keyboard: [
        [{ text: "✅ تحقق من اشتراكي واحصل على الكود", callback_data: "get_code" }],
        [{ text: "📢 قناة التليجرام", url: DEFAULT_CHANNEL_URL }],
      ],
    };

    if (!isCodeRequest) {
      await tg(token, "sendMessage", {
        chat_id: chatId,
        text: "أهلاً بك 👋\nاشترك في القناة أولاً، ثم اضغط الزر بالأسفل لتحصل على كود الدخول للتطبيق.",
        reply_markup: keyboard,
      });
      return new Response("ok");
    }

    const ok = await enforceRateLimit(admin, `tgcode:${from.id}`, 5, 600);
    if (!ok) {
      await tg(token, "sendMessage", { chat_id: chatId, text: "محاولات كثيرة. انتظر بضع دقائق ثم حاول مرة أخرى." });
      return new Response("ok");
    }

    const m = await tg(token, "getChatMember", { chat_id: channel, user_id: from.id });
    const status = m?.result?.status;
    const isMember = m?.ok && ["creator", "administrator", "member"].includes(status);
    if (!isMember) {
      await tg(token, "sendMessage", {
        chat_id: chatId,
        text: "لازم تشترك في القناة أولاً. اشترك ثم اضغط الزر مرة أخرى.",
        reply_markup: keyboard,
      });
      return new Response("ok");
    }

    // Generate a unique 6-digit code (no other active code with the same value)
    const nowIso = new Date().toISOString();
    let code = "";
    let hash = "";
    for (let i = 0; i < 10; i++) {
      code = randomCode();
      hash = await codeHash(code, token);
      const { data: clash } = await admin
        .from("telegram_login_codes")
        .select("id").eq("code_hash", hash).is("used_at", null).gt("expires_at", nowIso).limit(1);
      if (!clash || clash.length === 0) break;
    }
    // Invalidate older unused codes of this user
    await admin.from("telegram_login_codes").update({ used_at: nowIso })
      .eq("telegram_user_id", from.id).is("used_at", null);
    await admin.from("telegram_login_codes").insert({
      telegram_user_id: from.id,
      telegram_username: from.username || null,
      telegram_first_name: from.first_name || null,
      code_hash: hash,
      expires_at: new Date(Date.now() + 10 * 60 * 1000).toISOString(),
    });

    await tg(token, "sendMessage", {
      chat_id: chatId,
      parse_mode: "HTML",
      text: `كود الدخول الخاص بك:\n\n<code>${code}</code>\n\nصالح لمدة 10 دقائق ويُستخدم مرة واحدة فقط. ارجع للتطبيق وأدخله.`,
    });
  } catch (e) {
    console.error("telegram-bot error", e);
  }
  return new Response("ok");
});
