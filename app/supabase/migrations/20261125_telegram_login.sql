-- Telegram bot code login: single-use, hashed, short-lived codes
CREATE TABLE IF NOT EXISTS public.telegram_login_codes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    telegram_user_id BIGINT NOT NULL,
    telegram_username TEXT,
    telegram_first_name TEXT,
    code_hash TEXT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_tg_login_codes_hash ON public.telegram_login_codes(code_hash);
CREATE INDEX IF NOT EXISTS idx_tg_login_codes_user ON public.telegram_login_codes(telegram_user_id, created_at DESC);
ALTER TABLE public.telegram_login_codes ENABLE ROW LEVEL SECURITY;
-- No policies: only the service role (edge functions) can read or write.
