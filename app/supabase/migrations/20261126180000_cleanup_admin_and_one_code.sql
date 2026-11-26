-- Owner chose NOT to restore admin: remove the re-created account, leave zero users.
DELETE FROM auth.users;
-- One 1-day single-use activation code requested by the owner.
INSERT INTO public.activation_codes (code, description, code_type, max_uses, duration_days, is_active)
VALUES ('KHB-KNKML4L9', 'owner 1-day code', 'single_use', 1, 1, true);
