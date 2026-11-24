// ==============================================================================
// SUPABASE EDGE FUNCTION: verify-telegram-membership
// PURPOSE: Real Server-Side Telegram Channel Membership Verification
// SECURITY: Uses TELEGRAM_BOT_TOKEN server secret; updates user_profiles state
// ==============================================================================

import { serve } from "https://deno.land/std@0.177.0/http/server.ts";
import {
  corsHeaders,
  jsonResponse,
  getAdminClient,
  authenticateRequest,
  enforceRateLimit,
  getClientIp,
} from "../_shared/utils.ts";

const DEFAULT_CHANNEL_USERNAME = "@Kharbash_anas";
const DEFAULT_CHANNEL_URL = "https://t.me/Kharbash_anas";

serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const supabaseAdmin = getAdminClient();
    const clientIp = getClientIp(req);
    const { user, profile } = await authenticateRequest(req, supabaseAdmin);

    if (!user) {
      return jsonResponse(
        { success: false, is_member: false, code: "AUTH_REQUIRED", error: "Authentication required" },
        401
      );
    }

    if (profile?.is_banned || profile?.account_status === "banned") {
      return jsonResponse(
        { success: false, is_member: false, code: "ACCOUNT_BANNED", error: "Account is banned" },
        403
      );
    }

    const allowedRate = await enforceRateLimit(
      supabaseAdmin,
      `telegram:${user?.id || clientIp}`,
      15,
      60
    );
    if (!allowedRate) {
      return jsonResponse(
        { success: false, is_member: false, error: "Too many verification attempts. Please wait." },
        429
      );
    }

    const botToken = Deno.env.get("TELEGRAM_BOT_TOKEN");
    const { data: sysRow } = await supabaseAdmin
      .from("system_status")
      .select("telegram_channel_username, telegram_channel_url")
      .eq("id", "00000000-0000-0000-0000-000000000001")
      .maybeSingle();

    const channelId =
      sysRow?.telegram_channel_username ||
      Deno.env.get("TELEGRAM_CHANNEL_ID") ||
      DEFAULT_CHANNEL_USERNAME;
    const channelUrl = sysRow?.telegram_channel_url || DEFAULT_CHANNEL_URL;

    if (!botToken) {
      return jsonResponse(
        {
          success: false,
          is_member: false,
          code: "TELEGRAM_BOT_NOT_CONFIGURED",
          channel_username: channelId,
          channel_url: channelUrl,
          error: "TELEGRAM_BOT_TOKEN secret is not configured on the server.",
        },
        503
      );
    }

    const body = await req.json().catch(() => ({}));
    const rawTelegramUserId = body.telegram_user_id;
    const telegramUserIdStr = rawTelegramUserId !== undefined && rawTelegramUserId !== null
      ? String(rawTelegramUserId).trim()
      : "";

    if (!telegramUserIdStr || !/^\d{5,20}$/.test(telegramUserIdStr)) {
      return jsonResponse(
        {
          success: false,
          is_member: false,
          error: "Valid numeric telegram_user_id is required.",
        },
        400
      );
    }

    const telegramApiUrl =
      `https://api.telegram.org/bot${botToken}/getChatMember` +
      `?chat_id=${encodeURIComponent(channelId)}&user_id=${encodeURIComponent(telegramUserIdStr)}`;

    const tgResponse = await fetch(telegramApiUrl);
    const tgData = await tgResponse.json();

    if (!tgData.ok) {
      return jsonResponse({
        success: false,
        is_member: false,
        channel_username: channelId,
        channel_url: channelUrl,
        error: `Telegram verification error: ${tgData.description || "User not found in channel"}`,
      });
    }

    const status = tgData.result?.status;
    const isMember = status === "creator" || status === "administrator" || status === "member";
    const nowIso = new Date().toISOString();

    if (user) {
      await supabaseAdmin
        .from("user_profiles")
        .update({
          telegram_user_id: Number(telegramUserIdStr),
          telegram_verified: isMember,
          telegram_verified_at: isMember ? nowIso : null,
          updated_at: nowIso,
        })
        .eq("id", user.id);

      await supabaseAdmin.from("user_activity_logs").insert({
        user_id: user.id,
        action: isMember ? "TELEGRAM_VERIFIED" : "TELEGRAM_CHECK_FAILED",
        details: {
          telegram_user_id: telegramUserIdStr,
          channel: channelId,
          chat_member_status: status,
        },
        ip_address: clientIp,
      });
    }

    return jsonResponse({
      success: true,
      is_member: isMember,
      status,
      channel_username: channelId,
      channel_url: channelUrl,
      verified_at: nowIso,
    });
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : "Internal Server Error";
    return jsonResponse({ success: false, is_member: false, error: message }, 500);
  }
});
