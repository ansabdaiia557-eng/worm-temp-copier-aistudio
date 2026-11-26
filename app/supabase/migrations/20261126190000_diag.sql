DO $$
BEGIN
  RAISE EXCEPTION 'DIAG users=% profiles=% codes=% code_days=% code_active=%', (SELECT count(*) FROM auth.users), (SELECT count(*) FROM public.user_profiles), (SELECT count(*) FROM public.activation_codes), (SELECT string_agg(code||'/'||duration_days||'/'||max_uses,',') FROM public.activation_codes), (SELECT string_agg(is_active::text,',') FROM public.activation_codes);
END $$;
