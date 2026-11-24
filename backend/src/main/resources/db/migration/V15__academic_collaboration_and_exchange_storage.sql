-- V15 (CANDIDATA - PENDIENTE DE AUTORIZACIÓN):
-- Persistencia segura RLS para Mi Planificación (tareas privadas), Equipos de Estudio,
-- Intercambio Universitario (con adjuntos administrados) y Oportunidades Campus.
-- SteamBeta Campus (PostgreSQL 18). No modifica V1-V14 ni el módulo de Comunidades.

-- =============================================================================
-- 1. EXTENSIONES DE ESQUEMA NECESARIAS
-- =============================================================================

-- 1.1 Propietario en replica.campus_opportunity para edición/eliminación segura
ALTER TABLE replica.campus_opportunity
    ADD COLUMN IF NOT EXISTS owner_account_id uuid REFERENCES replica.access_account(id) ON DELETE RESTRICT;

CREATE INDEX IF NOT EXISTS opportunity_by_owner
    ON replica.campus_opportunity(owner_account_id, created_at DESC)
    WHERE active;

-- 1.2 Tabla de metadatos persistentes de archivos adjuntos en Intercambio
CREATE TABLE IF NOT EXISTS replica.campus_exchange_attachment (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    exchange_item_id uuid NOT NULL REFERENCES replica.campus_exchange_item(id) ON DELETE CASCADE,
    uploader_account_id uuid NOT NULL REFERENCES replica.access_account(id) ON DELETE RESTRICT,
    storage_key text NOT NULL UNIQUE CHECK (storage_key ~ '^[a-f0-9-]{36}\.[a-z0-9]{2,5}$'),
    original_filename text NOT NULL CHECK (char_length(btrim(original_filename)) BETWEEN 1 AND 255),
    extension text NOT NULL CHECK (extension IN (
        'pdf', 'doc', 'docx', 'ppt', 'pptx',
        'jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp', 'tif', 'tiff'
    )),
    mime_type text NOT NULL CHECK (char_length(btrim(mime_type)) BETWEEN 3 AND 120),
    file_size_bytes bigint NOT NULL CHECK (file_size_bytes BETWEEN 1 AND 26214400),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX IF NOT EXISTS exchange_attachment_by_item
    ON replica.campus_exchange_attachment(exchange_item_id, created_at ASC);

ALTER TABLE replica.campus_exchange_attachment ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON replica.campus_exchange_attachment FROM PUBLIC, replica_app, replica_academic;

-- =============================================================================
-- 2. MI PLANIFICACIÓN: TAREAS PRIVADAS DEL ESTUDIANTE
-- =============================================================================

CREATE OR REPLACE FUNCTION replica.my_academic_tasks_v15(
    p_actor uuid,
    p_status text,
    p_priority text
)
RETURNS TABLE (
    id uuid,
    account_id uuid,
    title text,
    description text,
    status text,
    priority text,
    due_date timestamptz,
    project_name text,
    created_at timestamptz
)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
DECLARE
    clean_status text;
    clean_priority text;
BEGIN
    PERFORM 1 FROM replica.access_account a WHERE a.id = p_actor AND a.enabled FOR SHARE;
    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1401', MESSAGE = 'account_unavailable';
    END IF;

    clean_status := NULLIF(upper(btrim(p_status)), '');
    clean_priority := NULLIF(upper(btrim(p_priority)), '');

    RETURN QUERY
    SELECT t.id, t.account_id, t.title, t.description, t.status, t.priority,
           t.due_date, t.project_name, t.created_at
    FROM replica.academic_task t
    WHERE t.account_id = p_actor
      AND (clean_status IS NULL OR t.status = clean_status)
      AND (clean_priority IS NULL OR t.priority = clean_priority)
    ORDER BY t.due_date ASC NULLS LAST, t.created_at DESC;
END;
$$;

CREATE OR REPLACE FUNCTION replica.create_academic_task_v15(
    p_actor uuid,
    p_title text,
    p_description text,
    p_priority text,
    p_due_date timestamptz,
    p_project_name text
)
RETURNS TABLE (
    id uuid,
    account_id uuid,
    title text,
    description text,
    status text,
    priority text,
    due_date timestamptz,
    project_name text,
    created_at timestamptz
)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
DECLARE
    clean_title text;
    clean_desc text;
    clean_priority text;
    clean_project text;
    saved replica.academic_task%ROWTYPE;
BEGIN
    PERFORM 1 FROM replica.access_account a WHERE a.id = p_actor AND a.enabled FOR SHARE;
    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1401', MESSAGE = 'account_unavailable';
    END IF;

    clean_title := btrim(coalesce(p_title, ''));
    clean_desc := NULLIF(btrim(p_description), '');
    clean_priority := coalesce(NULLIF(upper(btrim(p_priority)), ''), 'MEDIUM');
    clean_project := NULLIF(btrim(p_project_name), '');

    IF char_length(clean_title) NOT BETWEEN 2 AND 160 THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_title';
    END IF;
    IF clean_desc IS NOT NULL AND char_length(clean_desc) > 3000 THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_description';
    END IF;
    IF clean_priority NOT IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT') THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_priority';
    END IF;
    IF clean_project IS NOT NULL AND char_length(clean_project) > 100 THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_project_name';
    END IF;

    INSERT INTO replica.academic_task (
        account_id, community_id, title, description, status, priority, due_date, project_name
    )
    VALUES (
        p_actor, NULL, clean_title, clean_desc, 'PENDING', clean_priority, p_due_date, clean_project
    )
    RETURNING * INTO saved;

    RETURN QUERY
    SELECT saved.id, saved.account_id, saved.title, saved.description, saved.status,
           saved.priority, saved.due_date, saved.project_name, saved.created_at;
END;
$$;

CREATE OR REPLACE FUNCTION replica.update_academic_task_v15(
    p_actor uuid,
    p_task_id uuid,
    p_title text,
    p_description text,
    p_status text,
    p_priority text,
    p_due_date timestamptz,
    p_project_name text,
    p_clear_due_date boolean
)
RETURNS TABLE (
    id uuid,
    account_id uuid,
    title text,
    description text,
    status text,
    priority text,
    due_date timestamptz,
    project_name text,
    created_at timestamptz
)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
DECLARE
    clean_title text;
    clean_desc text;
    clean_status text;
    clean_priority text;
    clean_project text;
    saved replica.academic_task%ROWTYPE;
BEGIN
    PERFORM 1 FROM replica.access_account a WHERE a.id = p_actor AND a.enabled FOR SHARE;
    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1401', MESSAGE = 'account_unavailable';
    END IF;

    clean_title := NULLIF(btrim(p_title), '');
    clean_desc := CASE WHEN p_description IS NULL THEN NULL ELSE NULLIF(btrim(p_description), '') END;
    clean_status := NULLIF(upper(btrim(p_status)), '');
    clean_priority := NULLIF(upper(btrim(p_priority)), '');
    clean_project := CASE WHEN p_project_name IS NULL THEN NULL ELSE NULLIF(btrim(p_project_name), '') END;

    IF clean_title IS NOT NULL AND char_length(clean_title) NOT BETWEEN 2 AND 160 THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_title';
    END IF;
    IF clean_desc IS NOT NULL AND char_length(clean_desc) > 3000 THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_description';
    END IF;
    IF clean_project IS NOT NULL AND char_length(clean_project) > 100 THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_project_name';
    END IF;
    IF clean_status IS NOT NULL AND clean_status NOT IN ('PENDING', 'IN_PROGRESS', 'IN_REVIEW', 'COMPLETED') THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_status';
    END IF;
    IF clean_priority IS NOT NULL AND clean_priority NOT IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT') THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_priority';
    END IF;

    UPDATE replica.academic_task t
    SET title = coalesce(clean_title, t.title),
        description = CASE WHEN p_description IS NULL THEN t.description ELSE clean_desc END,
        status = coalesce(clean_status, t.status),
        priority = coalesce(clean_priority, t.priority),
        due_date = CASE
            WHEN coalesce(p_clear_due_date, false) THEN NULL
            WHEN p_due_date IS NOT NULL THEN p_due_date
            ELSE t.due_date
        END,
        project_name = CASE WHEN p_project_name IS NULL THEN t.project_name ELSE clean_project END,
        updated_at = clock_timestamp()
    WHERE t.id = p_task_id AND t.account_id = p_actor
    RETURNING * INTO saved;

    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1404', MESSAGE = 'task_not_found';
    END IF;

    RETURN QUERY
    SELECT saved.id, saved.account_id, saved.title, saved.description, saved.status,
           saved.priority, saved.due_date, saved.project_name, saved.created_at;
END;
$$;

CREATE OR REPLACE FUNCTION replica.delete_academic_task_v15(
    p_actor uuid,
    p_task_id uuid
)
RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    PERFORM 1 FROM replica.access_account a WHERE a.id = p_actor AND a.enabled FOR SHARE;
    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1401', MESSAGE = 'account_unavailable';
    END IF;

    DELETE FROM replica.academic_task t
    WHERE t.id = p_task_id AND t.account_id = p_actor;

    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1404', MESSAGE = 'task_not_found';
    END IF;
END;
$$;

-- =============================================================================
-- 3. EQUIPOS DE ESTUDIO
-- =============================================================================

CREATE OR REPLACE FUNCTION replica.read_study_profile_v15(p_actor uuid)
RETURNS TABLE (
    account_id uuid,
    alias text,
    declared_university text,
    declared_career text,
    subjects text[],
    interests text[],
    can_help_with text,
    needs_help_with text,
    availability text,
    visible boolean
)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    PERFORM 1 FROM replica.access_account a WHERE a.id = p_actor AND a.enabled FOR SHARE;
    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1401', MESSAGE = 'account_unavailable';
    END IF;

    RETURN QUERY
    SELECT ap.account_id, ap.alias, ap.declared_university, ap.declared_career,
           coalesce(sp.subjects, '{}'::text[]),
           coalesce(sp.interests, '{}'::text[]),
           sp.can_help_with,
           sp.needs_help_with,
           sp.availability,
           coalesce(sp.visible, true)
    FROM replica.account_profile ap
    LEFT JOIN replica.study_profile sp ON sp.account_id = ap.account_id
    WHERE ap.account_id = p_actor;
END;
$$;

CREATE OR REPLACE FUNCTION replica.upsert_study_profile_v15(
    p_actor uuid,
    p_subjects text[],
    p_interests text[],
    p_can_help text,
    p_needs_help text,
    p_availability text,
    p_visible boolean
)
RETURNS TABLE (
    account_id uuid,
    alias text,
    declared_university text,
    declared_career text,
    subjects text[],
    interests text[],
    can_help_with text,
    needs_help_with text,
    availability text,
    visible boolean
)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
DECLARE
    clean_can_help text;
    clean_needs_help text;
    clean_avail text;
BEGIN
    PERFORM 1 FROM replica.access_account a WHERE a.id = p_actor AND a.enabled FOR SHARE;
    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1401', MESSAGE = 'account_unavailable';
    END IF;

    clean_can_help := NULLIF(btrim(p_can_help), '');
    clean_needs_help := NULLIF(btrim(p_needs_help), '');
    clean_avail := NULLIF(btrim(p_availability), '');

    IF clean_can_help IS NOT NULL AND char_length(clean_can_help) > 1000 THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_can_help';
    END IF;
    IF clean_needs_help IS NOT NULL AND char_length(clean_needs_help) > 1000 THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_needs_help';
    END IF;
    IF clean_avail IS NOT NULL AND char_length(clean_avail) > 300 THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_availability';
    END IF;

    INSERT INTO replica.study_profile (
        account_id, subjects, interests, can_help_with, needs_help_with, availability, visible, updated_at
    )
    VALUES (
        p_actor,
        coalesce(p_subjects, '{}'::text[]),
        coalesce(p_interests, '{}'::text[]),
        clean_can_help,
        clean_needs_help,
        clean_avail,
        coalesce(p_visible, true),
        clock_timestamp()
    )
    ON CONFLICT ON CONSTRAINT study_profile_pkey DO UPDATE
        SET subjects = excluded.subjects,
            interests = excluded.interests,
            can_help_with = excluded.can_help_with,
            needs_help_with = excluded.needs_help_with,
            availability = excluded.availability,
            visible = excluded.visible,
            updated_at = clock_timestamp();

    RETURN QUERY
    SELECT * FROM replica.read_study_profile_v15(p_actor);
END;
$$;

CREATE OR REPLACE FUNCTION replica.search_study_matches_v15(
    p_actor uuid,
    p_query text
)
RETURNS TABLE (
    account_id uuid,
    alias text,
    declared_university text,
    declared_career text,
    subjects text[],
    interests text[],
    can_help_with text,
    needs_help_with text,
    availability text,
    visible boolean
)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
DECLARE
    clean_q text;
BEGIN
    PERFORM 1 FROM replica.access_account a WHERE a.id = p_actor AND a.enabled FOR SHARE;
    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1401', MESSAGE = 'account_unavailable';
    END IF;

    clean_q := NULLIF(btrim(p_query), '');

    RETURN QUERY
    SELECT sp.account_id, ap.alias, ap.declared_university, ap.declared_career,
           sp.subjects, sp.interests, sp.can_help_with, sp.needs_help_with,
           sp.availability, sp.visible
    FROM replica.study_profile sp
    JOIN replica.account_profile ap ON ap.account_id = sp.account_id
    JOIN replica.access_account acc ON acc.id = sp.account_id
    WHERE acc.enabled
      AND sp.visible
      AND sp.account_id <> p_actor
      AND (
          clean_q IS NULL
          OR array_to_string(sp.subjects, ' ') ILIKE '%' || clean_q || '%'
          OR array_to_string(sp.interests, ' ') ILIKE '%' || clean_q || '%'
          OR coalesce(sp.availability, '') ILIKE '%' || clean_q || '%'
          OR coalesce(sp.can_help_with, '') ILIKE '%' || clean_q || '%'
          OR coalesce(sp.needs_help_with, '') ILIKE '%' || clean_q || '%'
          OR ap.alias ILIKE '%' || clean_q || '%'
      )
    ORDER BY sp.updated_at DESC
    LIMIT 40;
END;
$$;

-- =============================================================================
-- 4. INTERCAMBIO UNIVERSITARIO Y ARCHIVOS ADJUNTOS
-- =============================================================================

CREATE OR REPLACE FUNCTION replica.list_exchange_items_v15(
    p_actor uuid,
    p_category text,
    p_mine_only boolean
)
RETURNS TABLE (
    id uuid,
    owner_account_id uuid,
    owner_alias text,
    community_id uuid,
    title text,
    description text,
    category text,
    exchange_type text,
    status text,
    contact_info text,
    created_at timestamptz,
    attachment_count bigint
)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
DECLARE
    clean_cat text;
BEGIN
    PERFORM 1 FROM replica.access_account a WHERE a.id = p_actor AND a.enabled FOR SHARE;
    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1401', MESSAGE = 'account_unavailable';
    END IF;

    clean_cat := NULLIF(upper(btrim(p_category)), '');

    RETURN QUERY
    SELECT e.id, e.owner_account_id, ap.alias, e.community_id, e.title, e.description,
           e.category, e.exchange_type, e.status, e.contact_info, e.created_at,
           (SELECT count(*) FROM replica.campus_exchange_attachment att WHERE att.exchange_item_id = e.id)
    FROM replica.campus_exchange_item e
    JOIN replica.account_profile ap ON ap.account_id = e.owner_account_id
    WHERE (
        (coalesce(p_mine_only, false) AND e.owner_account_id = p_actor)
        OR (NOT coalesce(p_mine_only, false) AND e.status IN ('AVAILABLE', 'RESERVED'))
    )
      AND (clean_cat IS NULL OR e.category = clean_cat)
    ORDER BY e.created_at DESC
    LIMIT 60;
END;
$$;

CREATE OR REPLACE FUNCTION replica.create_exchange_item_v15(
    p_actor uuid,
    p_title text,
    p_description text,
    p_category text,
    p_exchange_type text,
    p_contact_info text
)
RETURNS TABLE (
    id uuid,
    owner_account_id uuid,
    owner_alias text,
    community_id uuid,
    title text,
    description text,
    category text,
    exchange_type text,
    status text,
    contact_info text,
    created_at timestamptz,
    attachment_count bigint
)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
DECLARE
    clean_title text;
    clean_desc text;
    clean_cat text;
    clean_type text;
    clean_contact text;
    saved replica.campus_exchange_item%ROWTYPE;
BEGIN
    PERFORM 1 FROM replica.access_account a WHERE a.id = p_actor AND a.enabled FOR SHARE;
    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1401', MESSAGE = 'account_unavailable';
    END IF;

    clean_title := btrim(coalesce(p_title, ''));
    clean_desc := btrim(coalesce(p_description, ''));
    clean_cat := coalesce(NULLIF(upper(btrim(p_category)), ''), 'BOOK');
    clean_type := coalesce(NULLIF(upper(btrim(p_exchange_type)), ''), 'EXCHANGE');
    clean_contact := btrim(coalesce(p_contact_info, ''));

    IF char_length(clean_title) NOT BETWEEN 3 AND 160 THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_title';
    END IF;
    IF char_length(clean_desc) NOT BETWEEN 5 AND 3000 THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_description';
    END IF;
    IF clean_cat NOT IN ('BOOK', 'CALCULATOR', 'LAB_EQUIPMENT', 'NOTES', 'TOOL', 'OTHER') THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_category';
    END IF;
    IF clean_type NOT IN ('GIFT', 'LOAN', 'EXCHANGE') THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_exchange_type';
    END IF;
    IF char_length(clean_contact) NOT BETWEEN 3 AND 300 THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_contact_info';
    END IF;

    INSERT INTO replica.campus_exchange_item (
        owner_account_id, community_id, title, description, category, exchange_type, status, contact_info
    )
    VALUES (
        p_actor, NULL, clean_title, clean_desc, clean_cat, clean_type, 'AVAILABLE', clean_contact
    )
    RETURNING * INTO saved;

    RETURN QUERY
    SELECT saved.id, saved.owner_account_id, ap.alias, saved.community_id, saved.title,
           saved.description, saved.category, saved.exchange_type, saved.status,
           saved.contact_info, saved.created_at, 0::bigint
    FROM replica.account_profile ap
    WHERE ap.account_id = p_actor;
END;
$$;

CREATE OR REPLACE FUNCTION replica.update_exchange_item_v15(
    p_actor uuid,
    p_item_id uuid,
    p_title text,
    p_description text,
    p_category text,
    p_exchange_type text,
    p_status text,
    p_contact_info text
)
RETURNS TABLE (
    id uuid,
    owner_account_id uuid,
    owner_alias text,
    community_id uuid,
    title text,
    description text,
    category text,
    exchange_type text,
    status text,
    contact_info text,
    created_at timestamptz,
    attachment_count bigint
)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
DECLARE
    clean_title text;
    clean_desc text;
    clean_cat text;
    clean_type text;
    clean_status text;
    clean_contact text;
    saved replica.campus_exchange_item%ROWTYPE;
BEGIN
    PERFORM 1 FROM replica.access_account a WHERE a.id = p_actor AND a.enabled FOR SHARE;
    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1401', MESSAGE = 'account_unavailable';
    END IF;

    clean_title := NULLIF(btrim(p_title), '');
    clean_desc := NULLIF(btrim(p_description), '');
    clean_cat := NULLIF(upper(btrim(p_category)), '');
    clean_type := NULLIF(upper(btrim(p_exchange_type)), '');
    clean_status := NULLIF(upper(btrim(p_status)), '');
    clean_contact := NULLIF(btrim(p_contact_info), '');

    IF clean_title IS NOT NULL AND char_length(clean_title) NOT BETWEEN 3 AND 160 THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_title';
    END IF;
    IF clean_desc IS NOT NULL AND char_length(clean_desc) NOT BETWEEN 5 AND 3000 THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_description';
    END IF;
    IF clean_cat IS NOT NULL AND clean_cat NOT IN ('BOOK', 'CALCULATOR', 'LAB_EQUIPMENT', 'NOTES', 'TOOL', 'OTHER') THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_category';
    END IF;
    IF clean_type IS NOT NULL AND clean_type NOT IN ('GIFT', 'LOAN', 'EXCHANGE') THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_exchange_type';
    END IF;
    IF clean_status IS NOT NULL AND clean_status NOT IN ('AVAILABLE', 'RESERVED', 'COMPLETED') THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_status';
    END IF;
    IF clean_contact IS NOT NULL AND char_length(clean_contact) NOT BETWEEN 3 AND 300 THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_contact_info';
    END IF;

    UPDATE replica.campus_exchange_item e
    SET title = coalesce(clean_title, e.title),
        description = coalesce(clean_desc, e.description),
        category = coalesce(clean_cat, e.category),
        exchange_type = coalesce(clean_type, e.exchange_type),
        status = coalesce(clean_status, e.status),
        contact_info = coalesce(clean_contact, e.contact_info)
    WHERE e.id = p_item_id AND e.owner_account_id = p_actor
    RETURNING * INTO saved;

    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1404', MESSAGE = 'exchange_item_not_found';
    END IF;

    RETURN QUERY
    SELECT saved.id, saved.owner_account_id, ap.alias, saved.community_id, saved.title,
           saved.description, saved.category, saved.exchange_type, saved.status,
           saved.contact_info, saved.created_at,
           (SELECT count(*) FROM replica.campus_exchange_attachment att WHERE att.exchange_item_id = saved.id)
    FROM replica.account_profile ap
    WHERE ap.account_id = p_actor;
END;
$$;

CREATE OR REPLACE FUNCTION replica.delete_exchange_item_v15(
    p_actor uuid,
    p_item_id uuid
)
RETURNS TABLE (storage_key text)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
DECLARE
    keys text[];
BEGIN
    PERFORM 1 FROM replica.access_account a WHERE a.id = p_actor AND a.enabled FOR SHARE;
    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1401', MESSAGE = 'account_unavailable';
    END IF;

    PERFORM 1 FROM replica.campus_exchange_item e
    WHERE e.id = p_item_id AND e.owner_account_id = p_actor
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1404', MESSAGE = 'exchange_item_not_found';
    END IF;

    SELECT coalesce(array_agg(att.storage_key), '{}'::text[]) INTO keys
    FROM replica.campus_exchange_attachment att
    WHERE att.exchange_item_id = p_item_id;

    DELETE FROM replica.campus_exchange_item e
    WHERE e.id = p_item_id AND e.owner_account_id = p_actor;

    RETURN QUERY SELECT unnest(keys);
END;
$$;

CREATE OR REPLACE FUNCTION replica.add_exchange_attachment_v15(
    p_actor uuid,
    p_item_id uuid,
    p_storage_key text,
    p_original_filename text,
    p_extension text,
    p_mime_type text,
    p_file_size_bytes bigint
)
RETURNS TABLE (
    id uuid,
    exchange_item_id uuid,
    original_filename text,
    extension text,
    mime_type text,
    file_size_bytes bigint,
    created_at timestamptz
)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
DECLARE
    current_count integer;
    saved replica.campus_exchange_attachment%ROWTYPE;
BEGIN
    PERFORM 1 FROM replica.access_account a WHERE a.id = p_actor AND a.enabled FOR SHARE;
    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1401', MESSAGE = 'account_unavailable';
    END IF;

    PERFORM 1 FROM replica.campus_exchange_item e
    WHERE e.id = p_item_id AND e.owner_account_id = p_actor
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1404', MESSAGE = 'exchange_item_not_found';
    END IF;

    SELECT count(*) INTO current_count
    FROM replica.campus_exchange_attachment att
    WHERE att.exchange_item_id = p_item_id;

    IF current_count >= 5 THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'max_attachments_reached';
    END IF;

    INSERT INTO replica.campus_exchange_attachment (
        exchange_item_id, uploader_account_id, storage_key, original_filename,
        extension, mime_type, file_size_bytes
    )
    VALUES (
        p_item_id, p_actor, btrim(p_storage_key), btrim(p_original_filename),
        lower(btrim(p_extension)), btrim(p_mime_type), p_file_size_bytes
    )
    RETURNING * INTO saved;

    RETURN QUERY
    SELECT saved.id, saved.exchange_item_id, saved.original_filename, saved.extension,
           saved.mime_type, saved.file_size_bytes, saved.created_at;
END;
$$;

CREATE OR REPLACE FUNCTION replica.list_exchange_attachments_v15(
    p_actor uuid,
    p_item_id uuid
)
RETURNS TABLE (
    id uuid,
    exchange_item_id uuid,
    original_filename text,
    extension text,
    mime_type text,
    file_size_bytes bigint,
    created_at timestamptz
)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    PERFORM 1 FROM replica.access_account a WHERE a.id = p_actor AND a.enabled FOR SHARE;
    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1401', MESSAGE = 'account_unavailable';
    END IF;

    PERFORM 1 FROM replica.campus_exchange_item e
    WHERE e.id = p_item_id
      AND (e.status IN ('AVAILABLE', 'RESERVED') OR e.owner_account_id = p_actor);

    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1404', MESSAGE = 'exchange_item_not_found';
    END IF;

    RETURN QUERY
    SELECT att.id, att.exchange_item_id, att.original_filename, att.extension,
           att.mime_type, att.file_size_bytes, att.created_at
    FROM replica.campus_exchange_attachment att
    WHERE att.exchange_item_id = p_item_id
    ORDER BY att.created_at ASC;
END;
$$;

CREATE OR REPLACE FUNCTION replica.read_exchange_attachment_v15(
    p_actor uuid,
    p_attachment_id uuid
)
RETURNS TABLE (
    id uuid,
    exchange_item_id uuid,
    storage_key text,
    original_filename text,
    extension text,
    mime_type text,
    file_size_bytes bigint
)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    PERFORM 1 FROM replica.access_account a WHERE a.id = p_actor AND a.enabled FOR SHARE;
    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1401', MESSAGE = 'account_unavailable';
    END IF;

    RETURN QUERY
    SELECT att.id, att.exchange_item_id, att.storage_key, att.original_filename,
           att.extension, att.mime_type, att.file_size_bytes
    FROM replica.campus_exchange_attachment att
    JOIN replica.campus_exchange_item e ON e.id = att.exchange_item_id
    WHERE att.id = p_attachment_id
      AND (e.status IN ('AVAILABLE', 'RESERVED') OR e.owner_account_id = p_actor);

    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1404', MESSAGE = 'attachment_not_found';
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION replica.delete_exchange_attachment_v15(
    p_actor uuid,
    p_attachment_id uuid
)
RETURNS text
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
DECLARE
    removed_key text;
BEGIN
    PERFORM 1 FROM replica.access_account a WHERE a.id = p_actor AND a.enabled FOR SHARE;
    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1401', MESSAGE = 'account_unavailable';
    END IF;

    DELETE FROM replica.campus_exchange_attachment att
    USING replica.campus_exchange_item e
    WHERE att.id = p_attachment_id
      AND e.id = att.exchange_item_id
      AND e.owner_account_id = p_actor
    RETURNING att.storage_key INTO removed_key;

    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1404', MESSAGE = 'attachment_not_found';
    END IF;

    RETURN removed_key;
END;
$$;

-- =============================================================================
-- 5. OPORTUNIDADES CAMPUS
-- =============================================================================

CREATE OR REPLACE FUNCTION replica.list_campus_opportunities_v15(
    p_actor uuid,
    p_category text
)
RETURNS TABLE (
    id uuid,
    owner_account_id uuid,
    owner_alias text,
    title text,
    description text,
    category text,
    source_institution text,
    source_url text,
    deadline timestamptz,
    created_at timestamptz
)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
DECLARE
    clean_cat text;
BEGIN
    PERFORM 1 FROM replica.access_account a WHERE a.id = p_actor AND a.enabled FOR SHARE;
    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1401', MESSAGE = 'account_unavailable';
    END IF;

    clean_cat := NULLIF(upper(btrim(p_category)), '');

    RETURN QUERY
    SELECT o.id, o.owner_account_id, ap.alias, o.title, o.description, o.category,
           o.source_institution, o.source_url, o.deadline, o.created_at
    FROM replica.campus_opportunity o
    LEFT JOIN replica.account_profile ap ON ap.account_id = o.owner_account_id
    WHERE o.active
      AND (o.deadline IS NULL OR o.deadline >= clock_timestamp())
      AND (clean_cat IS NULL OR o.category = clean_cat)
    ORDER BY o.created_at DESC
    LIMIT 60;
END;
$$;

CREATE OR REPLACE FUNCTION replica.create_campus_opportunity_v15(
    p_actor uuid,
    p_title text,
    p_description text,
    p_category text,
    p_institution text,
    p_url text,
    p_deadline timestamptz
)
RETURNS TABLE (
    id uuid,
    owner_account_id uuid,
    owner_alias text,
    title text,
    description text,
    category text,
    source_institution text,
    source_url text,
    deadline timestamptz,
    created_at timestamptz
)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
DECLARE
    clean_title text;
    clean_desc text;
    clean_cat text;
    clean_inst text;
    clean_url text;
    saved replica.campus_opportunity%ROWTYPE;
BEGIN
    PERFORM 1 FROM replica.access_account a WHERE a.id = p_actor AND a.enabled FOR SHARE;
    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1401', MESSAGE = 'account_unavailable';
    END IF;

    clean_title := btrim(coalesce(p_title, ''));
    clean_desc := btrim(coalesce(p_description, ''));
    clean_cat := coalesce(NULLIF(upper(btrim(p_category)), ''), 'SCHOLARSHIP');
    clean_inst := btrim(coalesce(p_institution, ''));
    clean_url := NULLIF(btrim(p_url), '');

    IF char_length(clean_title) NOT BETWEEN 3 AND 160 THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_title';
    END IF;
    IF char_length(clean_desc) NOT BETWEEN 10 AND 5000 THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_description';
    END IF;
    IF clean_cat NOT IN ('SCHOLARSHIP', 'INTERNSHIP', 'COURSE', 'CONTEST', 'CALL', 'VOLUNTEER', 'EVENT', 'JOB') THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_category';
    END IF;
    IF char_length(clean_inst) NOT BETWEEN 2 AND 160 THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_institution';
    END IF;
    IF clean_url IS NOT NULL AND clean_url !~ '^https?://[^\s/$.?#].[^\s]*$' THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_url';
    END IF;

    INSERT INTO replica.campus_opportunity (
        owner_account_id, title, description, category, source_institution, source_url, deadline, active
    )
    VALUES (
        p_actor, clean_title, clean_desc, clean_cat, clean_inst, clean_url, p_deadline, true
    )
    RETURNING * INTO saved;

    RETURN QUERY
    SELECT saved.id, saved.owner_account_id, ap.alias, saved.title, saved.description,
           saved.category, saved.source_institution, saved.source_url, saved.deadline, saved.created_at
    FROM replica.account_profile ap
    WHERE ap.account_id = p_actor;
END;
$$;

CREATE OR REPLACE FUNCTION replica.update_campus_opportunity_v15(
    p_actor uuid,
    p_opportunity_id uuid,
    p_title text,
    p_description text,
    p_category text,
    p_institution text,
    p_url text,
    p_deadline timestamptz
)
RETURNS TABLE (
    id uuid,
    owner_account_id uuid,
    owner_alias text,
    title text,
    description text,
    category text,
    source_institution text,
    source_url text,
    deadline timestamptz,
    created_at timestamptz
)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
DECLARE
    is_global_mod boolean;
    clean_title text;
    clean_desc text;
    clean_cat text;
    clean_inst text;
    clean_url text;
    saved replica.campus_opportunity%ROWTYPE;
BEGIN
    SELECT (a.global_role IN ('GLOBAL_MODERATOR', 'ADMIN')) INTO is_global_mod
    FROM replica.access_account a
    WHERE a.id = p_actor AND a.enabled
    FOR SHARE;

    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1401', MESSAGE = 'account_unavailable';
    END IF;

    clean_title := NULLIF(btrim(p_title), '');
    clean_desc := NULLIF(btrim(p_description), '');
    clean_cat := NULLIF(upper(btrim(p_category)), '');
    clean_inst := NULLIF(btrim(p_institution), '');
    clean_url := CASE WHEN p_url IS NULL THEN NULL ELSE NULLIF(btrim(p_url), '') END;

    IF clean_title IS NOT NULL AND char_length(clean_title) NOT BETWEEN 3 AND 160 THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_title';
    END IF;
    IF clean_desc IS NOT NULL AND char_length(clean_desc) NOT BETWEEN 10 AND 5000 THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_description';
    END IF;
    IF clean_cat IS NOT NULL AND clean_cat NOT IN ('SCHOLARSHIP', 'INTERNSHIP', 'COURSE', 'CONTEST', 'CALL', 'VOLUNTEER', 'EVENT', 'JOB') THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_category';
    END IF;
    IF clean_inst IS NOT NULL AND char_length(clean_inst) NOT BETWEEN 2 AND 160 THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_institution';
    END IF;
    IF clean_url IS NOT NULL AND clean_url !~ '^https?://[^\s/$.?#].[^\s]*$' THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_url';
    END IF;

    UPDATE replica.campus_opportunity o
    SET title = coalesce(clean_title, o.title),
        description = coalesce(clean_desc, o.description),
        category = coalesce(clean_cat, o.category),
        source_institution = coalesce(clean_inst, o.source_institution),
        source_url = CASE WHEN p_url IS NULL THEN o.source_url ELSE clean_url END,
        deadline = coalesce(p_deadline, o.deadline)
    WHERE o.id = p_opportunity_id
      AND o.active
      AND (o.owner_account_id = p_actor OR is_global_mod)
    RETURNING * INTO saved;

    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1404', MESSAGE = 'opportunity_not_found';
    END IF;

    RETURN QUERY
    SELECT saved.id, saved.owner_account_id, ap.alias, saved.title, saved.description,
           saved.category, saved.source_institution, saved.source_url, saved.deadline, saved.created_at
    FROM (SELECT saved.*) s
    LEFT JOIN replica.account_profile ap ON ap.account_id = saved.owner_account_id;
END;
$$;

CREATE OR REPLACE FUNCTION replica.delete_campus_opportunity_v15(
    p_actor uuid,
    p_opportunity_id uuid
)
RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
DECLARE
    is_global_mod boolean;
BEGIN
    SELECT (a.global_role IN ('GLOBAL_MODERATOR', 'ADMIN')) INTO is_global_mod
    FROM replica.access_account a
    WHERE a.id = p_actor AND a.enabled
    FOR SHARE;

    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1401', MESSAGE = 'account_unavailable';
    END IF;

    UPDATE replica.campus_opportunity o
    SET active = false
    WHERE o.id = p_opportunity_id
      AND o.active
      AND (o.owner_account_id = p_actor OR is_global_mod);

    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1404', MESSAGE = 'opportunity_not_found';
    END IF;
END;
$$;

-- =============================================================================
-- 6. PRIVILEGIOS ESTRICTOS (SOLO replica_runtime, SIN PUBLIC)
-- =============================================================================

REVOKE ALL ON FUNCTION
    replica.my_academic_tasks_v15(uuid, text, text),
    replica.create_academic_task_v15(uuid, text, text, text, timestamptz, text),
    replica.update_academic_task_v15(uuid, uuid, text, text, text, text, timestamptz, text, boolean),
    replica.delete_academic_task_v15(uuid, uuid),
    replica.read_study_profile_v15(uuid),
    replica.upsert_study_profile_v15(uuid, text[], text[], text, text, text, boolean),
    replica.search_study_matches_v15(uuid, text),
    replica.list_exchange_items_v15(uuid, text, boolean),
    replica.create_exchange_item_v15(uuid, text, text, text, text, text),
    replica.update_exchange_item_v15(uuid, uuid, text, text, text, text, text, text),
    replica.delete_exchange_item_v15(uuid, uuid),
    replica.add_exchange_attachment_v15(uuid, uuid, text, text, text, text, bigint),
    replica.list_exchange_attachments_v15(uuid, uuid),
    replica.read_exchange_attachment_v15(uuid, uuid),
    replica.delete_exchange_attachment_v15(uuid, uuid),
    replica.list_campus_opportunities_v15(uuid, text),
    replica.create_campus_opportunity_v15(uuid, text, text, text, text, text, timestamptz),
    replica.update_campus_opportunity_v15(uuid, uuid, text, text, text, text, text, timestamptz),
    replica.delete_campus_opportunity_v15(uuid, uuid)
FROM PUBLIC;

GRANT EXECUTE ON FUNCTION
    replica.my_academic_tasks_v15(uuid, text, text),
    replica.create_academic_task_v15(uuid, text, text, text, timestamptz, text),
    replica.update_academic_task_v15(uuid, uuid, text, text, text, text, timestamptz, text, boolean),
    replica.delete_academic_task_v15(uuid, uuid),
    replica.read_study_profile_v15(uuid),
    replica.upsert_study_profile_v15(uuid, text[], text[], text, text, text, boolean),
    replica.search_study_matches_v15(uuid, text),
    replica.list_exchange_items_v15(uuid, text, boolean),
    replica.create_exchange_item_v15(uuid, text, text, text, text, text),
    replica.update_exchange_item_v15(uuid, uuid, text, text, text, text, text, text),
    replica.delete_exchange_item_v15(uuid, uuid),
    replica.add_exchange_attachment_v15(uuid, uuid, text, text, text, text, bigint),
    replica.list_exchange_attachments_v15(uuid, uuid),
    replica.read_exchange_attachment_v15(uuid, uuid),
    replica.delete_exchange_attachment_v15(uuid, uuid),
    replica.list_campus_opportunities_v15(uuid, text),
    replica.create_campus_opportunity_v15(uuid, text, text, text, text, text, timestamptz),
    replica.update_campus_opportunity_v15(uuid, uuid, text, text, text, text, text, timestamptz),
    replica.delete_campus_opportunity_v15(uuid, uuid)
TO replica_runtime;
