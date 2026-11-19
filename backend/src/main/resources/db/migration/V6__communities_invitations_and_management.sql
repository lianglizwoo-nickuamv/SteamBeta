-- V6: Gestión de comunidades desde la aplicación, invitaciones seguras y ampliación de perfil
-- SteamBeta Campus: compatibilidad garantizada con V1-V5

-- 1. Ampliación del perfil estudiantil
ALTER TABLE replica.account_profile
    ADD COLUMN IF NOT EXISTS bio text CHECK (bio IS NULL OR char_length(bio) <= 500),
    ADD COLUMN IF NOT EXISTS avatar_url text CHECK (avatar_url IS NULL OR char_length(avatar_url) <= 255),
    ADD COLUMN IF NOT EXISTS academic_interests text CHECK (academic_interests IS NULL OR char_length(academic_interests) <= 300);

-- 2. Ampliación de comunidades: categoría y visibilidad
ALTER TABLE replica.community
    ADD COLUMN IF NOT EXISTS category text NOT NULL DEFAULT 'ACADEMICA'
        CHECK (category IN ('ACADEMICA', 'ESTUDIO', 'PROYECTO', 'CARRERA', 'INTERES')),
    ADD COLUMN IF NOT EXISTS visibility text NOT NULL DEFAULT 'PUBLIC'
        CHECK (visibility IN ('PUBLIC', 'INVITE_ONLY', 'PRIVATE'));

-- 3. Tabla de códigos de invitación
CREATE TABLE IF NOT EXISTS replica.community_invitation (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    community_id uuid NOT NULL REFERENCES replica.community(id) ON DELETE RESTRICT,
    code text NOT NULL UNIQUE CHECK (code ~ '^[A-Z0-9]{6,16}$'),
    creator_account_id uuid NOT NULL REFERENCES replica.access_account(id) ON DELETE RESTRICT,
    max_uses integer CHECK (max_uses IS NULL OR max_uses > 0),
    used_count integer NOT NULL DEFAULT 0,
    expires_at timestamptz,
    revoked boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX IF NOT EXISTS invitation_by_community ON replica.community_invitation (community_id, created_at DESC);
CREATE INDEX IF NOT EXISTS invitation_lookup_code ON replica.community_invitation (code) WHERE NOT revoked;

ALTER TABLE replica.community_invitation ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON replica.community_invitation FROM PUBLIC, replica_app, replica_academic;

-- 4. Funciones de Perfil Estudiantil
CREATE OR REPLACE FUNCTION replica.update_account_profile(
    p_actor uuid,
    p_alias text,
    p_declared_university text,
    p_declared_career text,
    p_bio text,
    p_avatar_url text,
    p_academic_interests text
)
RETURNS TABLE (
    account_id uuid,
    alias text,
    declared_university text,
    declared_career text,
    bio text,
    avatar_url text,
    academic_interests text
)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    PERFORM 1 FROM replica.access_account a WHERE a.id = p_actor AND a.enabled FOR SHARE;
    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1401', MESSAGE = 'account_unavailable';
    END IF;

    IF char_length(btrim(p_alias)) NOT BETWEEN 2 AND 60 THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_alias';
    END IF;

    UPDATE replica.account_profile
    SET alias = btrim(p_alias),
        declared_university = NULLIF(btrim(p_declared_university), ''),
        declared_career = NULLIF(btrim(p_declared_career), ''),
        bio = NULLIF(btrim(p_bio), ''),
        avatar_url = NULLIF(btrim(p_avatar_url), ''),
        academic_interests = NULLIF(btrim(p_academic_interests), ''),
        updated_at = clock_timestamp()
    WHERE account_profile.account_id = p_actor;

    RETURN QUERY
    SELECT ap.account_id, ap.alias, ap.declared_university, ap.declared_career,
           ap.bio, ap.avatar_url, ap.academic_interests
    FROM replica.account_profile ap
    WHERE ap.account_id = p_actor;
END;
$$;

-- 5. Crear Comunidad desde JavaFX (el creador adquiere rol ADMIN automáticamente)
CREATE OR REPLACE FUNCTION replica.create_community(
    p_actor uuid,
    p_slug text,
    p_name text,
    p_description text,
    p_rules text,
    p_category text,
    p_visibility text
)
RETURNS TABLE (
    id uuid,
    slug text,
    name text,
    description text,
    rules text,
    category text,
    visibility text,
    role text
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

    INSERT INTO replica.community (slug, name, description, rules, category, visibility)
    VALUES (clean_slug, clean_name, clean_desc, clean_rules, clean_cat, clean_vis)
    RETURNING community.id INTO new_community_id;

    -- Asignar al creador como ADMIN
    INSERT INTO replica.community_membership (community_id, account_id, role, active)
    VALUES (new_community_id, p_actor, 'ADMIN', true);

    RETURN QUERY
    SELECT c.id, c.slug, c.name, c.description, c.rules, c.category, c.visibility, 'ADMIN'::text
    FROM replica.community c
    WHERE c.id = new_community_id;
EXCEPTION WHEN unique_violation THEN
    RAISE EXCEPTION USING ERRCODE = 'P1409', MESSAGE = 'community_slug_conflict';
END;
$$;

-- 6. Explorar Comunidades Públicas
CREATE OR REPLACE FUNCTION replica.explore_communities(p_actor uuid)
RETURNS TABLE (
    id uuid,
    slug text,
    name text,
    description text,
    category text,
    visibility text,
    member_count bigint,
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
           (SELECT count(*) FROM replica.community_membership m WHERE m.community_id = c.id AND m.active),
           EXISTS(SELECT 1 FROM replica.community_membership my WHERE my.community_id = c.id AND my.account_id = p_actor AND my.active),
           (SELECT my.role FROM replica.community_membership my WHERE my.community_id = c.id AND my.account_id = p_actor AND my.active LIMIT 1)
    FROM replica.community c
    WHERE c.active AND c.visibility IN ('PUBLIC', 'INVITE_ONLY')
    ORDER BY c.name ASC;
END;
$$;

-- 7. Generar código de invitación (requiere ADMIN o MODERATOR)
CREATE OR REPLACE FUNCTION replica.create_community_invitation(
    p_actor uuid,
    p_community uuid,
    p_code text,
    p_max_uses integer,
    p_expires_at timestamptz
)
RETURNS TABLE (
    id uuid,
    community_id uuid,
    code text,
    max_uses integer,
    used_count integer,
    expires_at timestamptz,
    created_at timestamptz
)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
DECLARE
    clean_code text;
    new_invitation replica.community_invitation%ROWTYPE;
BEGIN
    PERFORM replica.require_community_member(p_actor, p_community);
    IF NOT EXISTS(SELECT 1 FROM replica.community_membership m
                  WHERE m.community_id = p_community AND m.account_id = p_actor
                    AND m.role IN ('MODERATOR', 'ADMIN') AND m.active) THEN
        RAISE EXCEPTION USING ERRCODE = 'P1404', MESSAGE = 'community_unavailable';
    END IF;

    clean_code := upper(btrim(p_code));
    IF clean_code !~ '^[A-Z0-9]{6,16}$' THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_invitation_code';
    END IF;

    IF p_max_uses IS NOT NULL AND p_max_uses <= 0 THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_max_uses';
    END IF;

    IF p_expires_at IS NOT NULL AND p_expires_at <= clock_timestamp() THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_expiration';
    END IF;

    INSERT INTO replica.community_invitation (community_id, code, creator_account_id, max_uses, expires_at)
    VALUES (p_community, clean_code, p_actor, p_max_uses, p_expires_at)
    RETURNING * INTO new_invitation;

    RETURN QUERY
    SELECT new_invitation.id, new_invitation.community_id, new_invitation.code,
           new_invitation.max_uses, new_invitation.used_count, new_invitation.expires_at, new_invitation.created_at;
EXCEPTION WHEN unique_violation THEN
    RAISE EXCEPTION USING ERRCODE = 'P1409', MESSAGE = 'code_already_exists';
END;
$$;

-- 8. Unirse a una comunidad mediante código de invitación
-- CRÍTICO: Siempre asigna rol 'MEMBER', nunca otorga MODERATOR ni ADMIN.
CREATE OR REPLACE FUNCTION replica.join_community_by_code(
    p_actor uuid,
    p_code text
)
RETURNS TABLE (
    community_id uuid,
    slug text,
    name text,
    description text,
    role text
)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
DECLARE
    inv replica.community_invitation%ROWTYPE;
    comm replica.community%ROWTYPE;
    clean_code text;
BEGIN
    PERFORM 1 FROM replica.access_account a WHERE a.id = p_actor AND a.enabled FOR SHARE;
    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1401', MESSAGE = 'account_unavailable';
    END IF;

    clean_code := upper(btrim(p_code));
    SELECT * INTO inv FROM replica.community_invitation
    WHERE code = clean_code AND NOT revoked FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1404', MESSAGE = 'invitation_not_found';
    END IF;

    IF inv.expires_at IS NOT NULL AND inv.expires_at <= clock_timestamp() THEN
        RAISE EXCEPTION USING ERRCODE = 'P1404', MESSAGE = 'invitation_expired';
    END IF;

    IF inv.max_uses IS NOT NULL AND inv.used_count >= inv.max_uses THEN
        RAISE EXCEPTION USING ERRCODE = 'P1404', MESSAGE = 'invitation_exhausted';
    END IF;

    SELECT * INTO comm FROM replica.community
    WHERE id = inv.community_id AND active;

    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1404', MESSAGE = 'community_unavailable';
    END IF;

    -- Verificar si ya es miembro
    IF EXISTS(SELECT 1 FROM replica.community_membership
              WHERE community_id = comm.id AND account_id = p_actor AND active) THEN
        RAISE EXCEPTION USING ERRCODE = 'P1409', MESSAGE = 'already_member';
    END IF;

    -- Registrar membresía (siempre rol MEMBER)
    INSERT INTO replica.community_membership (community_id, account_id, role, active)
    VALUES (comm.id, p_actor, 'MEMBER', true)
    ON CONFLICT (community_id, account_id) DO UPDATE
        SET role = 'MEMBER', active = true, joined_at = clock_timestamp();

    -- Incrementar contador de usos
    UPDATE replica.community_invitation
    SET used_count = used_count + 1
    WHERE id = inv.id;

    RETURN QUERY
    SELECT comm.id, comm.slug, comm.name, comm.description, 'MEMBER'::text;
END;
$$;

-- 9. Listar integrantes de una comunidad
CREATE OR REPLACE FUNCTION replica.community_members(
    p_actor uuid,
    p_community uuid
)
RETURNS TABLE (
    account_id uuid,
    alias text,
    role text,
    declared_university text,
    declared_career text,
    avatar_url text,
    joined_at timestamptz
)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    PERFORM replica.require_community_member(p_actor, p_community);

    RETURN QUERY
    SELECT m.account_id, ap.alias, m.role, ap.declared_university, ap.declared_career,
           ap.avatar_url, m.joined_at
    FROM replica.community_membership m
    JOIN replica.account_profile ap ON ap.account_id = m.account_id
    WHERE m.community_id = p_community AND m.active
    ORDER BY CASE m.role WHEN 'ADMIN' THEN 1 WHEN 'MODERATOR' THEN 2 ELSE 3 END,
             ap.alias ASC;
END;
$$;

-- 10. Actualizar rol de miembro (solo ADMIN de la comunidad)
CREATE OR REPLACE FUNCTION replica.update_member_role(
    p_actor uuid,
    p_community uuid,
    p_target_account uuid,
    p_new_role text
)
RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
DECLARE
    clean_role text;
BEGIN
    PERFORM replica.require_community_member(p_actor, p_community);
    IF NOT EXISTS(SELECT 1 FROM replica.community_membership m
                  WHERE m.community_id = p_community AND m.account_id = p_actor
                    AND m.role = 'ADMIN' AND m.active) THEN
        RAISE EXCEPTION USING ERRCODE = 'P1404', MESSAGE = 'community_unavailable';
    END IF;

    clean_role := upper(btrim(p_new_role));
    IF clean_role NOT IN ('MEMBER', 'MODERATOR', 'ADMIN') THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_role';
    END IF;

    IF p_actor = p_target_account AND clean_role <> 'ADMIN' THEN
        -- Evitar que el último admin se degrade a sí mismo dejando acéfala la comunidad
        IF (SELECT count(*) FROM replica.community_membership
            WHERE community_id = p_community AND role = 'ADMIN' AND active) <= 1 THEN
            RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'cannot_demote_last_admin';
        END IF;
    END IF;

    UPDATE replica.community_membership
    SET role = clean_role
    WHERE community_id = p_community AND account_id = p_target_account AND active;

    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1404', MESSAGE = 'member_unavailable';
    END IF;
END;
$$;

-- 11. Retirar miembro de comunidad (ADMIN o salida propia)
CREATE OR REPLACE FUNCTION replica.remove_community_member(
    p_actor uuid,
    p_community uuid,
    p_target_account uuid
)
RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    PERFORM replica.require_community_member(p_actor, p_community);

    -- Si no es el propio usuario saliéndose, debe ser ADMIN
    IF p_actor <> p_target_account THEN
        IF NOT EXISTS(SELECT 1 FROM replica.community_membership m
                      WHERE m.community_id = p_community AND m.account_id = p_actor
                        AND m.role = 'ADMIN' AND m.active) THEN
            RAISE EXCEPTION USING ERRCODE = 'P1404', MESSAGE = 'community_unavailable';
        END IF;
    END IF;

    -- Si el objetivo es ADMIN, verificar que no sea el último
    IF EXISTS(SELECT 1 FROM replica.community_membership
              WHERE community_id = p_community AND account_id = p_target_account
                AND role = 'ADMIN' AND active) THEN
        IF (SELECT count(*) FROM replica.community_membership
            WHERE community_id = p_community AND role = 'ADMIN' AND active) <= 1 THEN
            RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'cannot_remove_last_admin';
        END IF;
    END IF;

    UPDATE replica.community_membership
    SET active = false
    WHERE community_id = p_community AND account_id = p_target_account AND active;

    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1404', MESSAGE = 'member_unavailable';
    END IF;
END;
$$;

-- Conceder permisos al usuario de runtime del backend
REVOKE ALL ON FUNCTION
    replica.update_account_profile(uuid, text, text, text, text, text, text),
    replica.create_community(uuid, text, text, text, text, text, text),
    replica.explore_communities(uuid),
    replica.create_community_invitation(uuid, uuid, text, integer, timestamptz),
    replica.join_community_by_code(uuid, text),
    replica.community_members(uuid, uuid),
    replica.update_member_role(uuid, uuid, uuid, text),
    replica.remove_community_member(uuid, uuid, uuid)
FROM PUBLIC;

GRANT EXECUTE ON FUNCTION
    replica.update_account_profile(uuid, text, text, text, text, text, text),
    replica.create_community(uuid, text, text, text, text, text, text),
    replica.explore_communities(uuid),
    replica.create_community_invitation(uuid, uuid, text, integer, timestamptz),
    replica.join_community_by_code(uuid, text),
    replica.community_members(uuid, uuid),
    replica.update_member_role(uuid, uuid, uuid, text),
    replica.remove_community_member(uuid, uuid, uuid)
TO "${backendLogin}";
