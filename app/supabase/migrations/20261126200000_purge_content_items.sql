-- One-time purge requested by the owner: remove every hosted content item record.
-- (file_versions rows cascade; file_change_logs keep their rows with content_item_id set to NULL.)
-- Users, activation codes and subscriptions are not touched.
DO $$
DECLARE
  before_items int;
  before_versions int;
  item_list text;
  after_items int;
  after_versions int;
BEGIN
  SELECT count(*) INTO before_items FROM public.content_items;
  SELECT count(*) INTO before_versions FROM public.file_versions;
  SELECT coalesce(string_agg(format('%s|%s|%s|pub=%s', category, title, storage_path, is_published), ' ;; '), 'none')
    INTO item_list FROM public.content_items;

  DELETE FROM public.content_items;

  SELECT count(*) INTO after_items FROM public.content_items;
  SELECT count(*) INTO after_versions FROM public.file_versions;
  RAISE NOTICE 'PURGE before: items=% versions=% | list=%', before_items, before_versions, item_list;
  RAISE NOTICE 'PURGE after: items=% versions=%', after_items, after_versions;
END $$;
