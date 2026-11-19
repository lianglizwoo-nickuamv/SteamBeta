-- V8: Biblioteca de Recursos Académicos Colaborativa
-- SteamBeta Campus: apuntes, diapositivas, guías y enlaces con verificación estricta

CREATE TABLE IF NOT EXISTS replica.community_resource (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    community_id uuid NOT NULL REFERENCES replica.community(id) ON DELETE RESTRICT,
    uploader_account_id uuid NOT NULL REFERENCES replica.access_account(id) ON DELETE RESTRICT,
    title text NOT NULL CHECK (char_length(btrim(title)) BETWEEN 3 AND 160),
    description text CHECK (description IS NULL OR char_length(description) <= 2000),
    resource_type text NOT NULL CHECK (resource_type IN ('DOCUMENT', 'PDF', 'SLIDES', 'LAB_GUIDE', 'EXTERNAL_LINK')),
    external_url text CHECK (external_url IS NULL OR external_url ~ '^https?://[^\s/$.?#].[^\s]*$'),
    file_storage_name text,
    original_filename text,
    file_size_bytes bigint CHECK (file_size_bytes IS NULL OR file_size_bytes BETWEEN 1 AND 26214400), -- Máx 25 MB
    mime_type text,
    subject_name text CHECK (subject_name IS NULL OR char_length(btrim(subject_name)) BETWEEN 2 AND 80),
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX IF NOT EXISTS resource_by_community ON replica.community_resource (community_id, created_at DESC) WHERE active;
CREATE INDEX IF NOT EXISTS resource_by_subject ON replica.community_resource (community_id, subject_name) WHERE active AND subject_name IS NOT NULL;

ALTER TABLE replica.community_resource ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON replica.community_resource FROM PUBLIC, replica_app, replica_academic;

-- Publicar recurso académico
CREATE OR REPLACE FUNCTION replica.publish_community_resource(
    p_actor uuid,
    p_community uuid,
    p_title text,
    p_description text,
    p_type text,
    p_external_url text,
    p_file_storage_name text,
    p_original_filename text,
    p_file_size_bytes bigint,
    p_mime_type text,
    p_subject_name text
)
RETURNS TABLE (
    id uuid,
    community_id uuid,
    uploader_alias text,
    title text,
    description text,
    resource_type text,
    external_url text,
    original_filename text,
    file_size_bytes bigint,
    mime_type text,
    subject_name text,
    created_at timestamptz
)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
DECLARE
    new_res replica.community_resource%ROWTYPE;
    clean_title text;
    clean_desc text;
    clean_type text;
    clean_url text;
    clean_subj text;
BEGIN
    PERFORM replica.require_community_member(p_actor, p_community);

    clean_title := btrim(p_title);
    clean_desc := NULLIF(btrim(p_description), '');
    clean_type := upper(btrim(p_type));
    clean_url := NULLIF(btrim(p_external_url), '');
    clean_subj := NULLIF(btrim(p_subject_name), '');

    IF char_length(clean_title) NOT BETWEEN 3 AND 160 THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_title';
    END IF;

    IF clean_type NOT IN ('DOCUMENT', 'PDF', 'SLIDES', 'LAB_GUIDE', 'EXTERNAL_LINK') THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_resource_type';
    END IF;

    IF clean_type = 'EXTERNAL_LINK' AND (clean_url IS NULL OR clean_url !~ '^https?://[^\s/$.?#].[^\s]*$') THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_url';
    END IF;

    INSERT INTO replica.community_resource (
        community_id, uploader_account_id, title, description, resource_type,
        external_url, file_storage_name, original_filename, file_size_bytes,
        mime_type, subject_name
    )
    VALUES (
        p_community, p_actor, clean_title, clean_desc, clean_type,
        clean_url, p_file_storage_name, p_original_filename, p_file_size_bytes,
        p_mime_type, clean_subj
    )
    RETURNING * INTO new_res;

    RETURN QUERY
    SELECT new_res.id, new_res.community_id, a.alias, new_res.title, new_res.description,
           new_res.resource_type, new_res.external_url, new_res.original_filename,
           new_res.file_size_bytes, new_res.mime_type, new_res.subject_name, new_res.created_at
    FROM replica.account_profile a WHERE a.account_id = p_actor;
END;
$$;

-- Listar recursos de la comunidad
CREATE OR REPLACE FUNCTION replica.community_resources(
    p_actor uuid,
    p_community uuid,
    p_type_filter text,
    p_subject_filter text,
    p_search text
)
RETURNS TABLE (
    id uuid,
    community_id uuid,
    uploader_account_id uuid,
    uploader_alias text,
    title text,
    description text,
    resource_type text,
    external_url text,
    original_filename text,
    file_size_bytes bigint,
    mime_type text,
    subject_name text,
    created_at timestamptz
)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    PERFORM replica.require_community_member(p_actor, p_community);

    RETURN QUERY
    SELECT r.id, r.community_id, r.uploader_account_id, a.alias,
           r.title, r.description, r.resource_type, r.external_url,
           r.original_filename, r.file_size_bytes, r.mime_type,
           r.subject_name, r.created_at
    FROM replica.community_resource r
    JOIN replica.account_profile a ON a.account_id = r.uploader_account_id
    WHERE r.community_id = p_community AND r.active
      AND (p_type_filter IS NULL OR p_type_filter = 'ALL' OR r.resource_type = upper(btrim(p_type_filter)))
      AND (p_subject_filter IS NULL OR p_subject_filter = '' OR lower(r.subject_name) = lower(btrim(p_subject_filter)))
      AND (p_search IS NULL OR p_search = ''
           OR r.title ILIKE '%' || btrim(p_search) || '%'
           OR r.description ILIKE '%' || btrim(p_search) || '%')
    ORDER BY r.created_at DESC;
END;
$$;

-- Obtener descriptor seguro para descarga
CREATE OR REPLACE FUNCTION replica.get_resource_download(
    p_actor uuid,
    p_community uuid,
    p_resource uuid
)
RETURNS TABLE (
    file_storage_name text,
    original_filename text,
    mime_type text,
    file_size_bytes bigint
)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    PERFORM replica.require_community_member(p_actor, p_community);

    RETURN QUERY
    SELECT r.file_storage_name, r.original_filename, r.mime_type, r.file_size_bytes
    FROM replica.community_resource r
    WHERE r.id = p_resource AND r.community_id = p_community AND r.active
      AND r.file_storage_name IS NOT NULL;

    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1404', MESSAGE = 'resource_unavailable';
    END IF;
END;
$$;

REVOKE ALL ON FUNCTION
    replica.publish_community_resource(uuid, uuid, text, text, text, text, text, text, bigint, text, text),
    replica.community_resources(uuid, uuid, text, text, text),
    replica.get_resource_download(uuid, uuid, uuid)
FROM PUBLIC;

GRANT EXECUTE ON FUNCTION
    replica.publish_community_resource(uuid, uuid, text, text, text, text, text, text, bigint, text, text),
    replica.community_resources(uuid, uuid, text, text, text),
    replica.get_resource_download(uuid, uuid, uuid)
TO "${backendLogin}";
