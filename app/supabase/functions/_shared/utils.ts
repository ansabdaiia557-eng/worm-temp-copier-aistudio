import { createClient, SupabaseClient, User } from "https://esm.sh/@supabase/supabase-js@2.39.0";

export const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-app-version",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
};

export function jsonResponse(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

export function getAdminClient(): SupabaseClient {
  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!supabaseUrl || !serviceRoleKey) {
    throw new Error("Server configuration missing: SUPABASE_URL or SUPABASE_SERVICE_ROLE_KEY");
  }
  return createClient(supabaseUrl, serviceRoleKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
}

export function getClientIp(req: Request): string {
  const fwd = req.headers.get("x-forwarded-for");
  if (fwd) return fwd.split(",")[0].trim();
  return req.headers.get("cf-connecting-ip") || "unknown";
}

export interface AuthContext {
  user: User | null;
  profile: Record<string, unknown> | null;
  isAdmin: boolean;
  isSuperAdmin: boolean;
}

export async function authenticateRequest(
  req: Request,
  supabaseAdmin: SupabaseClient
): Promise<AuthContext> {
  const authHeader = req.headers.get("authorization") || req.headers.get("Authorization");
  if (!authHeader || !authHeader.startsWith("Bearer ")) {
    return { user: null, profile: null, isAdmin: false, isSuperAdmin: false };
  }
  const token = authHeader.slice(7).trim();
  if (!token) {
    return { user: null, profile: null, isAdmin: false, isSuperAdmin: false };
  }

  const { data: { user }, error } = await supabaseAdmin.auth.getUser(token);
  if (error || !user) {
    return { user: null, profile: null, isAdmin: false, isSuperAdmin: false };
  }

  const role = user.app_metadata?.role;
  const isAdmin = role === "admin" || role === "super_admin" || user.app_metadata?.is_admin === true;
  const isSuperAdmin = role === "super_admin";

  const { data: profile } = await supabaseAdmin
    .from("user_profiles")
    .select("*")
    .eq("id", user.id)
    .maybeSingle();

  return { user, profile: profile || null, isAdmin, isSuperAdmin };
}

export async function enforceRateLimit(
  supabaseAdmin: SupabaseClient,
  bucketKey: string,
  maxRequests: number,
  windowSeconds: number
): Promise<boolean> {
  try {
    const now = new Date();
    const { data: existing } = await supabaseAdmin
      .from("rate_limits")
      .select("*")
      .eq("bucket_key", bucketKey)
      .maybeSingle();

    if (!existing) {
      await supabaseAdmin.from("rate_limits").insert({
        bucket_key: bucketKey,
        window_start: now.toISOString(),
        request_count: 1,
      });
      return true;
    }

    const windowStart = new Date(existing.window_start);
    const elapsedSeconds = (now.getTime() - windowStart.getTime()) / 1000;

    if (elapsedSeconds > windowSeconds) {
      await supabaseAdmin
        .from("rate_limits")
        .update({ window_start: now.toISOString(), request_count: 1 })
        .eq("bucket_key", bucketKey);
      return true;
    }

    if (existing.request_count >= maxRequests) {
      return false;
    }

    await supabaseAdmin
      .from("rate_limits")
      .update({ request_count: existing.request_count + 1 })
      .eq("bucket_key", bucketKey);
    return true;
  } catch {
    return true;
  }
}

export async function logSecurityEvent(
  supabaseAdmin: SupabaseClient,
  eventType: string,
  severity: "low" | "medium" | "high" | "critical",
  userId: string | null,
  ip: string,
  endpoint: string,
  description: string,
  metadata: Record<string, unknown> = {}
) {
  try {
    await supabaseAdmin.from("security_events").insert({
      event_type: eventType,
      severity,
      user_id: userId,
      ip_address: ip,
      endpoint,
      description,
      metadata,
    });
  } catch {
    // Non-fatal logging failure
  }
}

export interface ClientReportedFile {
  id: string;
  version_code?: number;
  sha256?: string;
}

export async function evaluateServerGate(
  supabaseAdmin: SupabaseClient,
  user: User | null,
  profile: Record<string, unknown> | null,
  clientFiles: ClientReportedFile[] = [],
  clientVersionCode = 0
) {
  const nowIso = new Date().toISOString();
  const nowMs = Date.now();

  // 1. Load singleton system_status
  const { data: sysRow } = await supabaseAdmin
    .from("system_status")
    .select("*")
    .eq("id", "00000000-0000-0000-0000-000000000001")
    .maybeSingle();

  let maintenanceActive = Boolean(sysRow?.maintenance_mode);
  let maintenanceReasonAr = sysRow?.maintenance_reason_ar || sysRow?.announcement_body_ar || null;
  let maintenanceReasonEn = sysRow?.maintenance_reason_en || sysRow?.announcement_body_en || null;
  let maintenanceStartedAt = sysRow?.maintenance_started_at || null;
  let maintenanceEndsAt = sysRow?.maintenance_ends_at || null;
  let isScheduledMaintenance = false;

  // Auto-expire manual maintenance if ends_at has passed
  if (maintenanceActive && maintenanceEndsAt) {
    const endsMs = new Date(maintenanceEndsAt).getTime();
    if (!Number.isNaN(endsMs) && endsMs <= nowMs) {
      maintenanceActive = false;
      await supabaseAdmin
        .from("system_status")
        .update({ maintenance_mode: false, updated_at: nowIso })
        .eq("id", "00000000-0000-0000-0000-000000000001");
    }
  }

  // Evaluate scheduled maintenance windows
  const { data: schedules } = await supabaseAdmin
    .from("maintenance_schedules")
    .select("*")
    .in("status", ["scheduled", "active"])
    .order("starts_at", { ascending: true });

  if (schedules && schedules.length > 0) {
    for (const win of schedules) {
      const sMs = new Date(win.starts_at).getTime();
      const eMs = new Date(win.ends_at).getTime();
      if (eMs <= nowMs) {
        await supabaseAdmin
          .from("maintenance_schedules")
          .update({ status: "completed" })
          .eq("id", win.id);
      } else if (sMs <= nowMs && eMs > nowMs) {
        if (win.status === "scheduled") {
          await supabaseAdmin
            .from("maintenance_schedules")
            .update({ status: "active" })
            .eq("id", win.id);
        }
        maintenanceActive = true;
        isScheduledMaintenance = true;
        maintenanceReasonAr = win.reason_ar || win.title || maintenanceReasonAr;
        maintenanceReasonEn = win.reason_en || win.title || maintenanceReasonEn;
        maintenanceStartedAt = win.starts_at;
        maintenanceEndsAt = win.ends_at;
        break;
      }
    }
  }

  // 2. Check User Ban & Account Status
  const isAuthenticated = Boolean(user);
  const isBanned = Boolean(profile?.is_banned) || (profile?.account_status && profile.account_status !== "active");

  // 3. Check Subscription (server clock authoritative)
  let subscriptionActive = false;
  let subscriptionRecord: Record<string, unknown> | null = null;
  if (user) {
    const { data: subs } = await supabaseAdmin
      .from("subscriptions")
      .select("*")
      .eq("user_id", user.id)
      .order("ends_at", { ascending: false })
      .limit(5);

    if (subs && subs.length > 0) {
      // Auto-mark expired active subscriptions cleanly
      for (const s of subs) {
        const endMs = new Date(s.ends_at).getTime();
        if (s.status === "active" && endMs <= nowMs) {
          await supabaseAdmin.from("subscriptions").update({ status: "expired" }).eq("id", s.id);
          s.status = "expired";
        }
      }
      const valid = subs.find(
        (s) =>
          s.status === "active" &&
          new Date(s.starts_at).getTime() <= nowMs &&
          new Date(s.ends_at).getTime() > nowMs
      );
      if (valid) {
        subscriptionActive = true;
        subscriptionRecord = valid;
      } else {
        subscriptionRecord = subs[0];
      }
    }
  }

  // 4. Check Telegram Membership
  const telegramRequired = sysRow?.require_telegram_membership ?? true;
  const subscriptionRequired = sysRow?.require_active_subscription ?? true;
  const telegramVerified = Boolean(profile?.telegram_verified);
  const telegramChannelUrl = sysRow?.telegram_channel_url || "https://t.me/Kharbash_anas";

  // 5. Required & Published Server Files (Global File Update check)
  const { data: publishedItems } = await supabaseAdmin
    .from("content_items")
    .select("*")
    .eq("is_published", true)
    .eq("is_draft", false)
    .order("updated_at", { ascending: false });

  const eligibleItems = (publishedItems || []).filter((item) => {
    if (item.target_group === "all") return true;
    if (!user) return false;
    if (item.target_group === "specific_users") {
      return Array.isArray(item.assigned_user_ids) && item.assigned_user_ids.includes(user.id);
    }
    if (item.target_group === "vip") {
      return subscriptionActive;
    }
    return true;
  });

  const clientMap = new Map<string, ClientReportedFile>();
  for (const cf of clientFiles) {
    if (cf && cf.id) clientMap.set(cf.id, cf);
  }

  const outdatedFileIds: string[] = [];
  for (const item of eligibleItems) {
    if (!item.is_required) continue;
    const local = clientMap.get(item.id);
    const localVer = local?.version_code ?? 0;
    const localSha = (local?.sha256 || "").toLowerCase();
    const serverVer = item.version_code ?? 1;
    const serverSha = (item.sha256 || "").toLowerCase();
    if (!local || localVer < serverVer || (serverSha && localSha !== serverSha)) {
      outdatedFileIds.push(item.id);
    }
  }

  // 6. Latest Active App Update (APK)
  const { data: appUpdates } = await supabaseAdmin
    .from("app_updates")
    .select("*")
    .eq("is_active", true)
    .order("version_code", { ascending: false })
    .limit(1);

  const rawLatestAppUpdate = appUpdates && appUpdates.length > 0 ? appUpdates[0] : null;
  let latestAppUpdate = rawLatestAppUpdate;
  if (rawLatestAppUpdate?.apk_storage_path) {
    const { data: signedApk } = await supabaseAdmin.storage
      .from("worm-app-releases")
      .createSignedUrl(rawLatestAppUpdate.apk_storage_path, 3600);
    if (signedApk?.signedUrl) {
      latestAppUpdate = { ...rawLatestAppUpdate, download_url: signedApk.signedUrl };
    }
  }
  const mandatoryAppUpdateRequired =
    Boolean(latestAppUpdate?.is_mandatory) &&
    clientVersionCode > 0 &&
    latestAppUpdate.version_code > clientVersionCode;

  // 7. Authoritative Server Gate Decision (priority: Ban -> Maintenance -> Auth -> Sub -> Telegram -> Mandatory APK -> Outdated Files)
  let canExecuteCopy = true;
  let blockReasonCode: string | null = null;
  let blockReasonAr: string | null = null;
  let blockReasonEn: string | null = null;

  if (maintenanceActive) {
    canExecuteCopy = false;
    blockReasonCode = "MAINTENANCE_ACTIVE";
    blockReasonAr = maintenanceReasonAr || "الخادم في وضع الصيانة المؤقتة حالياً. جميع عمليات النسخ متوقفة لحين الانتهاء.";
    blockReasonEn = maintenanceReasonEn || "Server is currently under maintenance. Copy operations are temporarily paused.";
  } else if (!isAuthenticated) {
    canExecuteCopy = false;
    blockReasonCode = "AUTH_REQUIRED";
    blockReasonAr = "يجب تسجيل الدخول بحسابك أولاً للتحقق من الصلاحية والاشتراك.";
    blockReasonEn = "Sign in to your account to verify access and subscription.";
  } else if (isBanned) {
    canExecuteCopy = false;
    blockReasonCode = "ACCOUNT_BANNED";
    blockReasonAr = `تم حظر حسابك: ${profile?.ban_reason || "مخالفة شروط الاستخدام"}`;
    blockReasonEn = `Your account is banned: ${profile?.ban_reason || "Policy violation"}`;
  } else if (subscriptionRequired && !subscriptionActive) {
    canExecuteCopy = false;
    blockReasonCode = "SUBSCRIPTION_REQUIRED";
    blockReasonAr = "لا يوجد اشتراك نشط حالياً. يرجى تفعيل كود اشتراك صالح للمتابعة.";
    blockReasonEn = "Active subscription required. Redeem a valid activation code to continue.";
  } else if (telegramRequired && !telegramVerified) {
    canExecuteCopy = false;
    blockReasonCode = "TELEGRAM_REQUIRED";
    blockReasonAr = "يجب الانضمام لقناة تيليجرام الرسمية وتأكيد العضوية قبل المتابعة.";
    blockReasonEn = "Telegram channel membership verification is required before copying.";
  } else if (mandatoryAppUpdateRequired) {
    canExecuteCopy = false;
    blockReasonCode = "APP_UPDATE_REQUIRED";
    blockReasonAr = `يتوفر تحديث إجباري للتطبيق (${latestAppUpdate.version_name}). يرجى التحديث للمتابعة.`;
    blockReasonEn = `Mandatory app update (${latestAppUpdate.version_name}) is required to continue.`;
  } else if (outdatedFileIds.length > 0) {
    canExecuteCopy = false;
    blockReasonCode = "FILES_UPDATE_REQUIRED";
    blockReasonAr = `يوجد ${outdatedFileIds.length} ملفات مطلوبة غير محدثة. يجب تنزيل التحديثات من السيرفر أولاً.`;
    blockReasonEn = `${outdatedFileIds.length} required server file(s) need updating before installation.`;
  }

  return {
    server_time: nowIso,
    server_name: sysRow?.server_name || "ANS SERVER CONTROL",
    global_file_revision: sysRow?.global_file_revision || 1,
    can_execute_copy: canExecuteCopy,
    block_reason_code: blockReasonCode,
    block_reason_ar: blockReasonAr,
    block_reason_en: blockReasonEn,
    maintenance: {
      is_active: maintenanceActive,
      is_scheduled: isScheduledMaintenance,
      reason_ar: maintenanceReasonAr,
      reason_en: maintenanceReasonEn,
      started_at: maintenanceStartedAt,
      ends_at: maintenanceEndsAt,
    },
    user: user
      ? {
          id: user.id,
          email: user.email,
          display_name: profile?.display_name || user.email,
          role: profile?.role || "user",
          account_status: profile?.account_status || "active",
          is_banned: Boolean(isBanned),
          ban_reason: profile?.ban_reason || null,
          telegram_verified: telegramVerified,
          telegram_user_id: profile?.telegram_user_id || null,
          last_login_at: profile?.last_login_at || null,
          created_at: profile?.created_at || user.created_at,
        }
      : null,
    subscription: {
      required: subscriptionRequired,
      is_active: subscriptionActive,
      plan_name: subscriptionRecord?.plan_name || null,
      status: subscriptionRecord?.status || "none",
      starts_at: subscriptionRecord?.starts_at || null,
      ends_at: subscriptionRecord?.ends_at || null,
    },
    telegram: {
      required: telegramRequired,
      verified: telegramVerified,
      channel_url: telegramChannelUrl,
      channel_username: sysRow?.telegram_channel_username || "@Kharbash_anas",
    },
    files: eligibleItems,
    outdated_file_ids: outdatedFileIds,
    app_update: latestAppUpdate,
  };
}


// ==============================================================================
// CLIENT INTEGRITY: signed-app check + one device per account
// ==============================================================================
export const ALLOWED_APP_SIGS = [
  "631e2c62058b87f8f63c24d93ab217a051500c9dd69d919468b37484195ed9e4",
];

export interface IntegrityResult {
  ok: boolean;
  code?: "TAMPERED_APP" | "DEVICE_MISMATCH" | "DEVICE_REQUIRED";
  reason_ar?: string;
  reason_en?: string;
}

export async function checkClientIntegrity(
  supabaseAdmin: SupabaseClient,
  user: User | null,
  profile: Record<string, unknown> | null,
  isAdmin: boolean,
  deviceId: string,
  deviceModel: string,
  appSig: string,
  ip: string,
  endpoint: string
): Promise<IntegrityResult> {
  if (!user || isAdmin) return { ok: true };

  let legacyAllowed = true;
  try {
    const { data } = await supabaseAdmin.from("server_settings").select("value").eq("key", "allow_legacy_clients").maybeSingle();
    if (data && data.value === false) legacyAllowed = false;
  } catch { /* default true */ }

  const sig = (appSig || "").trim().toLowerCase();
  if (!sig) {
    if (legacyAllowed) return { ok: true };
    await logSecurityEvent(supabaseAdmin, "TAMPERED_APP", "high", user.id, ip, endpoint, "Client did not send app signature", { reason: "missing_signature" });
    return {
      ok: false, code: "TAMPERED_APP",
      reason_ar: "نسخة التطبيق غير معتمدة. نزّل النسخة الرسمية من القناة.",
      reason_en: "This app build is not official. Download the official version.",
    };
  }
  if (!ALLOWED_APP_SIGS.includes(sig)) {
    await logSecurityEvent(supabaseAdmin, "TAMPERED_APP", "high", user.id, ip, endpoint, "Unofficial app signature", { sig_prefix: sig.slice(0, 12), device_id: deviceId.slice(0, 64) });
    return {
      ok: false, code: "TAMPERED_APP",
      reason_ar: "تم اكتشاف نسخة معدلة من التطبيق. نزّل النسخة الرسمية من القناة.",
      reason_en: "A modified copy of the app was detected. Download the official version.",
    };
  }

  const dev = (deviceId || "").trim().slice(0, 128);
  if (!dev) {
    return {
      ok: false, code: "DEVICE_REQUIRED",
      reason_ar: "تعذر التعرف على الجهاز.", reason_en: "Could not identify the device.",
    };
  }
  const locked = (profile?.device_id as string | null) || null;
  if (!locked) {
    await supabaseAdmin.from("user_profiles").update({
      device_id: dev, device_model: deviceModel.slice(0, 128) || null, device_locked_at: new Date().toISOString(),
    }).eq("id", user.id);
    return { ok: true };
  }
  if (locked !== dev) {
    await logSecurityEvent(supabaseAdmin, "DEVICE_MISMATCH", "high", user.id, ip, endpoint, "Account used on a different device", {
      locked_device: locked.slice(0, 64), new_device: dev.slice(0, 64), new_model: deviceModel.slice(0, 64),
    });
    return {
      ok: false, code: "DEVICE_MISMATCH",
      reason_ar: "هذا الحساب مرتبط بجهاز آخر. تواصل مع المطور لإعادة ضبط الجهاز.",
      reason_en: "This account is linked to another device. Contact the developer to reset the device.",
    };
  }
  return { ok: true };
}
