// ==============================================================================
// SUPABASE EDGE FUNCTION: record-operation-audit
// PURPOSE: Records Client Installation Outcomes & Security Anomalies Server-Side
// ==============================================================================

import { serve } from "https://deno.land/std@0.177.0/http/server.ts";
import {
  authenticateRequest,
  corsHeaders,
  getAdminClient,
  getClientIp,
  jsonResponse,
  logSecurityEvent,
} from "../_shared/utils.ts";

serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const supabaseAdmin = getAdminClient();
    const ip = getClientIp(req);
    const { user } = await authenticateRequest(req, supabaseAdmin);

    if (!user) {
      return jsonResponse({ error: "Authentication required" }, 401);
    }

    const body = await req.json().catch(() => ({}));
    const action = String(body?.action || "PUBG_INSTALL_RESULT").slice(0, 100);
    const status = String(body?.status || "INFO").slice(0, 32);
    const details = body?.details && typeof body.details === "object" ? body.details : {};

    await supabaseAdmin.from("user_activity_logs").insert({
      user_id: user.id,
      action,
      details: { status, ...details },
      ip_address: ip,
    });

    if (status === "TAMPER_ALERT" || action === "SHA256_MISMATCH") {
      await logSecurityEvent(
        supabaseAdmin,
        "CLIENT_HASH_MISMATCH",
        "high",
        user.id,
        ip,
        "record-operation-audit",
        "Client reported SHA-256 verification failure",
        details
      );
    }

    return jsonResponse({ success: true });
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : "Internal Server Error";
    return jsonResponse({ error: message }, 500);
  }
});
