-- Restore dashboard admin after the reset: confirm and promote the owner's admin account.
UPDATE auth.users
SET email_confirmed_at = coalesce(email_confirmed_at, now()),
    raw_app_meta_data = coalesce(raw_app_meta_data,'{}'::jsonb) || '{"role":"admin"}'::jsonb
WHERE lower(email) = 'ansabdaiia557@gmail.com';
UPDATE public.user_profiles SET role = 'admin' WHERE lower(email) = 'ansabdaiia557@gmail.com';
