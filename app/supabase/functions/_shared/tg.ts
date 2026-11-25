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
