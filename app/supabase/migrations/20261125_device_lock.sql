-- One device per account + suspicious activity support
ALTER TABLE public.user_profiles ADD COLUMN IF NOT EXISTS device_id TEXT;
ALTER TABLE public.user_profiles ADD COLUMN IF NOT EXISTS device_model TEXT;
ALTER TABLE public.user_profiles ADD COLUMN IF NOT EXISTS device_locked_at TIMESTAMPTZ;
CREATE INDEX IF NOT EXISTS idx_security_events_user_time ON public.security_events(user_id, created_at DESC);
INSERT INTO public.server_settings (key, value, description)
VALUES ('allow_legacy_clients', 'true'::jsonb, 'Allow old app versions that do not send an app signature or device id')
ON CONFLICT (key) DO NOTHING;
