-- ==============================================================================
-- MIGRATION: 20261106_security_hardening.sql
-- PURPOSE: Idempotent Zero-Downtime Hardening for Worm Temp Copier
-- TARGET: Supabase PostgreSQL Database
-- SAFE TO RE-RUN (IDEMPOTENT) - PRESERVES EXISTING USER DATA
-- ==============================================================================

BEGIN;

-- 1. Ensure required extensions exist
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 2. CREATE SECURE IS_ADMIN FUNCTION
-- Prevents privilege escalation by inspecting app_metadata (protected from client modification)
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

-- 3. SCHEMA TWEAKS (SAFE ALTERATIONS)
-- Allow download_url to be optional/nullable so system relies on storage_path and signed URLs
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = 'public' 
          AND table_name = 'content_items' 
          AND column_name = 'download_url' 
          AND is_nullable = 'NO'
    ) THEN
        ALTER TABLE public.content_items ALTER COLUMN download_url DROP NOT NULL;
    END IF;
END $$;

-- Ensure storage_path index exists for fast lookup
CREATE INDEX IF NOT EXISTS idx_content_items_storage_path ON public.content_items(storage_path);

-- 4. HARDEN RLS POLICIES ON SYSTEM STATUS
ALTER TABLE public.system_status ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Public can view system status" ON public.system_status;
DROP POLICY IF EXISTS "Admins can update system status" ON public.system_status;
DROP POLICY IF EXISTS "Admins can manage system status" ON public.system_status;

CREATE POLICY "Public can view system status"
ON public.system_status FOR SELECT
TO anon, authenticated
USING (true);

CREATE POLICY "Admins can update system status"
ON public.system_status FOR UPDATE
TO authenticated
USING (public.is_admin())
WITH CHECK (public.is_admin());

-- 5. HARDEN RLS POLICIES ON CONTENT ITEMS
ALTER TABLE public.content_items ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Public can view published content items" ON public.content_items;
DROP POLICY IF EXISTS "Admins can view all content items" ON public.content_items;
DROP POLICY IF EXISTS "Admins can manage content items" ON public.content_items;
DROP POLICY IF EXISTS "Admins can update content items" ON public.content_items;
DROP POLICY IF EXISTS "Admins can delete content items" ON public.content_items;
DROP POLICY IF EXISTS "Admins can insert content items" ON public.content_items;

-- Public / normal authenticated users can ONLY read published and non-draft content
CREATE POLICY "Public can view published content items"
ON public.content_items FOR SELECT
TO anon, authenticated
USING (is_published = true AND is_draft = false);

-- Admins can view all content including drafts
CREATE POLICY "Admins can view all content items"
ON public.content_items FOR SELECT
TO authenticated
USING (public.is_admin());

-- Write operations strictly gated behind public.is_admin()
CREATE POLICY "Admins can insert content items"
ON public.content_items FOR INSERT
TO authenticated
WITH CHECK (public.is_admin());

CREATE POLICY "Admins can update content items"
ON public.content_items FOR UPDATE
TO authenticated
USING (public.is_admin())
WITH CHECK (public.is_admin());

CREATE POLICY "Admins can delete content items"
ON public.content_items FOR DELETE
TO authenticated
USING (public.is_admin());

-- 6. HARDEN RLS POLICIES ON RELEASE HISTORY
ALTER TABLE public.release_history ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Public can view release history" ON public.release_history;
DROP POLICY IF EXISTS "Admins can manage release history" ON public.release_history;
DROP POLICY IF EXISTS "Admins can insert release history" ON public.release_history;
DROP POLICY IF EXISTS "Admins can update release history" ON public.release_history;
DROP POLICY IF EXISTS "Admins can delete release history" ON public.release_history;

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

-- 7. HARDEN RLS POLICIES ON AUDIT LOGS
ALTER TABLE public.audit_logs ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Admins can view audit logs" ON public.audit_logs;
DROP POLICY IF EXISTS "Admins can insert audit logs" ON public.audit_logs;

CREATE POLICY "Admins can view audit logs"
ON public.audit_logs FOR SELECT
TO authenticated
USING (public.is_admin());

CREATE POLICY "Admins can insert audit logs"
ON public.audit_logs FOR INSERT
TO authenticated
WITH CHECK (public.is_admin());

-- Notice: audit_logs has NO UPDATE or DELETE policies, ensuring write-once auditability.

-- 8. CONVERT STORAGE BUCKET TO PRIVATE & REMOVE INSECURE POLICIES
UPDATE storage.buckets
SET public = false
WHERE id = 'worm-content-files';

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

-- 9. RPC FUNCTION FOR CONTENT METADATA RESOLUTION
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

COMMIT;
