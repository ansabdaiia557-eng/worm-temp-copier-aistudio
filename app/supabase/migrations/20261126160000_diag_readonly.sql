DO $$
DECLARE msg text;
BEGIN
  SELECT format('DIAG users=%s codes=%s profiles=%s | roles=%s | owner_email_in_auth=%s owner_meta_role=%s owner_profile_role=%s tg_profile=%s',
    (SELECT count(*) FROM auth.users),
    (SELECT count(*) FROM public.activation_codes),
    (SELECT count(*) FROM public.user_profiles),
    (SELECT coalesce(string_agg(role||':'||c,','),'none') FROM (SELECT role, count(*) c FROM public.user_profiles GROUP BY role) x),
    (SELECT count(*) FROM auth.users WHERE lower(email)='ansabdaiia557@gmail.com'),
    (SELECT coalesce(string_agg(coalesce(raw_app_meta_data->>'role','null'),','),'-') FROM auth.users WHERE lower(email)='ansabdaiia557@gmail.com'),
    (SELECT coalesce(string_agg(role,','),'-') FROM public.user_profiles WHERE lower(email)='ansabdaiia557@gmail.com'),
    (SELECT count(*) FROM public.user_profiles WHERE telegram_user_id=8031381457)) INTO msg;
  RAISE EXCEPTION '%', msg;
END $$;
