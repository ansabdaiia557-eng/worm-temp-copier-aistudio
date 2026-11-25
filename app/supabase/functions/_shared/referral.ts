import type { SupabaseClient } from "https://esm.sh/@supabase/supabase-js@2";

export const REFERRER_BONUS_DAYS = 3;
export const INVITED_BONUS_DAYS = 1;

async function dm(tgId: number | string, text: string) {
  try {
    const token = Deno.env.get("TELEGRAM_BOT_TOKEN");
    if (!token) return;
    await fetch(`https://api.telegram.org/bot${token}/sendMessage`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ chat_id: tgId, text, parse_mode: "HTML" }),
    });
  } catch { /* ignore */ }
}

export async function addDays(admin: SupabaseClient, userId: string, days: number, plan: string) {
  const now = new Date();
  const { data: sub } = await admin
    .from("subscriptions").select("id, ends_at")
    .eq("user_id", userId).eq("status", "active").gt("ends_at", now.toISOString())
    .order("ends_at", { ascending: false }).limit(1).maybeSingle();
  if (sub) {
    await admin.from("subscriptions")
      .update({ ends_at: new Date(new Date(sub.ends_at).getTime() + days * 86400000).toISOString() })
      .eq("id", sub.id);
  } else {
    await admin.from("subscriptions").insert({
      user_id: userId, plan_name: plan, status: "active",
      starts_at: now.toISOString(), ends_at: new Date(now.getTime() + days * 86400000).toISOString(),
      notes: "Referral bonus",
    });
  }
}

// Call after any new subscription is created/extended for `invitedUserId`. Never throws.
export async function processReferralReward(admin: SupabaseClient, invitedUserId: string): Promise<void> {
  try {
    const { data: inv } = await admin.from("user_profiles")
      .select("id, telegram_user_id, device_id").eq("id", invitedUserId).maybeSingle();
    if (!inv?.telegram_user_id) return;
    const { data: ref } = await admin.from("referrals").select("*")
      .eq("invited_tg_id", inv.telegram_user_id).eq("status", "pending").maybeSingle();
    if (!ref) return;

    // First subscription only
    const { count } = await admin.from("subscriptions")
      .select("id", { count: "exact", head: true }).eq("user_id", invitedUserId);
    if ((count || 0) > 1) {
      await admin.from("referrals").update({ status: "rejected", reason: "not_first_subscription" }).eq("id", ref.id);
      return;
    }

    const { data: referrer } = await admin.from("user_profiles")
      .select("id, telegram_user_id, device_id, is_banned").eq("telegram_user_id", ref.referrer_tg_id).maybeSingle();
    const reject = async (reason: string) => {
      await admin.from("referrals").update({ status: "rejected", reason }).eq("id", ref.id);
    };
    if (!referrer || referrer.is_banned) return await reject("referrer_unavailable");
    if (referrer.id === invitedUserId || Number(ref.referrer_tg_id) === Number(inv.telegram_user_id)) return await reject("self_referral");

    // Duplicate-account checks via device lock
    if (inv.device_id) {
      if (referrer.device_id && referrer.device_id === inv.device_id) return await reject("same_device_as_referrer");
      const { data: dup } = await admin.from("user_profiles").select("id")
        .eq("device_id", inv.device_id).neq("id", invitedUserId).limit(1);
      if (dup && dup.length > 0) return await reject("device_used_by_other_account");
    }

    await addDays(admin, referrer.id, REFERRER_BONUS_DAYS, "Referral bonus");
    await addDays(admin, invitedUserId, INVITED_BONUS_DAYS, "Referral bonus");
    await admin.from("referrals").update({
      status: "rewarded", referrer_days: REFERRER_BONUS_DAYS, invited_days: INVITED_BONUS_DAYS,
      rewarded_at: new Date().toISOString(),
    }).eq("id", ref.id);

    await dm(ref.referrer_tg_id, `🎉 <b>مكافأة دعوة!</b>\n\nصاحبك اللي دعيته فعّل اشتراكه، واتضاف لاشتراكك <b>${REFERRER_BONUS_DAYS} أيام</b> هدية 🎁\nشوف دعواتك بكتابة: دعواتي`);
    await dm(inv.telegram_user_id, `🎁 <b>هدية ترحيب!</b>\n\nاتضاف ليوم إضافي لاشتراكك لأنك دخلت عن طريق دعوة صاحبك.`);
  } catch (e) {
    console.error("referral error", e);
  }
}
