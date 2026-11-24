-- ==============================================================================
-- MIGRATION: 20261124_ans_server_control.sql
-- PURPOSE: ANS SERVER CONTROL - Full Backend Schema, Users, Subscriptions,
--          Activation Codes, File Versions, Global File Updates, Maintenance,
--          Instant Resume, Telegram Verification, App Updates, & Security Events
-- TARGET: Supabase PostgreSQL Database (Project Ref: nqwmahahhichwmnxccrg)
-- IDEMPOTENT & SAFE TO RE-RUN
-- ==============================================================================

BEGIN;

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ==============================================================================
-- 1. SECURITY DEFINER HELPERS
-- ==============================================================================

CREATE OR REPLACE FUNCTION public.is_admin()
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT coalesce(
    (auth.jwt() -> 'app_metadata' ->> 'role') IN ('admin', 'super_admin')
    OR (auth.jwt() -> 'app_metadata' ->> 'is_admin')::boolean = true,
    false
  );
$$;

CREATE OR REPLACE FUNCTION public.is_super_admin()
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT coalesce(
    (auth.jwt() -> 'app_metadata' ->> 'role') = 'super_admin',
    false
  );
$$;

CREATE OR REPLACE FUNCTION public.handle_updated_at()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = NOW();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ==============================================================================
-- 2. USER MANAGEMENT TABLES
-- ==============================================================================

CREATE TABLE IF NOT EXISTS public.user_profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    email TEXT,
    display_name TEXT,
    role TEXT NOT NULL DEFAULT 'user' CHECK (role IN ('user', 'moderator', 'admin', 'super_admin')),
    account_status TEXT NOT NULL DEFAULT 'active' CHECK (account_status IN ('active', 'suspended', 'banned')),
    is_banned BOOLEAN NOT NULL DEFAULT FALSE,
    ban_reason TEXT,
    banned_at TIMESTAMPTZ,
    banned_by UUID REFERENCES auth.users(id),
    telegram_user_id BIGINT,
    telegram_username TEXT,
    telegram_verified BOOLEAN NOT NULL DEFAULT FALSE,
    telegram_verified_at TIMESTAMPTZ,
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_content_items_category ON public.content_items(category);
CREATE INDEX IF NOT EXISTS idx_content_items_published ON public.content_items(is_published);
CREATE INDEX IF NOT EXISTS idx_content_items_sha256 ON public.content_items(sha256);
CREATE INDEX IF NOT EXISTS idx_content_items_storage_path ON public.content_items(storage_path);
CREATE INDEX IF NOT EXISTS idx_audit_logs_timestamp ON public.audit_logs(timestamp DESC);

DROP TRIGGER IF EXISTS trg_system_status_updated_at ON public.system_status;
CREATE TRIGGER trg_system_status_updated_at
BEFORE UPDATE ON public.system_status
FOR EACH ROW EXECUTE FUNCTION public.handle_updated_at();

DROP TRIGGER IF EXISTS trg_content_items_updated_at ON public.content_items;
CREATE TRIGGER trg_content_items_updated_at
BEFORE UPDATE ON public.content_items
FOR EACH ROW EXECUTE FUNCTION public.handle_updated_at();

CREATE INDEX IF NOT EXISTS idx_user_profiles_email ON public.user_profiles(email);
CREATE INDEX IF NOT EXISTS idx_user_profiles_banned ON public.user_profiles(is_banned);
CREATE INDEX IF NOT EXISTS idx_user_profiles_telegram ON public.user_profiles(telegram_user_id);

-- Auto-create profile when a new auth user signs up
CREATE OR REPLACE FUNCTION public.handle_new_auth_user()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    INSERT INTO public.user_profiles (id, email, display_name, role, account_status, created_at)
    VALUES (
        NEW.id,
        NEW.email,
        COALESCE(NEW.raw_user_meta_data ->> 'display_name', split_part(COALESCE(NEW.email, 'user'), '@', 1)),
        CASE
            WHEN (NEW.raw_app_meta_data ->> 'role') IN ('admin', 'super_admin') THEN NEW.raw_app_meta_data ->> 'role'
            ELSE 'user'
        END,
        'active',
        NOW()
    )
    ON CONFLICT (id) DO UPDATE SET
        email = EXCLUDED.email,
        updated_at = NOW();
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_auth_user_created ON auth.users;
CREATE TRIGGER trg_auth_user_created
AFTER INSERT ON auth.users
FOR EACH ROW EXECUTE FUNCTION public.handle_new_auth_user();

-- Prevent non-admin users from escalating privileges or altering ban/telegram status directly
CREATE OR REPLACE FUNCTION public.prevent_profile_privilege_escalation()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF current_setting('role', true) = 'service_role' OR public.is_admin() THEN
        RETURN NEW;
    END IF;

    IF NEW.role IS DISTINCT FROM OLD.role
       OR NEW.account_status IS DISTINCT FROM OLD.account_status
       OR NEW.is_banned IS DISTINCT FROM OLD.is_banned
       OR NEW.ban_reason IS DISTINCT FROM OLD.ban_reason
       OR NEW.banned_at IS DISTINCT FROM OLD.banned_at
       OR NEW.banned_by IS DISTINCT FROM OLD.banned_by
       OR NEW.telegram_verified IS DISTINCT FROM OLD.telegram_verified
       OR NEW.telegram_verified_at IS DISTINCT FROM OLD.telegram_verified_at THEN
        RAISE EXCEPTION 'Forbidden: Privileged profile fields cannot be modified by regular users';
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_user_profiles_updated_at ON public.user_profiles;
CREATE TRIGGER trg_user_profiles_updated_at
BEFORE UPDATE ON public.user_profiles
FOR EACH ROW EXECUTE FUNCTION public.handle_updated_at();

DROP TRIGGER IF EXISTS trg_prevent_profile_escalation ON public.user_profiles;
CREATE TRIGGER trg_prevent_profile_escalation
BEFORE UPDATE ON public.user_profiles
FOR EACH ROW EXECUTE FUNCTION public.prevent_profile_privilege_escalation();

CREATE TABLE IF NOT EXISTS public.user_sessions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    device_id TEXT,
    device_model TEXT,
    app_version TEXT,
    ip_address TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_user_sessions_user ON public.user_sessions(user_id, is_active);

CREATE TABLE IF NOT EXISTS public.user_activity_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    action TEXT NOT NULL,
    details JSONB DEFAULT '{}'::jsonb,
    ip_address TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_user_activity_user_time ON public.user_activity_logs(user_id, created_at DESC);

-- ==============================================================================
-- 3. SUBSCRIPTIONS & ACTIVATION CODES
-- ==============================================================================

CREATE TABLE IF NOT EXISTS public.activation_codes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    code VARCHAR(64) UNIQUE NOT NULL,
    description TEXT,
    code_type TEXT NOT NULL DEFAULT 'single_use' CHECK (code_type IN ('single_use', 'multi_use')),
    max_uses INTEGER NOT NULL DEFAULT 1 CHECK (max_uses > 0),
    current_uses INTEGER NOT NULL DEFAULT 0 CHECK (current_uses >= 0),
    duration_days INTEGER NOT NULL DEFAULT 30 CHECK (duration_days > 0),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    expires_at TIMESTAMPTZ,
    created_by UUID REFERENCES auth.users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_activation_codes_code ON public.activation_codes(code);

CREATE TABLE IF NOT EXISTS public.subscriptions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    activation_code_id UUID REFERENCES public.activation_codes(id) ON DELETE SET NULL,
    plan_name TEXT NOT NULL DEFAULT 'PRO',
    status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'paused', 'expired', 'revoked')),
    starts_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ends_at TIMESTAMPTZ NOT NULL,
    paused_at TIMESTAMPTZ,
    notes TEXT,
    created_by UUID REFERENCES auth.users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_subscriptions_user_status ON public.subscriptions(user_id, status, ends_at DESC);

DROP TRIGGER IF EXISTS trg_subscriptions_updated_at ON public.subscriptions;
CREATE TRIGGER trg_subscriptions_updated_at
BEFORE UPDATE ON public.subscriptions
FOR EACH ROW EXECUTE FUNCTION public.handle_updated_at();

CREATE TABLE IF NOT EXISTS public.activation_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    activation_code_id UUID REFERENCES public.activation_codes(id) ON DELETE SET NULL,
    code_snapshot TEXT NOT NULL,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    subscription_id UUID REFERENCES public.subscriptions(id) ON DELETE SET NULL,
    status TEXT NOT NULL CHECK (status IN ('success', 'rejected_expired', 'rejected_used', 'rejected_disabled', 'rejected_banned', 'rejected_duplicate')),
    reason TEXT,
    ip_address TEXT,
    activated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_activation_logs_user ON public.activation_logs(user_id, activated_at DESC);

-- ==============================================================================
-- 4. CENTRAL FILE MANAGER, FILE VERSIONS & GLOBAL FILE UPDATE
-- ==============================================================================

ALTER TABLE public.content_items
    ADD COLUMN IF NOT EXISTS version_code INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS target_relative_path TEXT NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS is_required BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS target_group TEXT NOT NULL DEFAULT 'all' CHECK (target_group IN ('all', 'vip', 'specific_users')),
    ADD COLUMN IF NOT EXISTS assigned_user_ids UUID[] NOT NULL DEFAULT ARRAY[]::uuid[],
    ADD COLUMN IF NOT EXISTS is_permanent BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE IF NOT EXISTS public.file_versions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    content_item_id UUID NOT NULL REFERENCES public.content_items(id) ON DELETE CASCADE,
    version VARCHAR(32) NOT NULL,
    version_code INTEGER NOT NULL,
    storage_path TEXT NOT NULL,
    file_name TEXT NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0),
    sha256 VARCHAR(64) NOT NULL,
    release_notes TEXT,
    is_current BOOLEAN NOT NULL DEFAULT TRUE,
    uploaded_by UUID REFERENCES auth.users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (content_item_id, version_code)
);

CREATE INDEX IF NOT EXISTS idx_file_versions_item ON public.file_versions(content_item_id, version_code DESC);

CREATE TABLE IF NOT EXISTS public.file_change_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    content_item_id UUID REFERENCES public.content_items(id) ON DELETE SET NULL,
    action TEXT NOT NULL CHECK (action IN ('upload', 'replace', 'rename', 'delete', 'publish', 'unpublish', 'assign_group', 'global_update_trigger')),
    old_value JSONB,
    new_value JSONB,
    performed_by UUID REFERENCES auth.users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_file_change_logs_time ON public.file_change_logs(created_at DESC);

-- ==============================================================================
-- 5. SYSTEM STATUS, GLOBAL SHUTDOWN / MAINTENANCE, INSTANT RESUME & TELEGRAM
-- ==============================================================================

ALTER TABLE public.system_status
    ADD COLUMN IF NOT EXISTS server_name TEXT NOT NULL DEFAULT 'ANS SERVER CONTROL',
    ADD COLUMN IF NOT EXISTS maintenance_reason_ar TEXT,
    ADD COLUMN IF NOT EXISTS maintenance_reason_en TEXT,
    ADD COLUMN IF NOT EXISTS maintenance_started_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS maintenance_ends_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS maintenance_duration_minutes INTEGER,
    ADD COLUMN IF NOT EXISTS min_update_interval_minutes INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS global_file_revision INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS last_file_update_at TIMESTAMPTZ DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS instant_resumed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS instant_resumed_by UUID REFERENCES auth.users(id),
    ADD COLUMN IF NOT EXISTS telegram_channel_url TEXT NOT NULL DEFAULT 'https://t.me/Kharbash_anas',
    ADD COLUMN IF NOT EXISTS telegram_channel_username TEXT NOT NULL DEFAULT '@Kharbash_anas',
    ADD COLUMN IF NOT EXISTS require_telegram_membership BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS require_active_subscription BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE IF NOT EXISTS public.maintenance_schedules (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    title TEXT NOT NULL,
    reason_ar TEXT,
    reason_en TEXT,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    status TEXT NOT NULL DEFAULT 'scheduled' CHECK (status IN ('scheduled', 'active', 'completed', 'cancelled', 'resumed_early')),
    created_by UUID REFERENCES auth.users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (ends_at > starts_at)
);

CREATE INDEX IF NOT EXISTS idx_maintenance_schedules_window ON public.maintenance_schedules(starts_at, ends_at, status);

-- Server-truth maintenance check (auto-expires finished manual windows and honors active scheduled windows)
CREATE OR REPLACE FUNCTION public.is_maintenance_active()
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1
        FROM public.system_status s
        WHERE s.id = '00000000-0000-0000-0000-000000000001'::uuid
          AND s.maintenance_mode = TRUE
          AND (s.maintenance_ends_at IS NULL OR s.maintenance_ends_at > NOW())
    )
    OR EXISTS (
        SELECT 1
        FROM public.maintenance_schedules m
        WHERE m.status IN ('scheduled', 'active')
          AND m.starts_at <= NOW()
          AND m.ends_at > NOW()
    );
$$;

-- ==============================================================================
-- 6. SEPARATE ANDROID APP UPDATE SYSTEM (APK RELEASES)
-- ==============================================================================

CREATE TABLE IF NOT EXISTS public.app_updates (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    version_code INTEGER NOT NULL CHECK (version_code > 0),
    version_name VARCHAR(32) NOT NULL,
    changelog_ar TEXT NOT NULL,
    changelog_en TEXT NOT NULL,
    apk_storage_path TEXT,
    download_url TEXT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    size_bytes BIGINT NOT NULL DEFAULT 0,
    is_mandatory BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID REFERENCES auth.users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_app_updates_active ON public.app_updates(is_active, version_code DESC);

-- Enforce strictly private bucket for server content files
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
    'worm-content-files',
    'worm-content-files',
    false,
    52428800,
    ARRAY['application/octet-stream', 'application/x-zip-compressed', 'text/plain', 'application/json']
)
ON CONFLICT (id) DO UPDATE SET
    public = false,
    file_size_limit = 52428800;

-- Create private bucket for APK releases
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
    'worm-app-releases',
    'worm-app-releases',
    false,
    157286400, -- 150 MB max APK
    ARRAY['application/vnd.android.package-archive', 'application/octet-stream']
)
ON CONFLICT (id) DO UPDATE SET
    public = false,
    file_size_limit = 157286400;

-- ==============================================================================
-- 7. SECURITY EVENTS, RATE LIMITING & SERVER SETTINGS
-- ==============================================================================

CREATE TABLE IF NOT EXISTS public.security_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    event_type TEXT NOT NULL,
    severity TEXT NOT NULL DEFAULT 'medium' CHECK (severity IN ('low', 'medium', 'high', 'critical')),
    user_id UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    ip_address TEXT,
    endpoint TEXT,
    description TEXT NOT NULL,
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_security_events_time ON public.security_events(created_at DESC);

CREATE TABLE IF NOT EXISTS public.rate_limits (
    bucket_key TEXT PRIMARY KEY,
    window_start TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    request_count INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS public.server_settings (
    key TEXT PRIMARY KEY,
    value JSONB NOT NULL,
    description TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by UUID REFERENCES auth.users(id)
);

-- ==============================================================================
-- 8. ROW LEVEL SECURITY (RLS) ON ALL SENSITIVE TABLES
-- ==============================================================================

ALTER TABLE public.user_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_activity_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.activation_codes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.subscriptions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.activation_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.file_versions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.file_change_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.maintenance_schedules ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.app_updates ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.security_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.rate_limits ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.server_settings ENABLE ROW LEVEL SECURITY;

-- user_profiles policies
DROP POLICY IF EXISTS "Users can view own profile" ON public.user_profiles;
DROP POLICY IF EXISTS "Users can update own safe profile fields" ON public.user_profiles;
DROP POLICY IF EXISTS "Admins can view all profiles" ON public.user_profiles;
DROP POLICY IF EXISTS "Admins can update all profiles" ON public.user_profiles;

CREATE POLICY "Users can view own profile"
ON public.user_profiles FOR SELECT
TO authenticated
USING (auth.uid() = id);

CREATE POLICY "Users can update own safe profile fields"
ON public.user_profiles FOR UPDATE
TO authenticated
USING (auth.uid() = id)
WITH CHECK (auth.uid() = id);

CREATE POLICY "Admins can view all profiles"
ON public.user_profiles FOR SELECT
TO authenticated
USING (public.is_admin());

CREATE POLICY "Admins can update all profiles"
ON public.user_profiles FOR UPDATE
TO authenticated
USING (public.is_admin())
WITH CHECK (public.is_admin());

-- user_sessions policies
DROP POLICY IF EXISTS "Users can view own sessions" ON public.user_sessions;
DROP POLICY IF EXISTS "Users can insert own sessions" ON public.user_sessions;
DROP POLICY IF EXISTS "Admins can manage all sessions" ON public.user_sessions;

CREATE POLICY "Users can view own sessions"
ON public.user_sessions FOR SELECT
TO authenticated
USING (auth.uid() = user_id);

CREATE POLICY "Users can insert own sessions"
ON public.user_sessions FOR INSERT
TO authenticated
WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Admins can manage all sessions"
ON public.user_sessions FOR ALL
TO authenticated
USING (public.is_admin())
WITH CHECK (public.is_admin());

-- user_activity_logs policies
DROP POLICY IF EXISTS "Users view own activity" ON public.user_activity_logs;
DROP POLICY IF EXISTS "Admins view all activity" ON public.user_activity_logs;

CREATE POLICY "Users view own activity"
ON public.user_activity_logs FOR SELECT
TO authenticated
USING (auth.uid() = user_id);

CREATE POLICY "Admins view all activity"
ON public.user_activity_logs FOR SELECT
TO authenticated
USING (public.is_admin());

-- activation_codes policies (Admin only - users NEVER list codes)
DROP POLICY IF EXISTS "Admins manage activation codes" ON public.activation_codes;
CREATE POLICY "Admins manage activation codes"
ON public.activation_codes FOR ALL
TO authenticated
USING (public.is_admin())
WITH CHECK (public.is_admin());

-- subscriptions policies
DROP POLICY IF EXISTS "Users view own subscriptions" ON public.subscriptions;
DROP POLICY IF EXISTS "Admins manage all subscriptions" ON public.subscriptions;

CREATE POLICY "Users view own subscriptions"
ON public.subscriptions FOR SELECT
TO authenticated
USING (auth.uid() = user_id);

CREATE POLICY "Admins manage all subscriptions"
ON public.subscriptions FOR ALL
TO authenticated
USING (public.is_admin())
WITH CHECK (public.is_admin());

-- activation_logs policies
DROP POLICY IF EXISTS "Users view own activation logs" ON public.activation_logs;
DROP POLICY IF EXISTS "Admins view all activation logs" ON public.activation_logs;

CREATE POLICY "Users view own activation logs"
ON public.activation_logs FOR SELECT
TO authenticated
USING (auth.uid() = user_id);

CREATE POLICY "Admins view all activation logs"
ON public.activation_logs FOR SELECT
TO authenticated
USING (public.is_admin());

-- file_versions policies
DROP POLICY IF EXISTS "Authenticated view published file versions" ON public.file_versions;
DROP POLICY IF EXISTS "Admins manage file versions" ON public.file_versions;

CREATE POLICY "Authenticated view published file versions"
ON public.file_versions FOR SELECT
TO authenticated
USING (
    EXISTS (
        SELECT 1 FROM public.content_items c
        WHERE c.id = file_versions.content_item_id
          AND c.is_published = true AND c.is_draft = false
    )
);

CREATE POLICY "Admins manage file versions"
ON public.file_versions FOR ALL
TO authenticated
USING (public.is_admin())
WITH CHECK (public.is_admin());

-- file_change_logs policies (Admin read/insert, immutable)
DROP POLICY IF EXISTS "Admins view file change logs" ON public.file_change_logs;
DROP POLICY IF EXISTS "Admins insert file change logs" ON public.file_change_logs;

CREATE POLICY "Admins view file change logs"
ON public.file_change_logs FOR SELECT
TO authenticated
USING (public.is_admin());

CREATE POLICY "Admins insert file change logs"
ON public.file_change_logs FOR INSERT
TO authenticated
WITH CHECK (public.is_admin());

-- maintenance_schedules policies
DROP POLICY IF EXISTS "Public view active maintenance schedules" ON public.maintenance_schedules;
DROP POLICY IF EXISTS "Admins manage maintenance schedules" ON public.maintenance_schedules;

CREATE POLICY "Public view active maintenance schedules"
ON public.maintenance_schedules FOR SELECT
TO anon, authenticated
USING (status IN ('scheduled', 'active'));

CREATE POLICY "Admins manage maintenance schedules"
ON public.maintenance_schedules FOR ALL
TO authenticated
USING (public.is_admin())
WITH CHECK (public.is_admin());

-- app_updates policies
DROP POLICY IF EXISTS "Public view active app updates" ON public.app_updates;
DROP POLICY IF EXISTS "Admins manage app updates" ON public.app_updates;

CREATE POLICY "Public view active app updates"
ON public.app_updates FOR SELECT
TO anon, authenticated
USING (is_active = true);

CREATE POLICY "Admins manage app updates"
ON public.app_updates FOR ALL
TO authenticated
USING (public.is_admin())
WITH CHECK (public.is_admin());

-- security_events policies (Admin view only; writes via service_role / Edge Functions)
DROP POLICY IF EXISTS "Admins view security events" ON public.security_events;
CREATE POLICY "Admins view security events"
ON public.security_events FOR SELECT
TO authenticated
USING (public.is_admin());

-- rate_limits policies (Strictly internal to service_role; zero public access)
DROP POLICY IF EXISTS "Deny all rate_limits" ON public.rate_limits;
CREATE POLICY "Deny all rate_limits"
ON public.rate_limits FOR SELECT
TO authenticated
USING (false);

-- server_settings policies (Admin only)
DROP POLICY IF EXISTS "Admins manage server settings" ON public.server_settings;
CREATE POLICY "Admins manage server settings"
ON public.server_settings FOR ALL
TO authenticated
USING (public.is_admin())
WITH CHECK (public.is_admin());

-- Ensure storage policies for private worm-content-files bucket
DROP POLICY IF EXISTS "Public read for worm content files" ON storage.objects;
DROP POLICY IF EXISTS "Admin upload for worm content files" ON storage.objects;
DROP POLICY IF EXISTS "Admin update for worm content files" ON storage.objects;
DROP POLICY IF EXISTS "Admin delete for worm content files" ON storage.objects;
DROP POLICY IF EXISTS "Admins can read worm content files" ON storage.objects;
DROP POLICY IF EXISTS "Admins can upload worm content files" ON storage.objects;
DROP POLICY IF EXISTS "Admins can update worm content files" ON storage.objects;
DROP POLICY IF EXISTS "Admins can delete worm content files" ON storage.objects;

CREATE POLICY "Admins can read worm content files"
ON storage.objects FOR SELECT
TO authenticated
USING (bucket_id = 'worm-content-files' AND public.is_admin());

CREATE POLICY "Admins can upload worm content files"
ON storage.objects FOR INSERT
TO authenticated
WITH CHECK (bucket_id = 'worm-content-files' AND public.is_admin());

CREATE POLICY "Admins can update worm content files"
ON storage.objects FOR UPDATE
TO authenticated
USING (bucket_id = 'worm-content-files' AND public.is_admin())
WITH CHECK (bucket_id = 'worm-content-files' AND public.is_admin());

CREATE POLICY "Admins can delete worm content files"
ON storage.objects FOR DELETE
TO authenticated
USING (bucket_id = 'worm-content-files' AND public.is_admin());

-- Storage policies for worm-app-releases
DROP POLICY IF EXISTS "Admins read worm app releases" ON storage.objects;
DROP POLICY IF EXISTS "Admins upload worm app releases" ON storage.objects;
DROP POLICY IF EXISTS "Admins update worm app releases" ON storage.objects;
DROP POLICY IF EXISTS "Admins delete worm app releases" ON storage.objects;

CREATE POLICY "Admins read worm app releases"
ON storage.objects FOR SELECT
TO authenticated
USING (bucket_id = 'worm-app-releases' AND public.is_admin());

CREATE POLICY "Admins upload worm app releases"
ON storage.objects FOR INSERT
TO authenticated
WITH CHECK (bucket_id = 'worm-app-releases' AND public.is_admin());

CREATE POLICY "Admins update worm app releases"
ON storage.objects FOR UPDATE
TO authenticated
USING (bucket_id = 'worm-app-releases' AND public.is_admin())
WITH CHECK (bucket_id = 'worm-app-releases' AND public.is_admin());

CREATE POLICY "Admins delete worm app releases"
ON storage.objects FOR DELETE
TO authenticated
USING (bucket_id = 'worm-app-releases' AND public.is_admin());

-- Update content_items SELECT policy to support group/user assignments
DROP POLICY IF EXISTS "Public can view published content items" ON public.content_items;
CREATE POLICY "Public can view published content items"
ON public.content_items FOR SELECT
TO anon, authenticated
USING (
    is_published = true
    AND is_draft = false
    AND (
        target_group = 'all'
        OR (auth.uid() IS NOT NULL AND auth.uid() = ANY(assigned_user_ids))
        OR (
            target_group = 'vip'
            AND auth.uid() IS NOT NULL
            AND EXISTS (
                SELECT 1 FROM public.subscriptions s
                WHERE s.user_id = auth.uid()
                  AND s.status = 'active'
                  AND s.ends_at > NOW()
            )
        )
    )
);

COMMIT;
