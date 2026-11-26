INSERT INTO storage.buckets (id, name, public)
VALUES ('worm-content-files', 'worm-content-files', false)
ON CONFLICT (id) DO NOTHING;
