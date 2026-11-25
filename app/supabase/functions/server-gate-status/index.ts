// ==============================================================================
// SUPABASE EDGE FUNCTION: server-gate-status
// PURPOSE: Authoritative Server Gate Evaluation (Maintenance, Auth, Ban,
//          Subscription, Telegram Membership, Global File Updates, App Updates)
// ==============================================================================

import { serve } from "https://deno.land/std@0.177.0/http/server.ts";
import {
  authenticateRequest,
  ClientReportedFile,
  corsHeaders,
  enforceRateLimit,
  evaluateServerGate,
  getAdminClient,
  getClientIp,
  jsonResponse,
  checkClientIntegrity,
} from "../_shared/utils.ts";

serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const supabaseAdmin = getAdminClient();
    const ip = getClientIp(req);
    const { user, profile, isAdmin: isAdminUser } = await authenticateRequest(req, supabaseAdmin);

    const allowed = await enforceRateLimit(
      supabaseAdmin,
      `gate:${user?.id || ip}`,
      120,
      60
    );
    if (!allowed) {
      return jsonResponse({ error: "Rate limit exceeded. Please wait a moment." }, 429);
    }

    const body = req.method === "POST" ? await req.json().catch(() => ({})) : {};
    const clientFiles: ClientReportedFile[] = Array.isArray(body?.client_files)
      ? body.client_files
      : [];
    const clientVersionCode = Number(body?.app_version_code || 0);

    // Update user last_login_at / session telemetry if authenticated
    if (user) {
      await supabaseAdmin
        .from("user_profiles")
        .update({ last_login_at: new Date().toISOString() })
        .eq("id", user.id);

      if (body?.device_id) {
        await supabaseAdmin.from("user_sessions").insert({
          user_id: user.id,
          device_id: String(body.device_id).slice(0, 128),
          device_model: body?.device_model ? String(body.device_model).slice(0, 128) : null,
          app_version: body?.app_version ? String(body.app_version).slice(0, 32) : null,
          ip_address: ip,
          is_active: true,
        });
      }
    }

    const integrity = await checkClientIntegrity(
      supabaseAdmin,
      user,
      profile,
      isAdminUser,
      String(body?.device_id || ""),
      String(body?.device_model || ""),
      String(body?.app_sig || ""),
      ip,
      "server-gate-status"
    );
    const gateResult = await evaluateServerGate(
      supabaseAdmin,
      user,
      profile,
      clientFiles,
      clientVersionCode
    );

    if (!integrity.ok) {
      return jsonResponse({
        ...gateResult,
        can_execute_copy: false,
        block_reason_code: integrity.code,
        block_reason_ar: integrity.reason_ar,
        block_reason_en: integrity.reason_en,
        files: [],
        outdated_file_ids: [],
      }, 200);
    }

    return jsonResponse(gateResult, 200);
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : "Internal Server Error";
    return jsonResponse({ error: message }, 500);
  }
});
