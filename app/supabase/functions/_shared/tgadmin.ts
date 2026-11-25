// Admin panel inside the Telegram bot. Only the owner's Telegram ID may use it.
import type { SupabaseClient } from "https://esm.sh/@supabase/supabase-js@2";
import { addDays } from "./referral.ts";
import { escHtml } from "./tg.ts";

export const ADMIN_TG_ID = 8031381457;
const PAGE = 6;

type Tg = (method: string, payload: Record<string, unknown>) => Promise<any>;

function fmtDate(iso: string | null | undefined): string {
  if (!iso) return "-";
  return new Date(iso).toLocaleDateString("en-GB", { timeZone: "Africa/Cairo" });
}

async function subOf(admin: SupabaseClient, ids: string[]) {
  const map = new Map<string, { status: string; ends_at: string }>();
  if (!ids.length) return map;
  const { data } = await admin.from("subscriptions").select("user_id, status, ends_at").in("user_id", ids).order("ends_at", { ascending: false });
  const now = Date.now();
  for (const s of data || []) {
    const active = s.status === "active" && new Date(s.ends_at).getTime() > now;
    const cur = map.get(s.user_id);
    if (!cur || (active && cur.status !== "active")) map.set(s.user_id, { status: active ? "active" : s.status === "active" ? "expired" : s.status, ends_at: s.ends_at });
  }
  return map;
}

function subLabel(s?: { status: string; ends_at: string }): string {
  if (!s) return "بدون اشتراك";
  if (s.status === "active") return `✅ شغال لحد ${fmtDate(s.ends_at)}`;
  if (s.status === "revoked") return "🚫 ملغي";
  if (s.status === "paused") return "⏸ موقوف";
  return "⌛ منتهي";
}

function nameOf(p: any): string {
  return p.telegram_username ? "@" + p.telegram_username : (p.display_name || String(p.telegram_user_id || p.id).slice(0, 8));
}

async function send(tg: Tg, chatId: number, msgId: number | undefined, text: string, kb: unknown) {
  const payload: Record<string, unknown> = { chat_id: chatId, text, parse_mode: "HTML", reply_markup: kb, disable_web_page_preview: true };
  if (msgId) {
    const r = await tg("editMessageText", { ...payload, message_id: msgId });
    if (r?.ok || /not modified/i.test(r?.description || "")) return;
  }
  await tg("sendMessage", payload);
}

async function menu(tg: Tg, chatId: number, msgId?: number) {
  await send(tg, chatId, msgId, "🛠 <b>لوحة الأدمن</b>\n\nاختار:", {
    inline_keyboard: [
      [{ text: "👥 المستخدمين", callback_data: "a:l:0" }],
      [{ text: "🔎 المحظورين", callback_data: "a:lb:0" }],
    ],
  });
}

async function list(admin: SupabaseClient, tg: Tg, chatId: number, msgId: number | undefined, page: number, bannedOnly: boolean) {
  let q = admin.from("user_profiles").select("id, display_name, telegram_username, telegram_user_id, is_banned, device_model, device_id, role", { count: "exact" });
  if (bannedOnly) q = q.eq("is_banned", true);
  const { data, count } = await q.order("created_at", { ascending: false }).range(page * PAGE, page * PAGE + PAGE - 1);
  const rows = data || [];
  const subs = await subOf(admin, rows.map((r: any) => r.id));
  const total = count || 0;
  const pages = Math.max(1, Math.ceil(total / PAGE));
  const lines = rows.map((p: any, i: number) =>
    `<b>${page * PAGE + i + 1}. ${escHtml(nameOf(p))}</b>${p.role && p.role !== "user" ? " 👑" : ""}\n   ${subLabel(subs.get(p.id))}\n   📱 ${p.device_id ? escHtml(p.device_model || "جهاز مربوط") : "مفيش جهاز"}   ${p.is_banned ? "⛔ محظور" : "🟢 شغال"}`);
  const kb: any[] = rows.map((p: any) => [{ text: `${p.is_banned ? "⛔ " : ""}${nameOf(p)}`, callback_data: `a:u:${p.id}` }]);
  const nav: any[] = [];
  const tag = bannedOnly ? "lb" : "l";
  if (page > 0) nav.push({ text: "◀️ السابق", callback_data: `a:${tag}:${page - 1}` });
  if ((page + 1) * PAGE < total) nav.push({ text: "التالي ▶️", callback_data: `a:${tag}:${page + 1}` });
  if (nav.length) kb.push(nav);
  kb.push([{ text: "🏠 القايمة", callback_data: "a:m" }]);
  await send(tg, chatId, msgId,
    `${bannedOnly ? "⛔ <b>المحظورين</b>" : "👥 <b>المستخدمين</b>"} (${total}) - صفحة ${page + 1}/${pages}\n\n${lines.join("\n\n") || "مفيش حد"}`,
    { inline_keyboard: kb });
}

async function detail(admin: SupabaseClient, tg: Tg, chatId: number, msgId: number | undefined, uid: string, note = "") {
  const { data: p } = await admin.from("user_profiles").select("*").eq("id", uid).maybeSingle();
  if (!p) return await send(tg, chatId, msgId, "المستخدم مش موجود.", { inline_keyboard: [[{ text: "🏠 القايمة", callback_data: "a:m" }]] });
  const subs = await subOf(admin, [uid]);
  const text = `👤 <b>${escHtml(nameOf(p))}</b>${note ? "\n\n" + note : ""}\n\n` +
    `🆔 Telegram: <code>${p.telegram_user_id || "-"}</code>\n` +
    `💳 الاشتراك: ${subLabel(subs.get(uid))}\n` +
    `📱 الجهاز: ${p.device_id ? escHtml(p.device_model || "مربوط") : "مفيش جهاز"}\n` +
    `🚦 الحالة: ${p.is_banned ? "⛔ محظور" : "🟢 شغال"}\n` +
    `🕒 آخر دخول: ${fmtDate(p.last_login_at)}`;
  const kb: any[] = [
    [{ text: "➕ يوم", callback_data: `a:g1:${uid}` }, { text: "➕ 7 أيام", callback_data: `a:g7:${uid}` }, { text: "➕ 30 يوم", callback_data: `a:g30:${uid}` }],
    [p.is_banned ? { text: "✅ فك الحظر", callback_data: `a:ub:${uid}` } : { text: "⛔ حظر", callback_data: `a:b:${uid}` }],
    [{ text: "📱 ريسيت الجهاز", callback_data: `a:rd:${uid}` }, { text: "🚫 إلغاء الاشتراك", callback_data: `a:rs:${uid}` }],
    [{ text: "◀️ القايمة", callback_data: "a:l:0" }],
  ];
  await send(tg, chatId, msgId, text, { inline_keyboard: kb });
}

// Returns true when the update was an admin interaction (handled or refused).
export async function handleAdmin(admin: SupabaseClient, tg: Tg, token: string, update: any): Promise<boolean> {
  const cb = update.callback_query;
  const msg = update.message;
  const from = cb?.from || msg?.from;
  const text: string = (msg?.text || "").trim();
  const data: string = cb?.data || "";
  const isAdminCmd = /^\/admin(@\w+)?$/i.test(text) || /^(لوحة الأدمن)$/.test(text);
  const isAdminCb = data.startsWith("a:");
  if (!isAdminCmd && !isAdminCb) return false;
  if (!from || Number(from.id) !== ADMIN_TG_ID) {
    if (cb) await tg("answerCallbackQuery", { callback_query_id: cb.id });
    return true; // ignore everyone else silently
  }
  const chatType = cb?.message?.chat?.type ?? msg?.chat?.type;
  if (chatType !== "private") return true;
  const chatId = cb?.message?.chat?.id ?? msg?.chat?.id;
  const msgId = cb?.message?.message_id;
  if (cb) await tg("answerCallbackQuery", { callback_query_id: cb.id });
  if (isAdminCmd) { await menu(tg, chatId); return true; }

  const [, act, arg] = data.split(":");
  const uid = data.split(":").slice(2).join(":");
  if (act === "m") return await menu(tg, chatId, msgId), true;
  if (act === "l") return await list(admin, tg, chatId, msgId, Number(arg) || 0, false), true;
  if (act === "lb") return await list(admin, tg, chatId, msgId, Number(arg) || 0, true), true;
  if (act === "u") return await detail(admin, tg, chatId, msgId, uid), true;

  const { data: target } = await admin.from("user_profiles").select("id, role, telegram_user_id").eq("id", uid).maybeSingle();
  if (!target) return await detail(admin, tg, chatId, msgId, uid), true;
  const nowIso = new Date().toISOString();
  let note = "";
  if (/^g(1|7|30)$/.test(act)) {
    const days = Number(act.slice(1));
    await addDays(admin, uid, days, "Granted via bot");
    note = `✅ اتضاف ${days} يوم للاشتراك.`;
  } else if (act === "b") {
    if (target.role && target.role !== "user") note = "⚠️ مينفعش تحظر حساب أدمن.";
    else {
      await admin.from("user_profiles").update({ is_banned: true, account_status: "banned", ban_reason: "Banned by admin (bot)", banned_at: nowIso }).eq("id", uid);
      await admin.from("user_sessions").update({ is_active: false }).eq("user_id", uid);
      note = "⛔ اتحظر.";
    }
  } else if (act === "ub") {
    await admin.from("user_profiles").update({ is_banned: false, account_status: "active", ban_reason: null, banned_at: null, banned_by: null }).eq("id", uid);
    note = "✅ اتفك الحظر.";
  } else if (act === "rd") {
    await admin.from("user_profiles").update({ device_id: null, device_model: null, device_locked_at: null }).eq("id", uid);
    note = "📱 اتعمل ريسيت للجهاز. أول موبايل يفتح الحساب هيتربط.";
  } else if (act === "rs") {
    await admin.from("subscriptions").update({ status: "revoked" }).eq("user_id", uid).eq("status", "active");
    note = "🚫 الاشتراك اتلغى.";
  }
  await detail(admin, tg, chatId, msgId, uid, note);
  return true;
}
