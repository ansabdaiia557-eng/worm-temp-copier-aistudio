// ==============================================================================
// SUPABASE EDGE FUNCTION: redeem-activation-code
// PURPOSE: Secure Server-Side Subscription Activation Code Redemption
// ==============================================================================

import { serve } from "https://deno.land/std@0.177.0/http/server.ts";
import {
  authenticateRequest,
  corsHeaders,
  enforceRateLimit,
  getAdminClient,
  getClientIp,
  jsonResponse,
  logSecurityEvent,
} from "../_shared/utils.ts";
import { processReferralReward } from "../_shared/referral.ts";

serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const supabaseAdmin = getAdminClient();
    const ip = getClientIp(req);
    const { user, profile } = await authenticateRequest(req, supabaseAdmin);

    if (!user) {
      return jsonResponse({ success: false, error: "Authentication required" }, 401);
    }

    const allowed = await enforceRateLimit(
      supabaseAdmin,
      `redeem:${user.id}:${ip}`,
      10,
      300
    );
    if (!allowed) {
      await logSecurityEvent(
        supabaseAdmin,
        "RATE_LIMIT_REDEEM",
        "medium",
        user.id,
        ip,
        "redeem-activation-code",
        "Repeated activation code attempts triggered rate limit"
      );
      return jsonResponse({ success: false, error: "Too many attempts. Please wait 5 minutes." }, 429);
    }

    if (profile?.is_banned || profile?.account_status === "banned") {
      return jsonResponse({ success: false, error: "Account is banned" }, 403);
    }

    const body = await req.json().catch(() => ({}));
    const rawCode = String(body?.code || "").trim().toUpperCase();
    if (!rawCode || rawCode.length < 4) {
      return jsonResponse({ success: false, error: "Invalid activation code format" }, 400);
    }

    const { data: codeRow, error: codeErr } = await supabaseAdmin
      .from("activation_codes")
      .select("*")
      .eq("code", rawCode)
      .maybeSingle();

    if (codeErr || !codeRow) {
      await logSecurityEvent(
        supabaseAdmin,
        "INVALID_ACTIVATION_CODE",
        "low",
        user.id,
        ip,
        "redeem-activation-code",
        `Unknown code attempted: ${rawCode.slice(0, 4)}***`
      );
      return jsonResponse({ success: false, error: "Activation code not found" }, 404);
    }

    if (!codeRow.is_active) {
      await supabaseAdmin.from("activation_logs").insert({
        activation_code_id: codeRow.id,
        code_snapshot: rawCode,
        user_id: user.id,
        status: "rejected_disabled",
        reason: "Code disabled by admin",
        ip_address: ip,
      });
      return jsonResponse({ success: false, error: "This activation code has been disabled" }, 400);
    }

    if (codeRow.expires_at && new Date(codeRow.expires_at).getTime() <= Date.now()) {
      await supabaseAdmin.from("activation_logs").insert({
        activation_code_id: codeRow.id,
        code_snapshot: rawCode,
        user_id: user.id,
        status: "rejected_expired",
        reason: "Code expired",
        ip_address: ip,
      });
      return jsonResponse({ success: false, error: "Activation code has expired" }, 400);
    }

    if (codeRow.current_uses >= codeRow.max_uses) {
      await supabaseAdmin.from("activation_logs").insert({
        activation_code_id: codeRow.id,
        code_snapshot: rawCode,
        user_id: user.id,
        status: "rejected_used",
        reason: "Max uses reached",
        ip_address: ip,
      });
      return jsonResponse({ success: false, error: "Activation code has reached maximum uses" }, 400);
    }

    // Prevent same user from redeeming the exact same code twice
    const { data: priorUse } = await supabaseAdmin
      .from("activation_logs")
      .select("id")
      .eq("activation_code_id", codeRow.id)
      .eq("user_id", user.id)
      .eq("status", "success")
      .maybeSingle();

    if (priorUse) {
      await supabaseAdmin.from("activation_logs").insert({
        activation_code_id: codeRow.id,
        code_snapshot: rawCode,
        user_id: user.id,
        status: "rejected_duplicate",
        reason: "User already redeemed this code",
        ip_address: ip,
      });
      return jsonResponse({ success: false, error: "You have already redeemed this code" }, 400);
    }

    // Compute server-side subscription start and end dates
    const now = new Date();
    const { data: activeSub } = await supabaseAdmin
      .from("subscriptions")
      .select("*")
      .eq("user_id", user.id)
      .eq("status", "active")
      .gt("ends_at", now.toISOString())
      .order("ends_at", { ascending: false })
      .limit(1)
      .maybeSingle();

    const baseDate = activeSub ? new Date(activeSub.ends_at) : now;
    const endsAt = new Date(baseDate.getTime() + codeRow.duration_days * 24 * 60 * 60 * 1000);

    // Atomic conditional increment to prevent race condition on max_uses
    const { data: updatedCode, error: incErr } = await supabaseAdmin
      .from("activation_codes")
      .update({ current_uses: codeRow.current_uses + 1 })
      .eq("id", codeRow.id)
      .lt("current_uses", codeRow.max_uses)
      .select()
      .maybeSingle();

    if (incErr || !updatedCode) {
      return jsonResponse({ success: false, error: "Code exhausted during redemption. Try another code." }, 409);
    }

    let subResult;
    if (activeSub) {
      const { data: updSub } = await supabaseAdmin
        .from("subscriptions")
        .update({
          ends_at: endsAt.toISOString(),
          activation_code_id: codeRow.id,
        })
        .eq("id", activeSub.id)
        .select()
        .single();
      subResult = updSub;
    } else {
      const { data: insSub } = await supabaseAdmin
        .from("subscriptions")
        .insert({
          user_id: user.id,
          activation_code_id: codeRow.id,
          plan_name: codeRow.description || `PRO ${codeRow.duration_days}D`,
          status: "active",
          starts_at: now.toISOString(),
          ends_at: endsAt.toISOString(),
        })
        .select()
        .single();
      subResult = insSub;
    }

    await supabaseAdmin.from("activation_logs").insert({
      activation_code_id: codeRow.id,
      code_snapshot: rawCode,
      user_id: user.id,
      subscription_id: subResult?.id || null,
      status: "success",
      reason: `Activated for ${codeRow.duration_days} days`,
      ip_address: ip,
    });

    await supabaseAdmin.from("user_activity_logs").insert({
      user_id: user.id,
      action: "SUBSCRIPTION_ACTIVATED",
      details: { duration_days: codeRow.duration_days, ends_at: endsAt.toISOString() },
      ip_address: ip,
    });

    await processReferralReward(supabaseAdmin, user.id);

    return jsonResponse({
      success: true,
      subscription: subResult,
      message: `Subscription active until ${endsAt.toISOString()}`,
    });
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : "Internal Server Error";
    return jsonResponse({ success: false, error: message }, 500);
  }
});
