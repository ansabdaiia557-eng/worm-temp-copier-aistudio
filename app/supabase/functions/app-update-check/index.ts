// ==============================================================================
// SUPABASE EDGE FUNCTION: app-update-check
// PURPOSE: Separate Android APK Release & Update Verification Endpoint
// ==============================================================================

import { serve } from "https://deno.land/std@0.177.0/http/server.ts";
import { corsHeaders, getAdminClient, jsonResponse } from "../_shared/utils.ts";

serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const supabaseAdmin = getAdminClient();
    const body = req.method === "POST" ? await req.json().catch(() => ({})) : {};
    const currentVersionCode = Number(body?.current_version_code || 0);

    const { data: releases, error } = await supabaseAdmin
      .from("app_updates")
      .select("*")
      .eq("is_active", true)
      .order("version_code", { ascending: false })
      .limit(1);

    if (error) {
      return jsonResponse({ error: error.message }, 500);
    }

    const latest = releases && releases.length > 0 ? releases[0] : null;
    if (!latest) {
      return jsonResponse({ update_available: false, latest: null });
    }

    let resolvedDownloadUrl = latest.download_url;
    if (latest.apk_storage_path) {
      const { data: signed } = await supabaseAdmin.storage
        .from("worm-app-releases")
        .createSignedUrl(latest.apk_storage_path, 3600);
      if (signed?.signedUrl) {
        resolvedDownloadUrl = signed.signedUrl;
      }
    }

    const updateAvailable = latest.version_code > currentVersionCode;

    return jsonResponse({
      update_available: updateAvailable,
      is_mandatory: updateAvailable && Boolean(latest.is_mandatory),
      latest: {
        ...latest,
        download_url: resolvedDownloadUrl,
      },
    });
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : "Internal Server Error";
    return jsonResponse({ error: message }, 500);
  }
});
