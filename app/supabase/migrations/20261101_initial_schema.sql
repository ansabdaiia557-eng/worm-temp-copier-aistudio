-- ==============================================================================
-- MIGRATION: 20261101_initial_schema.sql
-- PURPOSE: Baseline tables for Worm Temp Copier so migrations run cleanly
--          on both fresh and existing Supabase projects.
-- ==============================================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE OR REPLACE FUNCTION public.handle_updated_at()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = NOW();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE IF NOT EXISTS public.system_status (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    maintenance_mode BOOLEAN NOT NULL DEFAULT FALSE,
    min_required_version VARCHAR(32) NOT NULL DEFAULT '1.3.0',
    latest_app_version VARCHAR(32) NOT NULL DEFAULT '1.3.1',
    announcement_title_ar TEXT,
    announcement_body_ar TEXT,
    announcement_title_en TEXT,
    announcement_body_en TEXT,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_by UUID REFERENCES auth.users(id)
);

INSERT INTO public.system_status (id, maintenance_mode, min_required_version, latest_app_version, announcement_title_ar, announcement_body_ar, announcement_title_en, announcement_body_en)
SELECT
    '00000000-0000-0000-0000-000000000001'::uuid,
    FALSE,
    '1.3.0',
    '1.3.1',
    'الخادم يعمل بكفاءة',
    'تم تحديث جميع حزم التخصيص لتتوافق مع آخر إصدارات اللعبة.',
    'Systems Operational',
    'All customization packs have been validated for the latest game updates.'
WHERE NOT EXISTS (SELECT 1 FROM public.system_status WHERE id = '00000000-0000-0000-0000-000000000001'::uuid);

CREATE TABLE IF NOT EXISTS public.content_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    slug VARCHAR(120) UNIQUE NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    category VARCHAR(50) NOT NULL CHECK (category IN ('SKINS', 'AIM_SETTINGS', 'ALL_FEATURES')),
    version VARCHAR(32) NOT NULL DEFAULT '1.0.0',
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0),
    target_filename VARCHAR(255) NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    storage_path TEXT NOT NULL,
    download_url TEXT,
    compatible_editions TEXT[] NOT NULL DEFAULT ARRAY['PUBG_GLOBAL', 'PUBG_KOREA', 'PUBG_VIETNAM', 'PUBG_TAIWAN'],
    is_published BOOLEAN NOT NULL DEFAULT FALSE,
    is_draft BOOLEAN NOT NULL DEFAULT TRUE,
    downloads_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_by UUID REFERENCES auth.users(id)
);

CREATE TABLE IF NOT EXISTS public.release_history (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    version VARCHAR(32) NOT NULL,
    build_number INTEGER NOT NULL,
    changelog_ar TEXT NOT NULL,
    changelog_en TEXT NOT NULL,
    download_url TEXT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    is_mandatory BOOLEAN NOT NULL DEFAULT FALSE,
    release_date TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_by UUID REFERENCES auth.users(id)
);

CREATE TABLE IF NOT EXISTS public.audit_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID REFERENCES auth.users(id),
    user_email TEXT,
    action VARCHAR(100) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_id VARCHAR(100),
    details JSONB,
    ip_address TEXT,
    timestamp TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
    'worm-content-files',
    'worm-content-files',
    false,
    52428800,
    ARRAY['application/octet-stream', 'application/x-zip-compressed', 'text/plain', 'application/json']
)
ON CONFLICT (id) DO NOTHING;
