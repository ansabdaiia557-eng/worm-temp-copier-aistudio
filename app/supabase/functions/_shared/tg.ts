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
