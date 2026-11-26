-- ============================================================================
-- SteamBeta Campus — Script de mantenimiento manual (fuera de Flyway)
-- Propósito: Aumentar el límite máximo de consulta de
--            replica.control_center_search_students de 50 a 200 resultados.
--
-- REQUISITOS Y ADVERTENCIAS:
-- 1. Ejecutar manualmente únicamente con una conexión DBA autorizada
--    (p. ej. replica_migrator). NO ejecutar automáticamente al iniciar la app.
-- 2. NO contiene contraseñas ni credenciales.
-- 3. NO modifica tablas, columnas, triggers, constraints, RLS ni grants.
-- 4. Conserva intacta la firma, columnas de retorno, SECURITY DEFINER,
--    search_path, validación replica.require_global_moderator(p_actor),
--    propietario y permisos existentes de V11.
-- 5. IMPORTANTE: Al ser un parche de mantenimiento independiente de Flyway
--    (V1–V17 permanecen intactas), debe aplicarse también en cualquier entorno
--    nuevo que se reconstruya desde cero a partir de V1–V17.
-- ============================================================================

BEGIN;

CREATE OR REPLACE FUNCTION replica.control_center_search_students(
    p_actor uuid,
    p_query text,
    p_limit integer
)
RETURNS TABLE (
    account_id uuid,
    login_identifier text,
    alias text,
    declared_university text,
    declared_career text,
    enabled boolean,
    global_role text,
    created_at timestamptz,
    active_sanctions_count bigint,
    communities_count bigint,
    posts_count bigint
)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    PERFORM replica.require_global_moderator(p_actor);

    IF p_limit IS NULL OR p_limit NOT BETWEEN 1 AND 200 THEN
        p_limit := 20;
    END IF;

    RETURN QUERY
    SELECT a.id, a.login_identifier, ap.alias, ap.declared_university, ap.declared_career,
           a.enabled, a.global_role, a.created_at,
           (SELECT count(*) FROM replica.account_sanction s WHERE s.target_account_id = a.id AND s.active),
           (SELECT count(*) FROM replica.community_membership m WHERE m.account_id = a.id AND m.active),
           (SELECT count(*) FROM replica.community_post p WHERE p.author_account_id = a.id AND p.status <> 'DELETED')
    FROM replica.access_account a
    JOIN replica.account_profile ap ON ap.account_id = a.id
    WHERE (
        p_query IS NULL OR p_query = ''
        OR a.login_identifier ILIKE '%' || btrim(p_query) || '%'
        OR ap.alias ILIKE '%' || btrim(p_query) || '%'
    )
    ORDER BY a.created_at DESC
    LIMIT p_limit;
END;
$$;

COMMIT;
