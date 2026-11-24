-- V14: Acceso global y capacidad máxima de integrantes por comunidad
-- SteamBeta Campus (PostgreSQL 18, RLS estricto, compatible con V1-V13)
-- Alcance exclusivo: módulo de Comunidades.

-- 1. Columna persistente de capacidad máxima en replica.community
ALTER TABLE replica.community
    ADD COLUMN IF NOT EXISTS max_members integer NOT NULL DEFAULT 50
        CHECK (max_members BETWEEN 2 AND 50);

-- 2. Crear comunidad con capacidad máxima configurable (el creador cuenta como ADMIN y ocupa 1 espacio)
CREATE OR REPLACE FUNCTION replica.create_community_v14(
    p_actor uuid,
    p_slug text,
    p_name text,
    p_description text,
    p_rules text,
    p_category text,
    p_visibility text,
    p_max_members integer
)
RETURNS TABLE (
    id uuid,
    slug text,
    name text,
    description text,
    rules text,
    category text,
    visibility text,
    role text,
    member_count bigint,
    max_members integer,
    available_slots integer
)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
DECLARE
    new_community_id uuid;
    clean_slug text;
    clean_name text;
    clean_desc text;
    clean_rules text;
    clean_cat text;
    clean_vis text;
    clean_max integer;
BEGIN
    PERFORM 1 FROM replica.access_account a WHERE a.id = p_actor AND a.enabled FOR SHARE;
    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1401', MESSAGE = 'account_unavailable';
    END IF;

    clean_slug := lower(btrim(p_slug));
    clean_name := btrim(p_name);
    clean_desc := coalesce(btrim(p_description), '');
    clean_rules := coalesce(btrim(p_rules), 'Respeta a los miembros y colabora constructivamente.');
    clean_cat := coalesce(upper(btrim(p_category)), 'ACADEMICA');
    clean_vis := coalesce(upper(btrim(p_visibility)), 'PUBLIC');
    clean_max := coalesce(p_max_members, 50);

    IF clean_slug !~ '^[a-z0-9][a-z0-9-]{2,79}$' THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_slug';
    END IF;

    IF char_length(clean_name) NOT BETWEEN 3 AND 100 THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_community_name';
    END IF;

    IF clean_cat NOT IN ('ACADEMICA', 'ESTUDIO', 'PROYECTO', 'CARRERA', 'INTERES') THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_category';
    END IF;

    IF clean_vis NOT IN ('PUBLIC', 'INVITE_ONLY', 'PRIVATE') THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_visibility';
    END IF;

    IF clean_max NOT BETWEEN 2 AND 50 THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_max_members';
    END IF;

    INSERT INTO replica.community (slug, name, description, rules, category, visibility, max_members)
    VALUES (clean_slug, clean_name, clean_desc, clean_rules, clean_cat, clean_vis, clean_max)
    RETURNING replica.community.id INTO new_community_id;

    INSERT INTO replica.community_membership (community_id, account_id, role, active)
    VALUES (new_community_id, p_actor, 'ADMIN', true);

    RETURN QUERY
    SELECT c.id, c.slug, c.name, c.description, c.rules, c.category, c.visibility,
           'ADMIN'::text,
           1::bigint,
           c.max_members,
           GREATEST(0, c.max_members - 1)::integer
    FROM replica.community c
    WHERE c.id = new_community_id;
EXCEPTION WHEN unique_violation THEN
    RAISE EXCEPTION USING ERRCODE = 'P1409', MESSAGE = 'community_slug_conflict';
END;
$$;

-- 3. Unión directa segura a comunidades PUBLIC con bloqueo FOR UPDATE y control de capacidad
CREATE OR REPLACE FUNCTION replica.join_public_community_v14(
    p_actor uuid,
    p_community uuid
)
RETURNS TABLE (
    id uuid,
    slug text,
    name text,
    description text,
    rules text,
    category text,
    visibility text,
    role text,
    member_count bigint,
    max_members integer,
    available_slots integer
)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
DECLARE
    comm replica.community%ROWTYPE;
    active_members bigint;
BEGIN
    PERFORM 1 FROM replica.access_account a WHERE a.id = p_actor AND a.enabled FOR SHARE;
    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1401', MESSAGE = 'account_unavailable';
    END IF;

    -- Serializar ingresos concurrentes bloqueando la fila de la comunidad
    SELECT c.* INTO comm
    FROM replica.community c
    WHERE c.id = p_community AND c.active
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1404', MESSAGE = 'community_unavailable';
    END IF;

    IF comm.visibility <> 'PUBLIC' THEN
        RAISE EXCEPTION USING ERRCODE = '42501', MESSAGE = 'community_requires_invitation';
    END IF;

    IF EXISTS (
        SELECT 1 FROM replica.community_membership cm
        WHERE cm.community_id = comm.id AND cm.account_id = p_actor AND cm.active
    ) THEN
        RAISE EXCEPTION USING ERRCODE = 'P1409', MESSAGE = 'already_member';
    END IF;

    SELECT count(*) INTO active_members
    FROM replica.community_membership cm
    WHERE cm.community_id = comm.id AND cm.active;

    IF active_members >= comm.max_members THEN
        RAISE EXCEPTION USING ERRCODE = 'P1409', MESSAGE = 'community_full';
    END IF;

    INSERT INTO replica.community_membership (community_id, account_id, role, active)
    VALUES (comm.id, p_actor, 'MEMBER', true)
    ON CONFLICT (community_id, account_id) DO UPDATE
        SET role = 'MEMBER', active = true, joined_at = clock_timestamp();

    active_members := active_members + 1;

    RETURN QUERY
    SELECT comm.id, comm.slug, comm.name, comm.description, comm.rules, comm.category, comm.visibility,
           'MEMBER'::text,
           active_members,
           comm.max_members,
           GREATEST(0, comm.max_members - active_members::integer)::integer;
END;
$$;

-- 4. Unión por código de invitación corregida (sin ambigüedad 42702), con validación de comunidad y capacidad atómica
CREATE OR REPLACE FUNCTION replica.join_community_by_code_v14(
    p_actor uuid,
    p_code text,
    p_expected_community uuid
)
RETURNS TABLE (
    id uuid,
    slug text,
    name text,
    description text,
    rules text,
    category text,
    visibility text,
    role text,
    member_count bigint,
    max_members integer,
    available_slots integer
)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
DECLARE
    clean_code text;
    target_community_id uuid;
    comm replica.community%ROWTYPE;
    inv replica.community_invitation%ROWTYPE;
    active_members bigint;
BEGIN
    PERFORM 1 FROM replica.access_account a WHERE a.id = p_actor AND a.enabled FOR SHARE;
    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1401', MESSAGE = 'account_unavailable';
    END IF;

    clean_code := upper(btrim(p_code));

    -- Localizar comunidad asociada al código antes de adquirir bloqueos (orden de bloqueo: community -> invitation)
    SELECT ci.community_id INTO target_community_id
    FROM replica.community_invitation ci
    WHERE ci.code = clean_code AND NOT ci.revoked;

    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1404', MESSAGE = 'invitation_not_found';
    END IF;

    IF p_expected_community IS NOT NULL AND target_community_id <> p_expected_community THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invitation_community_mismatch';
    END IF;

    -- 1) Bloquear la comunidad para serializar verificación de capacidad
    SELECT c.* INTO comm
    FROM replica.community c
    WHERE c.id = target_community_id AND c.active
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1404', MESSAGE = 'community_unavailable';
    END IF;

    -- 2) Bloquear la invitación en orden consistente para evitar interbloqueos
    SELECT ci.* INTO inv
    FROM replica.community_invitation ci
    WHERE ci.code = clean_code AND ci.community_id = comm.id AND NOT ci.revoked
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1404', MESSAGE = 'invitation_not_found';
    END IF;

    IF inv.expires_at IS NOT NULL AND inv.expires_at <= clock_timestamp() THEN
        RAISE EXCEPTION USING ERRCODE = 'P1404', MESSAGE = 'invitation_expired';
    END IF;

    IF inv.max_uses IS NOT NULL AND inv.used_count >= inv.max_uses THEN
        RAISE EXCEPTION USING ERRCODE = 'P1404', MESSAGE = 'invitation_exhausted';
    END IF;

    IF EXISTS (
        SELECT 1 FROM replica.community_membership cm
        WHERE cm.community_id = comm.id AND cm.account_id = p_actor AND cm.active
    ) THEN
        RAISE EXCEPTION USING ERRCODE = 'P1409', MESSAGE = 'already_member';
    END IF;

    -- Validar capacidad ANTES de consumir el código
    SELECT count(*) INTO active_members
    FROM replica.community_membership cm
    WHERE cm.community_id = comm.id AND cm.active;

    IF active_members >= comm.max_members THEN
        RAISE EXCEPTION USING ERRCODE = 'P1409', MESSAGE = 'community_full';
    END IF;

    INSERT INTO replica.community_membership (community_id, account_id, role, active)
    VALUES (comm.id, p_actor, 'MEMBER', true)
    ON CONFLICT (community_id, account_id) DO UPDATE
        SET role = 'MEMBER', active = true, joined_at = clock_timestamp();

    UPDATE replica.community_invitation ci
    SET used_count = ci.used_count + 1
    WHERE ci.id = inv.id;

    active_members := active_members + 1;

    RETURN QUERY
    SELECT comm.id, comm.slug, comm.name, comm.description, comm.rules, comm.category, comm.visibility,
           'MEMBER'::text,
           active_members,
           comm.max_members,
           GREATEST(0, comm.max_members - active_members::integer)::integer;
END;
$$;

-- 5. Catálogo global de comunidades visibles (PUBLIC e INVITE_ONLY; jamás PRIVATE) con ocupación y capacidad
CREATE OR REPLACE FUNCTION replica.explore_communities_v14(p_actor uuid)
RETURNS TABLE (
    id uuid,
    slug text,
    name text,
    description text,
    category text,
    visibility text,
    member_count bigint,
    max_members integer,
    available_slots integer,
    is_member boolean,
    user_role text
)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    PERFORM 1 FROM replica.access_account a WHERE a.id = p_actor AND a.enabled FOR SHARE;
    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1401', MESSAGE = 'account_unavailable';
    END IF;

    RETURN QUERY
    SELECT c.id, c.slug, c.name, c.description, c.category, c.visibility,
           counts.cnt,
           c.max_members,
           GREATEST(0, c.max_members - counts.cnt::integer)::integer,
           EXISTS(
               SELECT 1 FROM replica.community_membership my
               WHERE my.community_id = c.id AND my.account_id = p_actor AND my.active
           ),
           (
               SELECT my.role FROM replica.community_membership my
               WHERE my.community_id = c.id AND my.account_id = p_actor AND my.active
               LIMIT 1
           )
    FROM replica.community c
    CROSS JOIN LATERAL (
        SELECT count(*)::bigint AS cnt
        FROM replica.community_membership m
        WHERE m.community_id = c.id AND m.active
    ) counts
    WHERE c.active AND c.visibility IN ('PUBLIC', 'INVITE_ONLY')
    ORDER BY c.name ASC;
END;
$$;

-- 6. Mis comunidades con ocupación y capacidad real
CREATE OR REPLACE FUNCTION replica.my_communities_v14(p_actor uuid)
RETURNS TABLE (
    id uuid,
    slug text,
    name text,
    description text,
    rules text,
    category text,
    visibility text,
    role text,
    member_count bigint,
    max_members integer,
    available_slots integer
)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    PERFORM 1 FROM replica.access_account a WHERE a.id = p_actor AND a.enabled FOR SHARE;
    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1401', MESSAGE = 'account_unavailable';
    END IF;

    RETURN QUERY
    SELECT c.id, c.slug, c.name, c.description, c.rules, c.category, c.visibility,
           m.role,
           counts.cnt,
           c.max_members,
           GREATEST(0, c.max_members - counts.cnt::integer)::integer
    FROM replica.community c
    JOIN replica.community_membership m
      ON m.community_id = c.id AND m.account_id = p_actor AND m.active
    CROSS JOIN LATERAL (
        SELECT count(*)::bigint AS cnt
        FROM replica.community_membership all_m
        WHERE all_m.community_id = c.id AND all_m.active
    ) counts
    WHERE c.active
    ORDER BY c.name ASC;
END;
$$;

-- 7. Privilegios estrictos: revocar PUBLIC y otorgar EXECUTE únicamente a replica_runtime
REVOKE ALL ON FUNCTION
    replica.create_community_v14(uuid, text, text, text, text, text, text, integer),
    replica.join_public_community_v14(uuid, uuid),
    replica.join_community_by_code_v14(uuid, text, uuid),
    replica.explore_communities_v14(uuid),
    replica.my_communities_v14(uuid)
FROM PUBLIC;

GRANT EXECUTE ON FUNCTION
    replica.create_community_v14(uuid, text, text, text, text, text, text, integer),
    replica.join_public_community_v14(uuid, uuid),
    replica.join_community_by_code_v14(uuid, text, uuid),
    replica.explore_communities_v14(uuid),
    replica.my_communities_v14(uuid)
TO replica_runtime;
