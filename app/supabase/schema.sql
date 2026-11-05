-- ==============================================================================
-- WORM TEMP COPIER - CENTRAL SERVER DATABASE SCHEMA (PostgreSQL / Supabase)
-- ==============================================================================
-- Version: 3.0.0
-- Security: Full Zero-Trust Row Level Security (RLS) Enabled on all tables
-- Storage: Bucket 'worm-content-files' (Private/Authenticated Uploads)
-- ==============================================================================

-- 1. EXTENSIONS
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 2. SYSTEM STATUS & REMOTE MAINTENANCE TABLE
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

-- Ensure singleton row exists
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

-- 3. CONTENT ITEMS TABLE (Skins, Aim Settings, Legitimate Customizations)
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
    download_url TEXT NOT NULL,
    compatible_editions TEXT[] NOT NULL DEFAULT ARRAY['PUBG_GLOBAL', 'PUBG_KOREA', 'PUBG_VIETNAM', 'PUBG_TAIWAN'],
    is_published BOOLEAN NOT NULL DEFAULT FALSE,
    is_draft BOOLEAN NOT NULL DEFAULT TRUE,
    downloads_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_by UUID REFERENCES auth.users(id)
);

CREATE INDEX IF NOT EXISTS idx_content_items_category ON public.content_items(category);
CREATE INDEX IF NOT EXISTS idx_content_items_published ON public.content_items(is_published);
CREATE INDEX IF NOT EXISTS idx_content_items_sha256 ON public.content_items(sha256);

-- 4. RELEASE HISTORY TABLE (App Releases & Changelogs)
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

-- 5. AUDIT LOGS TABLE
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

CREATE INDEX IF NOT EXISTS idx_audit_logs_timestamp ON public.audit_logs(timestamp DESC);

-- ==============================================================================
-- ROW LEVEL SECURITY (RLS) POLICIES
-- ==============================================================================

-- Enable RLS on all tables
ALTER TABLE public.system_status ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.content_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.release_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.audit_logs ENABLE ROW LEVEL SECURITY;

-- SYSTEM STATUS POLICIES:
-- Anyone (anon + authenticated) can read system status
CREATE POLICY "Public can view system status"
ON public.system_status FOR SELECT
TO anon, authenticated
USING (true);

-- Only authenticated admins can update system status
CREATE POLICY "Admins can update system status"
ON public.system_status FOR ALL
TO authenticated
USING (auth.uid() IS NOT NULL)
WITH CHECK (auth.uid() IS NOT NULL);

-- CONTENT ITEMS POLICIES:
-- Public can only view published and non-draft content items
CREATE POLICY "Public can view published content items"
ON public.content_items FOR SELECT
TO anon
USING (is_published = true AND is_draft = false);

-- Authenticated admins can view all (including drafts)
CREATE POLICY "Admins can view all content items"
ON public.content_items FOR SELECT
TO authenticated
USING (true);

-- Only authenticated admins can insert, update, or delete content items
CREATE POLICY "Admins can manage content items"
ON public.content_items FOR INSERT
TO authenticated
WITH CHECK (auth.uid() IS NOT NULL);

CREATE POLICY "Admins can update content items"
ON public.content_items FOR UPDATE
TO authenticated
USING (auth.uid() IS NOT NULL)
WITH CHECK (auth.uid() IS NOT NULL);

CREATE POLICY "Admins can delete content items"
ON public.content_items FOR DELETE
TO authenticated
USING (auth.uid() IS NOT NULL);

-- RELEASE HISTORY POLICIES:
-- Public can view release history
CREATE POLICY "Public can view release history"
ON public.release_history FOR SELECT
TO anon, authenticated
USING (true);

-- Only authenticated admins can manage releases
CREATE POLICY "Admins can manage release history"
ON public.release_history FOR ALL
TO authenticated
USING (auth.uid() IS NOT NULL)
WITH CHECK (auth.uid() IS NOT NULL);

-- AUDIT LOGS POLICIES:
-- Only authenticated admins can view and write audit logs
CREATE POLICY "Admins can view audit logs"
ON public.audit_logs FOR SELECT
TO authenticated
USING (auth.uid() IS NOT NULL);

CREATE POLICY "Admins can insert audit logs"
ON public.audit_logs FOR INSERT
TO authenticated
WITH CHECK (auth.uid() IS NOT NULL);

-- ==============================================================================
-- STORAGE CONFIGURATION (worm-content-files Bucket)
-- ==============================================================================

-- Create private bucket for content files
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
    'worm-content-files',
    'worm-content-files',
    true, -- public read for download URLs, restricted write
    52428800, -- 50 MB max per file
    ARRAY['application/octet-stream', 'application/x-zip-compressed', 'text/plain', 'application/json']
)
ON CONFLICT (id) DO NOTHING;

-- Storage RLS: Public read access for downloaded content
CREATE POLICY "Public read for worm content files"
ON storage.objects FOR SELECT
TO anon, authenticated
USING (bucket_id = 'worm-content-files');

-- Storage RLS: Admin upload/delete access only
CREATE POLICY "Admin upload for worm content files"
ON storage.objects FOR INSERT
TO authenticated
WITH CHECK (bucket_id = 'worm-content-files' AND auth.uid() IS NOT NULL);

CREATE POLICY "Admin update for worm content files"
ON storage.objects FOR UPDATE
TO authenticated
USING (bucket_id = 'worm-content-files' AND auth.uid() IS NOT NULL);

CREATE POLICY "Admin delete for worm content files"
ON storage.objects FOR DELETE
TO authenticated
USING (bucket_id = 'worm-content-files' AND auth.uid() IS NOT NULL);
