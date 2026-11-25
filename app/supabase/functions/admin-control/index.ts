// ==============================================================================
// SUPABASE EDGE FUNCTION: admin-control
// PURPOSE: Authoritative Admin Operations Endpoint for ANS SERVER CONTROL
// SECURITY: Strictly enforces verified app_metadata admin claims, logs all
//           mutations to audit_logs/file_change_logs, rejects non-admins (403).
// ==============================================================================

import { serve } from "https://deno.land/std@0.177.0/http/server.ts";
import {
  corsHeaders,
  jsonResponse,
  getAdminClient,
  authenticateRequest,
  logSecurityEvent,
  getClientIp,
} from "../_shared/utils.ts";

const SYSTEM_STATUS_ID = "00000000-0000-0000-0000-000000000001";

serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const supabaseAdmin = getAdminClient();
    const clientIp = getClientIp(req);
    const { user, isAdmin, isSuperAdmin } = await authenticateRequest(req, supabaseAdmin);

    if (!user) {
      return jsonResponse({ success: false, error: "لازم تسجل دخول" }, 401);
    }

    if (!isAdmin) {
      await logSecurityEvent(
        supabaseAdmin,
        "UNAUTHORIZED_ADMIN_ATTEMPT",
        "critical",
        user.id,
        clientIp,
        "admin-control",
        "Non-admin user attempted to invoke admin-control endpoint"
      );
      return jsonResponse({ success: false, error: "ممنوع: تحتاج صلاحيات الأدمن" }, 403);
    }

    const body = await req.json().catch(() => ({}));
    const action = String(body.action || "").trim();
    const nowIso = new Date().toISOString();

    const writeAudit = async (
      opAction: string,
      targetType: string,
      targetId: string | null,
      details: Record<string, unknown>
    ) => {
      await supabaseAdmin.from("audit_logs").insert({
        user_id: user.id,
        user_email: user.email || null,
        action: opAction,
        target_type: targetType,
        target_id: targetId,
        details,
        ip_address: clientIp,
        timestamp: nowIso,
      });
    };

    switch (action) {
      // ----------------------------------------------------------------------
      // 1. OVERVIEW / DASHBOARD STATS
      // ----------------------------------------------------------------------
      case "get_dashboard_stats": {
        const todayStart = new Date();
        todayStart.setUTCHours(0, 0, 0, 0);

        const [
          usersRes,
          bannedRes,
          subsRes,
          codesRes,
          filesRes,
          todayOpsRes,
          secRes,
          sysRes,
          maintSchedulesRes,
          appUpdatesRes,
        ] = await Promise.all([
          supabaseAdmin.from("user_profiles").select("id", { count: "exact", head: true }),
          supabaseAdmin.from("user_profiles").select("id", { count: "exact", head: true }).eq("is_banned", true),
          supabaseAdmin.from("subscriptions").select("id", { count: "exact", head: true }).eq("status", "active").gt("ends_at", nowIso),
          supabaseAdmin.from("activation_codes").select("id", { count: "exact", head: true }).eq("is_active", true),
          supabaseAdmin.from("content_items").select("id", { count: "exact", head: true }).eq("is_published", true),
          supabaseAdmin.from("audit_logs").select("id", { count: "exact", head: true }).gte("timestamp", todayStart.toISOString()),
          supabaseAdmin.from("security_events").select("id", { count: "exact", head: true }),
          supabaseAdmin.from("system_status").select("*").eq("id", SYSTEM_STATUS_ID).maybeSingle(),
          supabaseAdmin.from("maintenance_schedules").select("*").in("status", ["scheduled", "active"]).order("starts_at", { ascending: true }),
          supabaseAdmin.from("app_updates").select("*").eq("is_active", true).order("version_code", { ascending: false }).limit(1),
        ]);

        const { data: maintActiveRpc } = await supabaseAdmin.rpc("is_maintenance_active");

        return jsonResponse({
          success: true,
          stats: {
            total_users: usersRes.count || 0,
            banned_users: bannedRes.count || 0,
            active_subscriptions: subsRes.count || 0,
            active_codes: codesRes.count || 0,
            published_files: filesRes.count || 0,
            operations_today: todayOpsRes.count || 0,
            security_events: secRes.count || 0,
            maintenance_active: Boolean(maintActiveRpc),
            system_status: sysRes.data || null,
            active_schedules: maintSchedulesRes.data || [],
            latest_app_update: appUpdatesRes.data?.[0] || null,
          },
        });
      }

      // ----------------------------------------------------------------------
      // 2. USERS MANAGEMENT
      // ----------------------------------------------------------------------
      case "list_users": {
        const search = String(body.search || "").trim().toLowerCase();
        const { data: profiles, error } = await supabaseAdmin
          .from("user_profiles")
          .select("*")
          .order("created_at", { ascending: false })
          .limit(200);
        if (error) throw error;

        const { data: allSubs } = await supabaseAdmin
          .from("subscriptions")
          .select("*")
          .order("ends_at", { ascending: false });

        const filtered = (profiles || []).filter((p) => {
          if (!search) return true;
          return (
            String(p.email || "").toLowerCase().includes(search) ||
            String(p.display_name || "").toLowerCase().includes(search) ||
            String(p.telegram_user_id || "").includes(search) ||
            String(p.telegram_username || "").toLowerCase().includes(search) ||
            String(p.id || "").toLowerCase().includes(search)
          );
        });

        const enriched = filtered.map((p) => {
          const userSubs = (allSubs || []).filter((s) => s.user_id === p.id);
          return { ...p, latest_subscription: userSubs[0] || null };
        });

        return jsonResponse({ success: true, users: enriched });
      }

      case "ban_user": {
        const targetId = String(body.user_id || "");
        const reason = String(body.reason || "Banned by administrator").trim();
        if (!targetId) return jsonResponse({ success: false, error: "معرّف المستخدم ناقص" }, 400);
        if (targetId === user.id) return jsonResponse({ success: false, error: "لا يمكنك حظر حساب الأدمن الخاص بك" }, 400);

        const { error } = await supabaseAdmin
          .from("user_profiles")
          .update({
            is_banned: true,
            account_status: "banned",
            ban_reason: reason,
            banned_at: nowIso,
            banned_by: user.id,
          })
          .eq("id", targetId);
        if (error) throw error;

        await supabaseAdmin
          .from("user_sessions")
          .update({ is_active: false })
          .eq("user_id", targetId);

        await logSecurityEvent(supabaseAdmin, "USER_BANNED", "high", targetId, clientIp, "admin-control", reason, { banned_by: user.id });
        await writeAudit("BAN_USER", "user", targetId, { reason });
        return jsonResponse({ success: true });
      }

      case "unban_user": {
        const targetId = String(body.user_id || "");
        if (!targetId) return jsonResponse({ success: false, error: "معرّف المستخدم ناقص" }, 400);

        const { error } = await supabaseAdmin
          .from("user_profiles")
          .update({
            is_banned: false,
            account_status: "active",
            ban_reason: null,
            banned_at: null,
            banned_by: null,
          })
          .eq("id", targetId);
        if (error) throw error;

        await writeAudit("UNBAN_USER", "user", targetId, {});
        return jsonResponse({ success: true });
      }

      case "set_user_role": {
        const targetId = String(body.user_id || "");
        const role = String(body.role || "user");
        if (!targetId || !["user", "moderator", "admin", "super_admin"].includes(role)) {
          return jsonResponse({ success: false, error: "Invalid user_id or role" }, 400);
        }
        if (role === "super_admin" && !isSuperAdmin) {
          return jsonResponse({ success: false, error: "Only super_admin can assign super_admin" }, 403);
        }

        const isAdminRole = role === "admin" || role === "super_admin";
        const { error: authErr } = await supabaseAdmin.auth.admin.updateUserById(targetId, {
          app_metadata: { role, is_admin: isAdminRole },
        });
        if (authErr) throw authErr;

        await supabaseAdmin.from("user_profiles").update({ role }).eq("id", targetId);
        await writeAudit("SET_USER_ROLE", "user", targetId, { role });
        return jsonResponse({ success: true });
      }

      case "get_user_activity": {
        const targetId = String(body.user_id || "");
        if (!targetId) return jsonResponse({ success: false, error: "معرّف المستخدم ناقص" }, 400);

        const [logsRes, sessionsRes, subsRes] = await Promise.all([
          supabaseAdmin.from("user_activity_logs").select("*").eq("user_id", targetId).order("created_at", { ascending: false }).limit(50),
          supabaseAdmin.from("user_sessions").select("*").eq("user_id", targetId).order("last_seen_at", { ascending: false }).limit(20),
          supabaseAdmin.from("subscriptions").select("*").eq("user_id", targetId).order("created_at", { ascending: false }).limit(10),
        ]);

        return jsonResponse({
          success: true,
          activity: logsRes.data || [],
          sessions: sessionsRes.data || [],
          subscriptions: subsRes.data || [],
        });
      }

      // ----------------------------------------------------------------------
      // 3. ACTIVATION CODES
      // ----------------------------------------------------------------------
      case "list_activation_codes": {
        const { data, error } = await supabaseAdmin
          .from("activation_codes")
          .select("*")
          .order("created_at", { ascending: false })
          .limit(200);
        if (error) throw error;
        return jsonResponse({ success: true, codes: data || [] });
      }

      case "create_activation_code": {
        const code = String(body.code || "").trim().toUpperCase();
        const description = String(body.description || "").trim();
        const codeType = body.code_type === "multi_use" ? "multi_use" : "single_use";
        const maxUses = codeType === "single_use" ? 1 : Math.max(1, Number(body.max_uses || 1000000));
        const durationDays = Math.max(1, Number(body.duration_days || 30));
        const expiresAt = body.expires_at ? String(body.expires_at) : null;

        if (!code || code.length < 4) {
          return jsonResponse({ success: false, error: "كود التفعيل لازم يكون 4 أحرف على الأقل" }, 400);
        }

        const { data, error } = await supabaseAdmin
          .from("activation_codes")
          .insert({
            code,
            description,
            code_type: codeType,
            max_uses: maxUses,
            duration_days: durationDays,
            expires_at: expiresAt,
            is_active: true,
            created_by: user.id,
          })
          .select()
          .single();
        if (error) throw error;

        await writeAudit("CREATE_ACTIVATION_CODE", "activation_code", data?.id || null, { code, codeType, maxUses, durationDays });
        return jsonResponse({ success: true, code: data });
      }

      case "toggle_activation_code": {
        const codeId = String(body.code_id || "");
        const isActive = Boolean(body.is_active);
        const { error } = await supabaseAdmin
          .from("activation_codes")
          .update({ is_active: isActive })
          .eq("id", codeId);
        if (error) throw error;
        await writeAudit("TOGGLE_ACTIVATION_CODE", "activation_code", codeId, { is_active: isActive });
        return jsonResponse({ success: true });
      }

      case "delete_activation_code": {
        const codeId = String(body.code_id || "");
        const { error } = await supabaseAdmin.from("activation_codes").delete().eq("id", codeId);
        if (error) throw error;
        await writeAudit("DELETE_ACTIVATION_CODE", "activation_code", codeId, {});
        return jsonResponse({ success: true });
      }

      case "list_activation_logs": {
        const { data, error } = await supabaseAdmin
          .from("activation_logs")
          .select("*")
          .order("activated_at", { ascending: false })
          .limit(200);
        if (error) throw error;
        return jsonResponse({ success: true, logs: data || [] });
      }

      // ----------------------------------------------------------------------
      // 4. SUBSCRIPTIONS
      // ----------------------------------------------------------------------
      case "list_subscriptions": {
        const { data: subs, error } = await supabaseAdmin
          .from("subscriptions")
          .select("*")
          .order("created_at", { ascending: false })
          .limit(200);
        if (error) throw error;

        const { data: profiles } = await supabaseAdmin.from("user_profiles").select("id, email, display_name");
        const profileMap = new Map((profiles || []).map((p) => [p.id, p]));
        const enriched = (subs || []).map((s) => ({
          ...s,
          user_email: profileMap.get(s.user_id)?.email || s.user_id,
          user_name: profileMap.get(s.user_id)?.display_name || "",
        }));
        return jsonResponse({ success: true, subscriptions: enriched });
      }

      case "grant_subscription": {
        const targetId = String(body.user_id || "").trim();
        const planName = String(body.plan_name || "PRO").trim();
        const durationDays = Math.max(1, Number(body.duration_days || 30));
        const notes = String(body.notes || "Granted by admin").trim();
        if (!targetId) return jsonResponse({ success: false, error: "معرّف المستخدم ناقص" }, 400);

        // Resolve user by UUID or email
        let resolvedUserId = targetId;
        if (targetId.includes("@")) {
          const { data: prof } = await supabaseAdmin
            .from("user_profiles")
            .select("id")
            .eq("email", targetId)
            .maybeSingle();
          if (!prof) return jsonResponse({ success: false, error: "User email not found" }, 404);
          resolvedUserId = prof.id;
        }

        const startsAt = new Date();
        const endsAt = new Date(startsAt.getTime() + durationDays * 86400_000);
        const { data, error } = await supabaseAdmin
          .from("subscriptions")
          .insert({
            user_id: resolvedUserId,
            plan_name: planName,
            status: "active",
            starts_at: startsAt.toISOString(),
            ends_at: endsAt.toISOString(),
            notes,
            created_by: user.id,
          })
          .select()
          .single();
        if (error) throw error;

        await writeAudit("GRANT_SUBSCRIPTION", "subscription", data?.id || null, { user_id: resolvedUserId, planName, durationDays });
        return jsonResponse({ success: true, subscription: data });
      }

      case "update_subscription_status": {
        const subId = String(body.subscription_id || "");
        const status = String(body.status || "");
        if (!subId || !["active", "paused", "revoked", "expired"].includes(status)) {
          return jsonResponse({ success: false, error: "Invalid subscription_id or status" }, 400);
        }

        const updates: Record<string, unknown> = { status };
        if (status === "paused") updates.paused_at = nowIso;
        if (status === "active") updates.paused_at = null;
        if (body.extend_days) {
          const { data: curr } = await supabaseAdmin.from("subscriptions").select("ends_at").eq("id", subId).single();
          const base = curr ? Math.max(Date.now(), new Date(curr.ends_at).getTime()) : Date.now();
          updates.ends_at = new Date(base + Number(body.extend_days) * 86400_000).toISOString();
        }

        const { error } = await supabaseAdmin.from("subscriptions").update(updates).eq("id", subId);
        if (error) throw error;
        await writeAudit("UPDATE_SUBSCRIPTION_STATUS", "subscription", subId, { status, extend_days: body.extend_days || 0 });
        return jsonResponse({ success: true });
      }

      // ----------------------------------------------------------------------
      // 5. FILE MANAGER, VERSIONING & GLOBAL FILE UPDATE
      // ----------------------------------------------------------------------
      case "register_file_upload": {
        const {
          title,
          description,
          category,
          version,
          version_code,
          size_bytes,
          target_filename,
          target_relative_path,
          sha256,
          storage_path,
          compatible_editions,
          is_published,
          is_required,
          target_group,
          assigned_user_ids,
          release_notes,
        } = body;

        if (!title || !target_filename || !sha256 || !storage_path) {
          return jsonResponse({ success: false, error: "Missing required file metadata" }, 400);
        }

        const parsedVerCode = Math.max(1, Number(version_code || 1));
        const { data: inserted, error: insErr } = await supabaseAdmin
          .from("content_items")
          .insert({
            slug: `item_${Date.now()}`,
            title,
            description: description || "",
            category: category || "ALL_FEATURES",
            version: version || "v1.0.0",
            version_code: parsedVerCode,
            size_bytes: Number(size_bytes || 1),
            target_filename,
            target_relative_path: target_relative_path || "",
            sha256,
            storage_path,
            compatible_editions: Array.isArray(compatible_editions) ? compatible_editions : ["PUBG_GLOBAL"],
            is_published: Boolean(is_published),
            is_draft: !is_published,
            is_required: is_required !== false,
            target_group: target_group || "all",
            assigned_user_ids: Array.isArray(assigned_user_ids) ? assigned_user_ids : [],
            is_permanent: true,
          })
          .select()
          .single();
        if (insErr) throw insErr;

        await supabaseAdmin.from("file_versions").insert({
          content_item_id: inserted.id,
          version: inserted.version,
          version_code: parsedVerCode,
          storage_path,
          file_name: target_filename,
          size_bytes: inserted.size_bytes,
          sha256,
          release_notes: release_notes || "Initial server release",
          is_current: true,
          uploaded_by: user.id,
        });

        await supabaseAdmin.from("file_change_logs").insert({
          content_item_id: inserted.id,
          action: "upload",
          new_value: { title, version: inserted.version, version_code: parsedVerCode, sha256, target_group },
          performed_by: user.id,
        });

        await writeAudit("UPLOAD_SERVER_FILE", "content_item", inserted.id, { title, version: inserted.version });
        return jsonResponse({ success: true, item: inserted });
      }

      case "replace_file_version": {
        const itemId = String(body.content_item_id || "");
        if (!itemId || !body.sha256 || !body.storage_path || !body.target_filename) {
          return jsonResponse({ success: false, error: "Missing file replacement parameters" }, 400);
        }

        const { data: oldItem, error: fetchErr } = await supabaseAdmin
          .from("content_items")
          .select("*")
          .eq("id", itemId)
          .single();
        if (fetchErr || !oldItem) return jsonResponse({ success: false, error: "Content item not found" }, 404);

        const nextVerCode = Math.max((oldItem.version_code || 1) + 1, Number(body.version_code || 0));
        const nextVersion = String(body.version || `v${nextVerCode}.0`);

        await supabaseAdmin
          .from("file_versions")
          .update({ is_current: false })
          .eq("content_item_id", itemId);

        const { data: updated, error: updErr } = await supabaseAdmin
          .from("content_items")
          .update({
            version: nextVersion,
            version_code: nextVerCode,
            storage_path: body.storage_path,
            target_filename: body.target_filename,
            size_bytes: Number(body.size_bytes || oldItem.size_bytes),
            sha256: body.sha256,
            updated_at: nowIso,
          })
          .eq("id", itemId)
          .select()
          .single();
        if (updErr) throw updErr;

        await supabaseAdmin.from("file_versions").insert({
          content_item_id: itemId,
          version: nextVersion,
          version_code: nextVerCode,
          storage_path: body.storage_path,
          file_name: body.target_filename,
          size_bytes: Number(body.size_bytes || oldItem.size_bytes),
          sha256: body.sha256,
          release_notes: body.release_notes || "Replaced via ANS SERVER CONTROL",
          is_current: true,
          uploaded_by: user.id,
        });

        await supabaseAdmin.from("file_change_logs").insert({
          content_item_id: itemId,
          action: "replace",
          old_value: { version: oldItem.version, version_code: oldItem.version_code, sha256: oldItem.sha256 },
          new_value: { version: nextVersion, version_code: nextVerCode, sha256: body.sha256 },
          performed_by: user.id,
        });

        await writeAudit("REPLACE_FILE_VERSION", "content_item", itemId, { nextVersion, nextVerCode });
        return jsonResponse({ success: true, item: updated });
      }

      case "rename_file_item": {
        const itemId = String(body.content_item_id || "");
        const newTitle = String(body.title || "").trim();
        const newTargetFilename = String(body.target_filename || "").trim();
        const newRelPath = body.target_relative_path !== undefined ? String(body.target_relative_path).trim() : undefined;
        if (!itemId || (!newTitle && !newTargetFilename)) {
          return jsonResponse({ success: false, error: "Missing item ID or rename values" }, 400);
        }

        const { data: oldItem } = await supabaseAdmin.from("content_items").select("*").eq("id", itemId).single();
        if (!oldItem) return jsonResponse({ success: false, error: "Item not found" }, 404);

        const patch: Record<string, unknown> = { updated_at: nowIso };
        if (newTitle) patch.title = newTitle;
        if (newTargetFilename) patch.target_filename = newTargetFilename;
        if (newRelPath !== undefined) patch.target_relative_path = newRelPath;
        if (body.target_group) patch.target_group = body.target_group;
        if (body.is_required !== undefined) patch.is_required = Boolean(body.is_required);

        const { error: updErr } = await supabaseAdmin.from("content_items").update(patch).eq("id", itemId);
        if (updErr) throw updErr;

        await supabaseAdmin.from("file_change_logs").insert({
          content_item_id: itemId,
          action: "rename",
          old_value: { title: oldItem.title, target_filename: oldItem.target_filename },
          new_value: patch,
          performed_by: user.id,
        });
        await writeAudit("RENAME_FILE_ITEM", "content_item", itemId, patch);
        return jsonResponse({ success: true });
      }

      case "toggle_publish_file": {
        const itemId = String(body.content_item_id || "");
        const publish = Boolean(body.is_published);
        const { error } = await supabaseAdmin
          .from("content_items")
          .update({ is_published: publish, is_draft: !publish, updated_at: nowIso })
          .eq("id", itemId);
        if (error) throw error;

        await supabaseAdmin.from("file_change_logs").insert({
          content_item_id: itemId,
          action: publish ? "publish" : "unpublish",
          new_value: { is_published: publish },
          performed_by: user.id,
        });
        await writeAudit(publish ? "PUBLISH_FILE" : "UNPUBLISH_FILE", "content_item", itemId, { is_published: publish });
        return jsonResponse({ success: true });
      }

      case "delete_file_item": {
        const itemId = String(body.content_item_id || "");
        const { data: item } = await supabaseAdmin.from("content_items").select("*").eq("id", itemId).maybeSingle();
        if (!item) return jsonResponse({ success: false, error: "File item not found" }, 404);

        if (item.storage_path) {
          await supabaseAdmin.storage.from("worm-content-files").remove([item.storage_path]);
        }
        await supabaseAdmin.from("file_change_logs").insert({
          content_item_id: itemId,
          action: "delete",
          old_value: { title: item.title, target_filename: item.target_filename, storage_path: item.storage_path },
          performed_by: user.id,
        });
        const { error } = await supabaseAdmin.from("content_items").delete().eq("id", itemId);
        if (error) throw error;
        await writeAudit("DELETE_FILE_ITEM", "content_item", itemId, { title: item.title });
        return jsonResponse({ success: true });
      }

      case "list_file_change_logs": {
        const { data, error } = await supabaseAdmin
          .from("file_change_logs")
          .select("*")
          .order("created_at", { ascending: false })
          .limit(100);
        if (error) throw error;
        return jsonResponse({ success: true, logs: data || [] });
      }

      // ----------------------------------------------------------------------
      // 6. GLOBAL FILE UPDATE
      // ----------------------------------------------------------------------
      case "trigger_global_file_update": {
        const { data: sysRow } = await supabaseAdmin
          .from("system_status")
          .select("global_file_revision")
          .eq("id", SYSTEM_STATUS_ID)
          .single();

        const nextRevision = (sysRow?.global_file_revision || 1) + 1;
        const announcementAr = body.announcement_ar || undefined;
        const announcementEn = body.announcement_en || undefined;

        const patch: Record<string, unknown> = {
          global_file_revision: nextRevision,
          last_file_update_at: nowIso,
          updated_at: nowIso,
        };
        if (announcementAr) patch.announcement_body_ar = announcementAr;
        if (announcementEn) patch.announcement_body_en = announcementEn;

        const { error } = await supabaseAdmin.from("system_status").update(patch).eq("id", SYSTEM_STATUS_ID);
        if (error) throw error;

        await supabaseAdmin.from("file_change_logs").insert({
          content_item_id: null,
          action: "global_update_trigger",
          new_value: { global_file_revision: nextRevision, announcement_ar: announcementAr },
          performed_by: user.id,
        });
        await writeAudit("TRIGGER_GLOBAL_FILE_UPDATE", "system_status", SYSTEM_STATUS_ID, { global_file_revision: nextRevision });
        return jsonResponse({ success: true, global_file_revision: nextRevision });
      }

      // ----------------------------------------------------------------------
      // 7. GLOBAL SHUTDOWN / MAINTENANCE & INSTANT RESUME
      // ----------------------------------------------------------------------
      case "start_maintenance": {
        const reasonAr = String(body.reason_ar || "الخادم في وضع الصيانة المؤقتة لتحديث الملفات.");
        const reasonEn = String(body.reason_en || "Server is undergoing scheduled maintenance.");
        const durationMins = body.duration_minutes ? Number(body.duration_minutes) : null;
        const endsAt = durationMins && durationMins > 0
          ? new Date(Date.now() + durationMins * 60_000).toISOString()
          : null;

        const { error } = await supabaseAdmin
          .from("system_status")
          .update({
            maintenance_mode: true,
            maintenance_reason_ar: reasonAr,
            maintenance_reason_en: reasonEn,
            maintenance_started_at: nowIso,
            maintenance_ends_at: endsAt,
            maintenance_duration_minutes: durationMins,
            updated_at: nowIso,
          })
          .eq("id", SYSTEM_STATUS_ID);
        if (error) throw error;

        await writeAudit("START_MAINTENANCE", "system_status", SYSTEM_STATUS_ID, { reasonAr, durationMins, endsAt });
        return jsonResponse({ success: true, ends_at: endsAt });
      }

      case "schedule_maintenance": {
        const title = String(body.title || "صيانة مجدولة");
        const reasonAr = String(body.reason_ar || title);
        const reasonEn = String(body.reason_en || title);
        const startsAt = String(body.starts_at || "");
        const endsAt = String(body.ends_at || "");
        if (!startsAt || !endsAt || new Date(endsAt).getTime() <= new Date(startsAt).getTime()) {
          return jsonResponse({ success: false, error: "Invalid schedule start/end timestamps" }, 400);
        }

        const { data, error } = await supabaseAdmin
          .from("maintenance_schedules")
          .insert({
            title,
            reason_ar: reasonAr,
            reason_en: reasonEn,
            starts_at: startsAt,
            ends_at: endsAt,
            status: "scheduled",
            created_by: user.id,
          })
          .select()
          .single();
        if (error) throw error;
        await writeAudit("SCHEDULE_MAINTENANCE", "maintenance_schedule", data.id, { startsAt, endsAt });
        return jsonResponse({ success: true, schedule: data });
      }

      case "cancel_maintenance_schedule": {
        const schedId = String(body.schedule_id || "");
        const { error } = await supabaseAdmin
          .from("maintenance_schedules")
          .update({ status: "cancelled" })
          .eq("id", schedId);
        if (error) throw error;
        await writeAudit("CANCEL_MAINTENANCE_SCHEDULE", "maintenance_schedule", schedId, {});
        return jsonResponse({ success: true });
      }

      case "instant_resume": {
        // Authoritatively stops manual maintenance AND closes any currently active scheduled maintenance early
        const { error: sysErr } = await supabaseAdmin
          .from("system_status")
          .update({
            maintenance_mode: false,
            maintenance_ends_at: null,
            maintenance_duration_minutes: null,
            instant_resumed_at: nowIso,
            instant_resumed_by: user.id,
            updated_at: nowIso,
          })
          .eq("id", SYSTEM_STATUS_ID);
        if (sysErr) throw sysErr;

        await supabaseAdmin
          .from("maintenance_schedules")
          .update({ status: "resumed_early", ends_at: nowIso })
          .in("status", ["scheduled", "active"])
          .lte("starts_at", nowIso);

        await writeAudit("INSTANT_RESUME", "system_status", SYSTEM_STATUS_ID, { resumed_at: nowIso, resumed_by: user.id });
        return jsonResponse({ success: true, resumed_at: nowIso });
      }

      // ----------------------------------------------------------------------
      // 8. APP UPDATES (APK)
      // ----------------------------------------------------------------------
      case "list_app_updates": {
        const { data, error } = await supabaseAdmin
          .from("app_updates")
          .select("*")
          .order("version_code", { ascending: false })
          .limit(50);
        if (error) throw error;
        return jsonResponse({ success: true, updates: data || [] });
      }

      case "create_app_update": {
        const versionCode = Number(body.version_code || 0);
        const versionName = String(body.version_name || "").trim();
        const changelogAr = String(body.changelog_ar || "").trim();
        const changelogEn = String(body.changelog_en || changelogAr).trim();
        const downloadUrl = String(body.download_url || "").trim();
        const apkStoragePath = body.apk_storage_path ? String(body.apk_storage_path) : null;
        const sha256 = String(body.sha256 || "").trim();
        const sizeBytes = Number(body.size_bytes || 0);
        const isMandatory = Boolean(body.is_mandatory);

        if (!versionCode || !versionName || !sha256 || (!downloadUrl && !apkStoragePath)) {
          return jsonResponse({ success: false, error: "Missing required APK release fields" }, 400);
        }

        const { data, error } = await supabaseAdmin
          .from("app_updates")
          .insert({
            version_code: versionCode,
            version_name: versionName,
            changelog_ar: changelogAr,
            changelog_en: changelogEn,
            download_url: downloadUrl,
            apk_storage_path: apkStoragePath,
            sha256,
            size_bytes: sizeBytes,
            is_mandatory: isMandatory,
            is_active: true,
            created_by: user.id,
          })
          .select()
          .single();
        if (error) throw error;

        await writeAudit("CREATE_APP_UPDATE", "app_update", data?.id || null, { versionCode, versionName, isMandatory });
        return jsonResponse({ success: true, update: data });
      }

      case "toggle_app_update": {
        const updateId = String(body.update_id || "");
        const isActive = Boolean(body.is_active);
        const { error } = await supabaseAdmin.from("app_updates").update({ is_active: isActive }).eq("id", updateId);
        if (error) throw error;
        await writeAudit("TOGGLE_APP_UPDATE", "app_update", updateId, { is_active: isActive });
        return jsonResponse({ success: true });
      }

      // ----------------------------------------------------------------------
      // 9. TELEGRAM & SERVER SETTINGS
      // ----------------------------------------------------------------------
      case "update_server_settings": {
        const patch: Record<string, unknown> = { updated_at: nowIso };
        if (body.server_name) patch.server_name = String(body.server_name);
        if (body.announcement_body_ar !== undefined) patch.announcement_body_ar = String(body.announcement_body_ar);
        if (body.announcement_body_en !== undefined) patch.announcement_body_en = String(body.announcement_body_en);
        if (body.telegram_channel_url) patch.telegram_channel_url = String(body.telegram_channel_url);
        if (body.telegram_channel_username) patch.telegram_channel_username = String(body.telegram_channel_username);
        if (body.require_telegram_membership !== undefined) patch.require_telegram_membership = Boolean(body.require_telegram_membership);
        if (body.require_active_subscription !== undefined) patch.require_active_subscription = Boolean(body.require_active_subscription);
        if (body.min_update_interval_minutes !== undefined) patch.min_update_interval_minutes = Math.max(0, Number(body.min_update_interval_minutes));

        const { error } = await supabaseAdmin.from("system_status").update(patch).eq("id", SYSTEM_STATUS_ID);
        if (error) throw error;
        await writeAudit("UPDATE_SERVER_SETTINGS", "system_status", SYSTEM_STATUS_ID, patch);
        return jsonResponse({ success: true });
      }

      // ----------------------------------------------------------------------
      // 10. AUDIT & SECURITY LOGS
      // ----------------------------------------------------------------------
      case "list_audit_logs": {
        const { data, error } = await supabaseAdmin
          .from("audit_logs")
          .select("*")
          .order("timestamp", { ascending: false })
          .limit(200);
        if (error) throw error;
        return jsonResponse({ success: true, logs: data || [] });
      }

      case "reset_device": {
        const targetId = String(body.user_id || "");
        if (!targetId) return jsonResponse({ success: false, error: "معرّف المستخدم ناقص" }, 400);
        const { error } = await supabaseAdmin
          .from("user_profiles")
          .update({ device_id: null, device_model: null, device_locked_at: null })
          .eq("id", targetId);
        if (error) throw error;
        await writeAudit("RESET_DEVICE", "user", targetId, {});
        return jsonResponse({ success: true, message: "تم إعادة ضبط الجهاز. أول جهاز يفتح الحساب هيتربط." });
      }

      case "list_suspicious": {
        const since = new Date(Date.now() - 7 * 86400000).toISOString();
        const { data: evs, error } = await supabaseAdmin
          .from("security_events")
          .select("event_type, user_id, created_at, description")
          .not("user_id", "is", null)
          .in("event_type", ["INVALID_ACTIVATION_CODE", "DEVICE_MISMATCH", "TAMPERED_APP", "RATE_LIMIT_EXCEEDED", "BANNED_DOWNLOAD_ATTEMPT", "TELEGRAM_LOGIN_FAILED"])
          .gte("created_at", since)
          .order("created_at", { ascending: false })
          .limit(2000);
        if (error) throw error;
        const weights: Record<string, number> = { INVALID_ACTIVATION_CODE: 1, DEVICE_MISMATCH: 5, TAMPERED_APP: 10, RATE_LIMIT_EXCEEDED: 3, BANNED_DOWNLOAD_ATTEMPT: 4, TELEGRAM_LOGIN_FAILED: 1 };
        const agg = new Map<string, { counts: Record<string, number>; score: number; last: string }>();
        for (const e of evs || []) {
          const k = String(e.user_id);
          const a = agg.get(k) || { counts: {}, score: 0, last: e.created_at };
          a.counts[e.event_type] = (a.counts[e.event_type] || 0) + 1;
          a.score += weights[e.event_type] || 1;
          agg.set(k, a);
        }
        const ids = [...agg.keys()];
        const { data: profs } = ids.length
          ? await supabaseAdmin.from("user_profiles").select("id, display_name, telegram_username, telegram_user_id, is_banned, device_id, device_model").in("id", ids)
          : { data: [] as Record<string, unknown>[] };
        const rows = ids.map((id) => {
          const p = (profs || []).find((x: Record<string, unknown>) => x.id === id) || {};
          const a = agg.get(id)!;
          return { user_id: id, display_name: p.display_name || null, telegram_username: p.telegram_username || null, telegram_user_id: p.telegram_user_id || null, is_banned: Boolean(p.is_banned), device_model: p.device_model || null, has_device: Boolean(p.device_id), counts: a.counts, score: a.score, last_event_at: a.last };
        }).sort((x, y) => y.score - x.score).slice(0, 100);
        return jsonResponse({ success: true, suspicious: rows });
      }

      case "list_security_events": {
        const { data, error } = await supabaseAdmin
          .from("security_events")
          .select("*")
          .order("created_at", { ascending: false })
          .limit(200);
        if (error) throw error;
        return jsonResponse({ success: true, events: data || [] });
      }

      default:
        return jsonResponse({ success: false, error: `Unknown admin action: ${action}` }, 400);
    }
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : "Internal Server Error";
    return jsonResponse({ success: false, error: message }, 500);
  }
});
