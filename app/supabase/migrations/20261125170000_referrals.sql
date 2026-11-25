CREATE TABLE IF NOT EXISTS public.referrals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    referrer_tg_id BIGINT NOT NULL,
    invited_tg_id BIGINT NOT NULL UNIQUE,
    status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'rewarded', 'rejected')),
    reason TEXT,
    referrer_days INTEGER NOT NULL DEFAULT 0,
    invited_days INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    rewarded_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_referrals_referrer ON public.referrals(referrer_tg_id, status);
ALTER TABLE public.referrals ENABLE ROW LEVEL SECURITY;
