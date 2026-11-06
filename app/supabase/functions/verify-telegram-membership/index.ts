// ==============================================================================
// SUPABASE EDGE FUNCTION: verify-telegram-membership
// PURPOSE: Server-side Telegram Channel Subscription Verification
// SECURITY: Bot Token resides strictly in server environment secrets (TELEGRAM_BOT_TOKEN)
// ==============================================================================

import { serve } from "https://deno.land/std@0.177.0/http/server.ts";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

// Official target channel username / ID
const DEFAULT_CHANNEL_USERNAME = "@Kharbash_anas";

serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const botToken = Deno.env.get("TELEGRAM_BOT_TOKEN");
    const channelId = Deno.env.get("TELEGRAM_CHANNEL_ID") || DEFAULT_CHANNEL_USERNAME;

    if (!botToken) {
      throw new Error("Server configuration missing: TELEGRAM_BOT_TOKEN");
    }

    const body = await req.json().catch(() => ({}));
    const { telegram_user_id } = body;

    if (!telegram_user_id) {
      return new Response(
        JSON.stringify({ 
          success: false, 
          is_member: false, 
          error: "Missing required parameter: telegram_user_id" 
        }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // Call official Telegram Bot API getChatMember
    const telegramApiUrl = `https://api.telegram.org/bot${botToken}/getChatMember?chat_id=${encodeURIComponent(
      channelId
    )}&user_id=${encodeURIComponent(telegram_user_id)}`;

    const tgResponse = await fetch(telegramApiUrl);
    const tgData = await tgResponse.json();

    if (!tgData.ok) {
      return new Response(
        JSON.stringify({
          success: false,
          is_member: false,
          error: `Telegram API error: ${tgData.description || "Unable to verify member"}`,
        }),
        { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const status = tgData.result?.status;
    // Valid subscribed statuses: creator, administrator, member
    const isMember = status === "creator" || status === "administrator" || status === "member";

    return new Response(
      JSON.stringify({
        success: true,
        is_member: isMember,
        status: status,
        channel: channelId,
        verified_at: new Date().toISOString(),
      }),
      { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : "Internal Server Error";
    return new Response(
      JSON.stringify({ success: false, is_member: false, error: message }),
      { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }
});
