-- One-time owner-requested reset: delete all activation codes and all non-admin users.
-- Admin accounts (role admin/super_admin or app_metadata.is_admin) are kept.
DO $$
DECLARE
  n_codes int; n_users_before int; n_users_deleted int; n_admins_kept int;
  victims uuid[];
  r record;
BEGIN
  SELECT count(*) INTO n_codes FROM public.activation_codes;
  SELECT count(*) INTO n_users_before FROM auth.users;

  SELECT coalesce(array_agg(u.id), '{}') INTO victims
  FROM auth.users u
  LEFT JOIN public.user_profiles p ON p.id = u.id
  WHERE coalesce(p.role,'user') NOT IN ('admin','super_admin')
    AND coalesce((u.raw_app_meta_data->>'is_admin')::boolean, false) = false;

  -- detach non-cascading references to victims
  FOR r IN
    SELECT c.conrelid::regclass AS tbl, a.attname AS col
    FROM pg_constraint c
    JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = c.conkey[1]
    WHERE c.contype='f' AND c.confrelid = 'auth.users'::regclass
      AND c.confdeltype = 'a' AND (SELECT relnamespace::regnamespace::text FROM pg_class WHERE oid=c.conrelid)='public'
      AND NOT a.attnotnull
  LOOP
    EXECUTE format('UPDATE %s SET %I = NULL WHERE %I = ANY($1)', r.tbl, r.col, r.col) USING victims;
  END LOOP;

  DELETE FROM public.activation_codes;
  DELETE FROM auth.users WHERE id = ANY(victims);
  GET DIAGNOSTICS n_users_deleted = ROW_COUNT;

  BEGIN DELETE FROM public.referrals; EXCEPTION WHEN undefined_table THEN NULL; END;
  BEGIN DELETE FROM public.telegram_login_codes; EXCEPTION WHEN undefined_table THEN NULL; END;

  SELECT count(*) INTO n_admins_kept FROM auth.users;
  RAISE NOTICE 'RESET_RESULT codes_deleted=% users_before=% users_deleted=% users_remaining=%', n_codes, n_users_before, n_users_deleted, n_admins_kept;
END $$;
