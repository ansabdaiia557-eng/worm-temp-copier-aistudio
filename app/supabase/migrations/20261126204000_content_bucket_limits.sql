UPDATE storage.buckets
SET file_size_limit = 52428800,
    allowed_mime_types = ARRAY['application/octet-stream', 'application/x-zip-compressed', 'text/plain', 'application/json']
WHERE id = 'worm-content-files';
