// ==============================================================================
// SUPABASE EDGE FUNCTION: get-download-url
// PURPOSE: Zero-Trust Signed URL Generator for Private Content Storage
// SECURITY: Verifies auth, ban, maintenance, subscription, Telegram membership,
//           file group eligibility, and generates a 1-hour Signed URL.
// ==============================================================================

import { serve } from "https://deno.land/std@0.177.0/http/server.ts";
import {
  corsHeaders,
  jsonResponse,
  getAdminClient,
  authenticateRequest,
  enforceRateLimit,
  logSecurityEvent,
  getClientIp,
} from "../_shared/utils.ts";

serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const supabaseAdmin = getAdminClient();
    const clientIp = getClientIp(req);
    const { user, profile, isAdmin } = await authenticateRequest(req, supabaseAdmin);

    if (!user) {
      return jsonResponse(
        {
          success: false,
          code: "AUTH_REQUIRED",
          error: "Authentication required to request server file downloads",
        },
        401
      );
    }

    const allowedRate = await enforceRateLimit(
      supabaseAdmin,
      `download:${user.id}:${clientIp}`,
      40,
      60
    );
    if (!allowedRate) {
      await logSecurityEvent(
        supabaseAdmin,
        "RATE_LIMIT_EXCEEDED",
        "medium",
        user.id,
        clientIp,
        "get-download-url",
        "Too many file download URL requests"
      );
      return jsonResponse({ success: false, code: "RATE_LIMITED", error: "Too many requests" }, 429);
    }

    // 1. Check account ban
    const isBanned =
      Boolean(profile?.is_banned) ||
      (profile?.account_status && profile.account_status !== "active");
    if (isBanned && !isAdmin) {
      await logSecurityEvent(
        supabaseAdmin,
        "BANNED_DOWNLOAD_ATTEMPT",
        "high",
        user.id,
        clientIp,
        "get-download-url",
        "Banned user attempted to fetch signed download URL"
      );
      return jsonResponse(
        {
          success: false,
          code: "ACCOUNT_BANNED",
          error: profile?.ban_reason || "Account is banned",
        },
        403
      );
    }

    // 2. Load server rules & check maintenance
    const { data: sysRow } = await supabaseAdmin
      .from("system_status")
      .select("*")
      .eq("id", "00000000-0000-0000-0000-000000000001")
      .maybeSingle();

    const { data: maintActive } = await supabaseAdmin.rpc("is_maintenance_active");
    if (maintActive === true && !isAdmin) {
      return jsonResponse(
        {
          success: false,
          code: "MAINTENANCE_ACTIVE",
          error: sysRow?.maintenance_reason_en || "Server is currently under maintenance",
          error_ar: sysRow?.maintenance_reason_ar || "الخادم في وضع الصيانة المؤقتة حالياً",
        },
        503
      );
    }

    // 3. Check subscription if required
    const requireSub = sysRow?.require_active_subscription ?? true;
    const nowMs = Date.now();
    let hasActiveSub = false;
    const { data: subs } = await supabaseAdmin
      .from("subscriptions")
      .select("id, status, starts_at, ends_at")
      .eq("user_id", user.id)
      .eq("status", "active")
      .order("ends_at", { ascending: false })
      .limit(5);

    if (subs && subs.length > 0) {
      hasActiveSub = subs.some(
        (s) => new Date(s.starts_at).getTime() <= nowMs && new Date(s.ends_at).getTime() > nowMs
      );
    }

    if (requireSub && !hasActiveSub && !isAdmin) {
      return jsonResponse(
        {
          success: false,
          code: "SUBSCRIPTION_REQUIRED",
          error: "Active subscription required to download server files",
        },
        403
      );
    }

    // 4. Check Telegram membership if required
    const requireTelegram = sysRow?.require_telegram_membership ?? true;
    if (requireTelegram && !profile?.telegram_verified && !isAdmin) {
      return jsonResponse(
        {
          success: false,
          code: "TELEGRAM_REQUIRED",
          error: "Telegram channel membership verification required",
        },
        403
      );
    }

    // 5. Parse requested content item
    const body = await req.json().catch(() => ({}));
    const { content_id, storage_path } = body;

    if (!content_id && !storage_path) {
      return jsonResponse(
        { success: false, error: "Missing required parameter: content_id or storage_path" },
        400
      );
    }

    let query = supabaseAdmin.from("content_items").select("*");
    if (content_id) {
      query = query.eq("id", content_id);
    } else {
      query = query.eq("storage_path", storage_path);
    }

    const { data: item, error: dbError } = await query.maybeSingle();
    if (dbError || !item) {
      return jsonResponse({ success: false, error: "Content item not found" }, 404);
    }

    // 6. Verify published & target group eligibility
    if (!isAdmin && (!item.is_published || item.is_draft)) {
      return jsonResponse(
        { success: false, error: "Access denied: Content package is not published" },
        403
      );
    }

    if (!isAdmin) {
      if (item.target_group === "vip" && !hasActiveSub) {
        return jsonResponse(
          { success: false, error: "Access denied: VIP subscription required" },
          403
        );
      }
      if (
        item.target_group === "specific_users" &&
        (!Array.isArray(item.assigned_user_ids) || !item.assigned_user_ids.includes(user.id))
      ) {
        return jsonResponse(
          { success: false, error: "Access denied: File not assigned to this account" },
          403
        );
      }
    }

    // 7. Create 1-hour Signed URL from private bucket
    const expiresInSeconds = 3600;
    const { data: signedData, error: storageError } = await supabaseAdmin.storage
      .from("worm-content-files")
      .createSignedUrl(item.storage_path, expiresInSeconds);

    if (storageError || !signedData?.signedUrl) {
      return jsonResponse(
        {
          success: false,
          error: `Storage signature failed: ${storageError?.message || "Unknown error"}`,
        },
        500
      );
    }

    // 8. Update download counter & user activity log
    await supabaseAdmin
      .from("content_items")
      .update({ downloads_count: (item.downloads_count || 0) + 1 })
      .eq("id", item.id);

    await supabaseAdmin.from("user_activity_logs").insert({
      user_id: user.id,
      action: "REQUEST_SIGNED_DOWNLOAD",
      details: {
        content_id: item.id,
        title: item.title,
        version: item.version,
        version_code: item.version_code,
      },
      ip_address: clientIp,
    });

    return jsonResponse({
      success: true,
      content_id: item.id,
      title: item.title,
      target_filename: item.target_filename,
      target_relative_path: item.target_relative_path || "",
      version: item.version,
      version_code: item.version_code || 1,
      sha256: item.sha256,
      size_bytes: item.size_bytes,
      signed_url: signedData.signedUrl,
      expires_in_seconds: expiresInSeconds,
      generated_at: new Date().toISOString(),
    });
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : "Internal Server Error";
    return jsonResponse({ success: false, error: message }, 500);
  }
});
