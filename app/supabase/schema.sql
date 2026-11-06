-- ==============================================================================
-- WORM TEMP COPIER - HARDENED CENTRAL SERVER DATABASE SCHEMA (PostgreSQL / Supabase)
-- ==============================================================================
-- Version: 3.1.0 (Security Hardened)
-- Security: Zero-Trust Row Level Security (RLS) with app_metadata Role Verification
-- Storage: Bucket 'worm-content-files' (STRICTLY PRIVATE - Signed URLs Only)
-- ==============================================================================

-- 1. EXTENSIONS
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ==============================================================================
-- 2. SECURITY DEFINER HELPER FUNCTIONS (Anti-Privilege Escalation)
-- ==============================================================================

-- CRITICAL SECURITY FUNCTION:
-- Verifies whether the requesting user is an administrator via `app_metadata`.
-- Notice: `app_metadata` can ONLY be modified by the service_role (backend/dashboard),
-- unlike `user_metadata` which can be manipulated client-side by ordinary users.
CREATE OR REPLACE FUNCTION public.is_admin()
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT coalesce(
    (auth.jwt() -> 'app_metadata' ->> 'role') = 'admin'
    OR (auth.jwt() -> 'app_metadata' ->> 'is_admin')::boolean = true,
    false
  );
$$;

-- Trigger function for automatic updated_at timestamps
CREATE OR REPLACE FUNCTION public.handle_updated_at()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = NOW();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ==============================================================================
-- 3. TABLES DEFINITION
-- ==============================================================================

-- 3.1 SYSTEM STATUS & REMOTE MAINTENANCE TABLE
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

-- Ensure singleton row exists safely
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

-- 3.2 CONTENT ITEMS TABLE (Skins, Aim Settings, Legitimate Customizations)
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
    download_url TEXT, -- Nullable: Private storage relies on storage_path + temporary Signed URLs
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
CREATE INDEX IF NOT EXISTS idx_content_items_storage_path ON public.content_items(storage_path);

-- 3.3 RELEASE HISTORY TABLE (App Releases & Changelogs)
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

-- 3.4 AUDIT LOGS TABLE (Tamper-Resistant Administrative Logs)
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

-- Automatic timestamp triggers
DROP TRIGGER IF EXISTS trg_system_status_updated_at ON public.system_status;
CREATE TRIGGER trg_system_status_updated_at
BEFORE UPDATE ON public.system_status
FOR EACH ROW EXECUTE FUNCTION public.handle_updated_at();

DROP TRIGGER IF EXISTS trg_content_items_updated_at ON public.content_items;
CREATE TRIGGER trg_content_items_updated_at
BEFORE UPDATE ON public.content_items
FOR EACH ROW EXECUTE FUNCTION public.handle_updated_at();

-- ==============================================================================
-- 4. ROW LEVEL SECURITY (RLS) POLICIES - STRICT HARDENING
-- ==============================================================================

-- Enable RLS on all tables
ALTER TABLE public.system_status ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.content_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.release_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.audit_logs ENABLE ROW LEVEL SECURITY;

-- 4.1 SYSTEM STATUS POLICIES
DROP POLICY IF EXISTS "Public can view system status" ON public.system_status;
DROP POLICY IF EXISTS "Admins can update system status" ON public.system_status;
DROP POLICY IF EXISTS "Admins can manage system status" ON public.system_status;

-- Anyone (public/anon + authenticated) can view operational status and announcements
CREATE POLICY "Public can view system status"
ON public.system_status FOR SELECT
TO anon, authenticated
USING (true);

-- Only verified Admins (via app_metadata) can update system status
CREATE POLICY "Admins can update system status"
ON public.system_status FOR UPDATE
TO authenticated
USING (public.is_admin())
WITH CHECK (public.is_admin());

-- 4.2 CONTENT ITEMS POLICIES
DROP POLICY IF EXISTS "Public can view published content items" ON public.content_items;
DROP POLICY IF EXISTS "Admins can view all content items" ON public.content_items;
DROP POLICY IF EXISTS "Admins can manage content items" ON public.content_items;
DROP POLICY IF EXISTS "Admins can update content items" ON public.content_items;
DROP POLICY IF EXISTS "Admins can delete content items" ON public.content_items;

-- Public/regular users can ONLY read published items that are not in draft status
CREATE POLICY "Public can view published content items"
ON public.content_items FOR SELECT
TO anon, authenticated
USING (is_published = true AND is_draft = false);

-- Admins can view all items (including drafts and unpublished items)
CREATE POLICY "Admins can view all content items"
ON public.content_items FOR SELECT
TO authenticated
USING (public.is_admin());

-- Only Admins can insert new content items
CREATE POLICY "Admins can insert content items"
ON public.content_items FOR INSERT
TO authenticated
WITH CHECK (public.is_admin());

-- Only Admins can update content items
CREATE POLICY "Admins can update content items"
ON public.content_items FOR UPDATE
TO authenticated
USING (public.is_admin())
WITH CHECK (public.is_admin());

-- Only Admins can delete content items
CREATE POLICY "Admins can delete content items"
ON public.content_items FOR DELETE
TO authenticated
USING (public.is_admin());

-- 4.3 RELEASE HISTORY POLICIES
DROP POLICY IF EXISTS "Public can view release history" ON public.release_history;
DROP POLICY IF EXISTS "Admins can manage release history" ON public.release_history;

CREATE POLICY "Public can view release history"
ON public.release_history FOR SELECT
TO anon, authenticated
USING (true);

CREATE POLICY "Admins can insert release history"
ON public.release_history FOR INSERT
TO authenticated
WITH CHECK (public.is_admin());

CREATE POLICY "Admins can update release history"
ON public.release_history FOR UPDATE
TO authenticated
USING (public.is_admin())
WITH CHECK (public.is_admin());

CREATE POLICY "Admins can delete release history"
ON public.release_history FOR DELETE
TO authenticated
USING (public.is_admin());

-- 4.4 AUDIT LOGS POLICIES (Immutable Log Protection)
DROP POLICY IF EXISTS "Admins can view audit logs" ON public.audit_logs;
DROP POLICY IF EXISTS "Admins can insert audit logs" ON public.audit_logs;

-- Only Admins can view audit logs
CREATE POLICY "Admins can view audit logs"
ON public.audit_logs FOR SELECT
TO authenticated
USING (public.is_admin());

-- Admins can insert audit trail entries
CREATE POLICY "Admins can insert audit logs"
ON public.audit_logs FOR INSERT
TO authenticated
WITH CHECK (public.is_admin());

-- Notice: NO UPDATE or DELETE policies are granted on audit_logs.
-- Audit logs are strictly immutable and cannot be tampered with even by compromised admin accounts.

-- ==============================================================================
-- 5. SECURE STORAGE CONFIGURATION (worm-content-files Bucket)
-- ==============================================================================

-- Create / Update bucket as STRICTLY PRIVATE
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
    'worm-content-files',
    'worm-content-files',
    false, -- STRICTLY PRIVATE: Public URLs disabled; access exclusively via temporary Signed URLs
    52428800, -- 50 MB max per file
    ARRAY['application/octet-stream', 'application/x-zip-compressed', 'text/plain', 'application/json']
)
ON CONFLICT (id) DO UPDATE SET
    public = false,
    file_size_limit = 52428800;

-- Drop insecure legacy storage policies
DROP POLICY IF EXISTS "Public read for worm content files" ON storage.objects;
DROP POLICY IF EXISTS "Admin upload for worm content files" ON storage.objects;
DROP POLICY IF EXISTS "Admin update for worm content files" ON storage.objects;
DROP POLICY IF EXISTS "Admin delete for worm content files" ON storage.objects;
DROP POLICY IF EXISTS "Admins can upload worm content files" ON storage.objects;
DROP POLICY IF EXISTS "Admins can read worm content files" ON storage.objects;
DROP POLICY IF EXISTS "Admins can update worm content files" ON storage.objects;
DROP POLICY IF EXISTS "Admins can delete worm content files" ON storage.objects;

-- STORAGE POLICIES:
-- 1. Direct object reads in the storage table are restricted to Admins.
-- Regular client applications access files through temporary Signed URLs generated server-side.
CREATE POLICY "Admins can read worm content files"
ON storage.objects FOR SELECT
TO authenticated
USING (bucket_id = 'worm-content-files' AND public.is_admin());

-- 2. Only authenticated admins can upload files
CREATE POLICY "Admins can upload worm content files"
ON storage.objects FOR INSERT
TO authenticated
WITH CHECK (bucket_id = 'worm-content-files' AND public.is_admin());

-- 3. Only authenticated admins can modify files
CREATE POLICY "Admins can update worm content files"
ON storage.objects FOR UPDATE
TO authenticated
USING (bucket_id = 'worm-content-files' AND public.is_admin())
WITH CHECK (bucket_id = 'worm-content-files' AND public.is_admin());

-- 4. Only authenticated admins can delete files
CREATE POLICY "Admins can delete worm content files"
ON storage.objects FOR DELETE
TO authenticated
USING (bucket_id = 'worm-content-files' AND public.is_admin());

-- ==============================================================================
-- 6. RPC HELPER: SECURE DOWNLOAD METADATA RESOLUTION
-- ==============================================================================

-- Resolves download parameters for published items without exposing unpublished drafts.
CREATE OR REPLACE FUNCTION public.get_published_content_meta(p_item_id UUID)
RETURNS TABLE (
    id UUID,
    title VARCHAR(200),
    storage_path TEXT,
    sha256 VARCHAR(64),
    target_filename VARCHAR(255),
    size_bytes BIGINT,
    version VARCHAR(32)
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT id, title, storage_path, sha256, target_filename, size_bytes, version
    FROM public.content_items
    WHERE id = p_item_id
      AND is_published = true
      AND is_draft = false;
$$;
