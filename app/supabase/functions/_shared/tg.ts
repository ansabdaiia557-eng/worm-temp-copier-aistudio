export async function sha256Hex(s: string): Promise<string> {
  const buf = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(s));
  return Array.from(new Uint8Array(buf)).map((b) => b.toString(16).padStart(2, "0")).join("");
}

// Derived from the bot token so no extra secret is needed.
export async function webhookSecret(botToken: string): Promise<string> {
  return (await sha256Hex("wh:" + botToken)).slice(0, 48);
}

export async function codeHash(code: string, botToken: string): Promise<string> {
  return await sha256Hex("code:" + code + ":" + botToken);
}

export function escHtml(s: string): string {
  return String(s ?? "").replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

// Post an HTML message to the channel. Never throws: notifications must not break admin actions.
export async function notifyChannel(html: string): Promise<boolean> {
  try {
    const token = Deno.env.get("TELEGRAM_BOT_TOKEN");
    const chat = Deno.env.get("TELEGRAM_CHANNEL_ID") || "@Kharbash_anas";
    if (!token) return false;
    const r = await fetch(`https://api.telegram.org/bot${token}/sendMessage`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ chat_id: chat, text: html, parse_mode: "HTML", disable_web_page_preview: true }),
    });
    if (!r.ok) console.error("notifyChannel failed", r.status, await r.text().catch(() => ""));
    return r.ok;
  } catch (e) {
    console.error("notifyChannel error", e);
    return false;
  }
}

// Posts an "update" notification and deletes the previous update post, so only the latest stays visible.
// Never touches other channel posts (only the id stored under server_settings.last_update_post).
export async function notifyUpdate(admin: any, html: string): Promise<{ ok: boolean; error?: string; message_id?: number }> {
  try {
    const token = Deno.env.get("TELEGRAM_BOT_TOKEN");
    const chat = Deno.env.get("TELEGRAM_CHANNEL_ID") || "@Kharbash_anas";
    if (!token) return { ok: false, error: "no token" };
    const r = await fetch(`https://api.telegram.org/bot${token}/sendMessage`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ chat_id: chat, text: html, parse_mode: "HTML", disable_web_page_preview: true }),
    });
    const j = await r.json().catch(() => ({}));
    if (!j?.ok) {
      console.error("notifyUpdate failed", JSON.stringify(j));
      return { ok: false, error: String(j?.description || r.status) };
    }
    const newId = j.result.message_id as number;
    const { data: prev } = await admin.from("server_settings").select("value").eq("key", "last_update_post").maybeSingle();
    const prevId = prev?.value?.message_id;
    if (prevId && prevId !== newId) {
      await fetch(`https://api.telegram.org/bot${token}/deleteMessage`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ chat_id: chat, message_id: prevId }),
      }).catch(() => {});
    }
    await admin.from("server_settings").upsert({ key: "last_update_post", value: { message_id: newId }, description: "Last auto update post in the channel", updated_at: new Date().toISOString() });
    return { ok: true, message_id: newId };
  } catch (e) {
    console.error("notifyUpdate error", e);
    return { ok: false, error: String(e) };
  }
}

// Required chats: a user must be a member of ALL of these.
export const REQUIRED_CHATS: { id: string; name: string; url: string }[] = [
  { id: "@Kharbash_anas", name: "القناة الرئيسية", url: "https://t.me/Kharbash_anas" },
  { id: "@Scribble_explanations", name: "قناة الشروحات", url: "https://t.me/Scribble_explanations" },
  { id: "@Kharbash_Solution", name: "جروب حل المشاكل", url: "https://t.me/Kharbash_Solution" },
];

export async function checkAllMemberships(token: string, userId: string | number) {
  const results = await Promise.all(REQUIRED_CHATS.map(async (c) => {
    try {
      const r = await fetch(`https://api.telegram.org/bot${token}/getChatMember?chat_id=${encodeURIComponent(c.id)}&user_id=${encodeURIComponent(String(userId))}`);
      const j = await r.json();
      const st = j?.result?.status;
      const member = Boolean(j?.ok) && (["creator", "administrator", "member"].includes(st) || (st === "restricted" && j.result.is_member === true));
      return { ...c, member, error: j?.ok ? null : (j?.description || "error") };
    } catch (e) {
      return { ...c, member: false, error: String(e) };
    }
  }));
  const missing = results.filter((r) => !r.member);
  return { all: missing.length === 0, results, missing };
}
