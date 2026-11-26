DO $$
BEGIN
  RAISE EXCEPTION 'DIAG %', (SELECT string_agg(coalesce(email,'noemail')||' created='||created_at||' confirmed='||coalesce(email_confirmed_at::text,'no')||' meta='||raw_app_meta_data::text,' ; ') FROM auth.users);
END $$;
