-- V17: Corrección de replica.update_account_profile_v2
-- Elimina exclusivamente la asignación a la columna inexistente updated_at
-- en replica.account_profile, conservando firma, validaciones, SECURITY DEFINER,
-- search_path fijo, propietario y permisos existentes.

CREATE OR REPLACE FUNCTION replica.update_account_profile_v2(
    p_actor uuid,
    p_alias text,
    p_declared_university text,
    p_declared_career text,
    p_declared_semester integer,
    p_bio text,
    p_avatar_url text,
    p_academic_interests text,
    p_email_public boolean
)
RETURNS TABLE (
    account_id uuid,
    alias text,
    declared_university text,
    declared_career text,
    declared_semester integer,
    bio text,
    avatar_url text,
    academic_interests text,
    email_public boolean
)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    -- Verificación de autorización: la cuenta debe existir y estar activa
    PERFORM 1 FROM replica.access_account a WHERE a.id = p_actor AND a.enabled FOR SHARE;
    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1401', MESSAGE = 'account_unavailable';
    END IF;

    IF char_length(btrim(p_alias)) NOT BETWEEN 2 AND 60 THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_alias';
    END IF;

    IF p_declared_semester IS NOT NULL AND (p_declared_semester < 1 OR p_declared_semester > 20) THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_semester';
    END IF;

    UPDATE replica.account_profile
    SET alias = btrim(p_alias),
        declared_university = NULLIF(btrim(p_declared_university), ''),
        declared_career = NULLIF(btrim(p_declared_career), ''),
        declared_semester = p_declared_semester,
        bio = NULLIF(btrim(p_bio), ''),
        avatar_url = NULLIF(btrim(p_avatar_url), ''),
        academic_interests = NULLIF(btrim(p_academic_interests), ''),
        email_public = COALESCE(p_email_public, false)
    WHERE account_profile.account_id = p_actor;

    RETURN QUERY
    SELECT ap.account_id, ap.alias, ap.declared_university, ap.declared_career,
           ap.declared_semester, ap.bio, ap.avatar_url, ap.academic_interests, ap.email_public
    FROM replica.account_profile ap
    WHERE ap.account_id = p_actor;
END;
$$;

REVOKE ALL ON FUNCTION replica.update_account_profile_v2(uuid, text, text, text, integer, text, text, text, boolean) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION replica.update_account_profile_v2(uuid, text, text, text, integer, text, text, text, boolean) TO "${backendLogin}";
