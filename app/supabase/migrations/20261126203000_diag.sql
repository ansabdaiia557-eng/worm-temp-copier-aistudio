DO $$
BEGIN
  RAISE EXCEPTION 'DIAG items=% objects=% bucket=%',
    (SELECT count(*) FROM public.content_items),
    (SELECT count(*) FROM storage.objects WHERE bucket_id = 'worm-content-files'),
    (SELECT count(*) FROM storage.buckets WHERE id = 'worm-content-files');
END $$;
