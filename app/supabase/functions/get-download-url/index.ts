// ==============================================================================
// SUPABASE EDGE FUNCTION: get-download-url
// PURPOSE: Secure Time-Limited Signed URL Generator for Private Content Storage
// SECURITY: Service Role Key executes strictly server-side; validates published status
// ==============================================================================

import { serve } from "https://deno.land/std@0.177.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.39.0";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

serve(async (req: Request) => {
  // Handle CORS preflight
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const supabaseUrl = Deno.env.get("SUPABASE_URL");
    const supabaseServiceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");

    if (!supabaseUrl || !supabaseServiceRoleKey) {
      throw new Error("Server configuration missing: SUPABASE_URL or SUPABASE_SERVICE_ROLE_KEY");
    }

    // Initialize elevated server-side client (never exposed to client apps)
    const supabaseAdmin = createClient(supabaseUrl, supabaseServiceRoleKey);

    const body = await req.json().catch(() => ({}));
    const { content_id, storage_path } = body;

    if (!content_id && !storage_path) {
      return new Response(
        JSON.stringify({ error: "Missing required parameter: content_id or storage_path" }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // Optional user token inspection (to allow admins to download drafts)
    const authHeader = req.headers.get("Authorization");
    let isRequestingAdmin = false;
    if (authHeader) {
      const token = authHeader.replace("Bearer ", "");
      const { data: { user } } = await supabaseAdmin.auth.getUser(token);
      if (user?.app_metadata?.role === "admin" || user?.app_metadata?.is_admin === true) {
        isRequestingAdmin = true;
      }
    }

    // Query database to ensure item is published or requester is admin
    let query = supabaseAdmin
      .from("content_items")
      .select("id, title, storage_path, sha256, target_filename, size_bytes, is_published, is_draft");

    if (content_id) {
      query = query.eq("id", content_id);
    } else {
      query = query.eq("storage_path", storage_path);
    }

    const { data: items, error: dbError } = await query;
    if (dbError || !items || items.length === 0) {
      return new Response(
        JSON.stringify({ error: "Content item not found" }),
        { status: 404, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const item = items[0];

    // Access control: Non-admins cannot download unpublished or draft packages
    if (!isRequestingAdmin && (!item.is_published || item.is_draft)) {
      return new Response(
        JSON.stringify({ error: "Access denied: Content package is not published" }),
        { status: 403, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // Generate temporary Signed URL from private 'worm-content-files' bucket
    // Expiration: 3600 seconds (1 hour)
    const expiresInSeconds = 3600;
    const { data: signedData, error: storageError } = await supabaseAdmin.storage
      .from("worm-content-files")
      .createSignedUrl(item.storage_path, expiresInSeconds);

    if (storageError || !signedData?.signedUrl) {
      return new Response(
        JSON.stringify({ error: `Storage signature failed: ${storageError?.message || "Unknown error"}` }),
        { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // Return signed URL with integrity verification metadata
    return new Response(
      JSON.stringify({
        success: true,
        content_id: item.id,
        title: item.title,
        target_filename: item.target_filename,
        sha256: item.sha256,
        size_bytes: item.size_bytes,
        signed_url: signedData.signedUrl,
        expires_in_seconds: expiresInSeconds,
        generated_at: new Date().toISOString(),
      }),
      { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : "Internal Server Error";
    return new Response(
      JSON.stringify({ error: message }),
      { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }
});
