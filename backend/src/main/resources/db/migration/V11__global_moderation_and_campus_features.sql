-- V11: Moderación Global, Control Center, Organizador Académico, Equipos de Estudio,
--      Oportunidades Campus e Intercambio Universitario.
-- SteamBeta Campus: compatibilidad garantizada con V1 a V10.

-- 1. Rol Global en cuentas de acceso
ALTER TABLE replica.access_account
    ADD COLUMN IF NOT EXISTS global_role text NOT NULL DEFAULT 'USER'
        CHECK (global_role IN ('USER', 'GLOBAL_MODERATOR', 'ADMIN'));

CREATE INDEX IF NOT EXISTS account_by_global_role ON replica.access_account(global_role)
    WHERE global_role IN ('GLOBAL_MODERATOR', 'ADMIN');

-- 2. Tabla de Sanciones y Medidas Administrativas
CREATE TABLE IF NOT EXISTS replica.account_sanction (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    target_account_id uuid NOT NULL REFERENCES replica.access_account(id) ON DELETE RESTRICT,
    moderator_account_id uuid NOT NULL REFERENCES replica.access_account(id) ON DELETE RESTRICT,
    action text NOT NULL CHECK (action IN (
        'WARNING',
        'FORMAL_WARNING',
        'TEMPORARY_SUSPENSION',
        'EXTENDED_SUSPENSION',
        'ACCOUNT_DEACTIVATED'
    )),
    reason text NOT NULL CHECK (char_length(btrim(reason)) BETWEEN 5 AND 2000),
    evidence_context text CHECK (evidence_context IS NULL OR char_length(evidence_context) <= 4000),
    expires_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    active boolean NOT NULL DEFAULT true,
    appeal_status text NOT NULL DEFAULT 'NONE' CHECK (appeal_status IN ('NONE', 'REQUESTED', 'REVIEWED', 'REJECTED', 'ACCEPTED')),
    appeal_note text CHECK (appeal_note IS NULL OR char_length(appeal_note) <= 2000),
    appeal_resolved_by uuid REFERENCES replica.access_account(id) ON DELETE RESTRICT,
    appeal_resolved_at timestamptz
);

CREATE INDEX IF NOT EXISTS sanction_by_target ON replica.account_sanction(target_account_id, created_at DESC);
CREATE INDEX IF NOT EXISTS sanction_by_active ON replica.account_sanction(target_account_id) WHERE active;

-- 3. Bitácora de Auditoría de Moderación Global (Inmutable)
CREATE TABLE IF NOT EXISTS replica.global_moderation_audit_log (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    moderator_account_id uuid NOT NULL REFERENCES replica.access_account(id) ON DELETE RESTRICT,
    target_account_id uuid REFERENCES replica.access_account(id) ON DELETE RESTRICT,
    target_content_type text CHECK (target_content_type IS NULL OR target_content_type IN ('POST', 'COMMENT', 'COMMUNITY', 'ACCOUNT', 'RESOURCE')),
    target_content_id uuid,
    action text NOT NULL CHECK (char_length(btrim(action)) BETWEEN 2 AND 100),
    reason text NOT NULL CHECK (char_length(btrim(reason)) BETWEEN 5 AND 2000),
    metadata jsonb,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX IF NOT EXISTS audit_log_chronology ON replica.global_moderation_audit_log(created_at DESC);

-- 4. Organizador Académico: Tareas y Proyectos
CREATE TABLE IF NOT EXISTS replica.academic_task (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id uuid NOT NULL REFERENCES replica.access_account(id) ON DELETE RESTRICT,
    community_id uuid REFERENCES replica.community(id) ON DELETE SET NULL,
    title text NOT NULL CHECK (char_length(btrim(title)) BETWEEN 2 AND 160),
    description text CHECK (description IS NULL OR char_length(description) <= 3000),
    status text NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'IN_PROGRESS', 'IN_REVIEW', 'COMPLETED')),
    priority text NOT NULL DEFAULT 'MEDIUM' CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    due_date timestamptz,
    project_name text CHECK (project_name IS NULL OR char_length(project_name) <= 100),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at timestamptz NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX IF NOT EXISTS task_by_account_due ON replica.academic_task(account_id, due_date ASC);
CREATE INDEX IF NOT EXISTS task_by_status ON replica.academic_task(account_id, status);

-- 5. Equipos de Estudio y Afinidades
CREATE TABLE IF NOT EXISTS replica.study_profile (
    account_id uuid PRIMARY KEY REFERENCES replica.access_account(id) ON DELETE RESTRICT,
    subjects text[] NOT NULL DEFAULT '{}',
    interests text[] NOT NULL DEFAULT '{}',
    can_help_with text CHECK (can_help_with IS NULL OR char_length(can_help_with) <= 1000),
    needs_help_with text CHECK (needs_help_with IS NULL OR char_length(needs_help_with) <= 1000),
    availability text CHECK (availability IS NULL OR char_length(availability) <= 300),
    visible boolean NOT NULL DEFAULT true,
    updated_at timestamptz NOT NULL DEFAULT clock_timestamp()
);

-- 6. Oportunidades Campus (Becas, Pasantías, Concursos, Eventos)
CREATE TABLE IF NOT EXISTS replica.campus_opportunity (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    title text NOT NULL CHECK (char_length(btrim(title)) BETWEEN 3 AND 160),
    description text NOT NULL CHECK (char_length(btrim(description)) BETWEEN 10 AND 5000),
    category text NOT NULL CHECK (category IN (
        'SCHOLARSHIP', 'INTERNSHIP', 'COURSE', 'CONTEST', 'CALL', 'VOLUNTEER', 'EVENT', 'JOB'
    )),
    source_institution text NOT NULL CHECK (char_length(btrim(source_institution)) BETWEEN 2 AND 160),
    source_url text CHECK (source_url IS NULL OR source_url ~ '^https?://[^\s/$.?#].[^\s]*$'),
    deadline timestamptz,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX IF NOT EXISTS opportunity_by_category ON replica.campus_opportunity(category, created_at DESC) WHERE active;

-- 7. Intercambio Universitario de Libros y Materiales (Sin Pagos)
CREATE TABLE IF NOT EXISTS replica.campus_exchange_item (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_account_id uuid NOT NULL REFERENCES replica.access_account(id) ON DELETE RESTRICT,
    community_id uuid REFERENCES replica.community(id) ON DELETE SET NULL,
    title text NOT NULL CHECK (char_length(btrim(title)) BETWEEN 3 AND 160),
    description text NOT NULL CHECK (char_length(btrim(description)) BETWEEN 5 AND 3000),
    category text NOT NULL CHECK (category IN (
        'BOOK', 'CALCULATOR', 'LAB_EQUIPMENT', 'NOTES', 'TOOL', 'OTHER'
    )),
    exchange_type text NOT NULL CHECK (exchange_type IN ('GIFT', 'LOAN', 'EXCHANGE')),
    status text NOT NULL DEFAULT 'AVAILABLE' CHECK (status IN ('AVAILABLE', 'RESERVED', 'COMPLETED', 'REPORTED')),
    contact_info text NOT NULL CHECK (char_length(btrim(contact_info)) BETWEEN 3 AND 300),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX IF NOT EXISTS exchange_by_category ON replica.campus_exchange_item(category, status) WHERE status = 'AVAILABLE';

-- RLS y Permisos
ALTER TABLE replica.account_sanction ENABLE ROW LEVEL SECURITY;
ALTER TABLE replica.global_moderation_audit_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE replica.academic_task ENABLE ROW LEVEL SECURITY;
ALTER TABLE replica.study_profile ENABLE ROW LEVEL SECURITY;
ALTER TABLE replica.campus_opportunity ENABLE ROW LEVEL SECURITY;
ALTER TABLE replica.campus_exchange_item ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON
    replica.account_sanction,
    replica.global_moderation_audit_log,
    replica.academic_task,
    replica.study_profile,
    replica.campus_opportunity,
    replica.campus_exchange_item
FROM PUBLIC, replica_app, replica_academic;

-- 8. Funciones PL/pgSQL: Verificación de Moderador Global
CREATE OR REPLACE FUNCTION replica.require_global_moderator(p_actor uuid) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    PERFORM 1 FROM replica.access_account a
    WHERE a.id = p_actor AND a.enabled AND a.global_role IN ('GLOBAL_MODERATOR', 'ADMIN')
    FOR SHARE;
    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1404', MESSAGE = 'global_moderation_unauthorized';
    END IF;
END;
$$;

-- 9. Procedimiento de Asignación Controlada de Moderador Global (Solo Administrador)
-- Limita estrictamente a máximo 4 moderadores globales activos
CREATE OR REPLACE FUNCTION replica.assign_global_moderator(p_admin uuid, p_target_login text)
RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
DECLARE
    target_id uuid;
    active_mods integer;
BEGIN
    -- Verificar que p_admin sea ADMIN global
    PERFORM 1 FROM replica.access_account a
    WHERE a.id = p_admin AND a.enabled AND a.global_role = 'ADMIN'
    FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1404', MESSAGE = 'admin_unauthorized';
    END IF;

    -- Verificar límite de 4 moderadores globales
    SELECT count(*) INTO active_mods FROM replica.access_account
    WHERE global_role = 'GLOBAL_MODERATOR' AND enabled;

    IF active_mods >= 4 THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'global_moderator_limit_reached';
    END IF;

    SELECT id INTO target_id FROM replica.access_account
    WHERE login_identifier = lower(btrim(p_target_login)) AND enabled;

    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1404', MESSAGE = 'target_account_not_found';
    END IF;

    UPDATE replica.access_account
    SET global_role = 'GLOBAL_MODERATOR'
    WHERE id = target_id;

    INSERT INTO replica.global_moderation_audit_log(
        moderator_account_id, target_account_id, action, reason
    )
    VALUES (
        p_admin, target_id, 'ASSIGN_GLOBAL_MODERATOR',
        'Asignación administrativa de moderador general autorizado'
    );
END;
$$;

-- 10. Consulta de Estudiantes para el Control Center
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

    IF p_limit IS NULL OR p_limit NOT BETWEEN 1 AND 50 THEN
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

-- 11. Aplicación de Sanción desde Control Center
CREATE OR REPLACE FUNCTION replica.control_center_apply_sanction(
    p_actor uuid,
    p_target uuid,
    p_action text,
    p_reason text,
    p_evidence text,
    p_duration_hours integer
)
RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
DECLARE
    new_sanction_id uuid;
    clean_action text;
    calc_expires timestamptz;
BEGIN
    PERFORM replica.require_global_moderator(p_actor);

    clean_action := upper(btrim(p_action));
    IF clean_action NOT IN ('WARNING', 'FORMAL_WARNING', 'TEMPORARY_SUSPENSION', 'EXTENDED_SUSPENSION', 'ACCOUNT_DEACTIVATED') THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_action';
    END IF;

    IF char_length(btrim(p_reason)) < 5 THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'reason_too_short';
    END IF;

    IF p_duration_hours IS NOT NULL AND p_duration_hours > 0 THEN
        calc_expires := clock_timestamp() + (p_duration_hours || ' hours')::interval;
    END IF;

    INSERT INTO replica.account_sanction (
        target_account_id, moderator_account_id, action, reason, evidence_context, expires_at
    )
    VALUES (
        p_target, p_actor, clean_action, btrim(p_reason), p_evidence, calc_expires
    )
    RETURNING id INTO new_sanction_id;

    -- Si la acción es desactivación, deshabilitar la cuenta
    IF clean_action = 'ACCOUNT_DEACTIVATED' THEN
        UPDATE replica.access_account SET enabled = false WHERE id = p_target;
    END IF;

    -- Registro de auditoría inmutable
    INSERT INTO replica.global_moderation_audit_log (
        moderator_account_id, target_account_id, action, reason
    )
    VALUES (
        p_actor, p_target, 'SANCTION_' || clean_action, btrim(p_reason)
    );

    RETURN new_sanction_id;
END;
$$;

-- 12. Listar Auditoría Global
CREATE OR REPLACE FUNCTION replica.control_center_audit_logs(
    p_actor uuid,
    p_limit integer
)
RETURNS TABLE (
    id uuid,
    moderator_alias text,
    target_login text,
    action text,
    reason text,
    created_at timestamptz
)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    PERFORM replica.require_global_moderator(p_actor);

    IF p_limit IS NULL OR p_limit NOT BETWEEN 1 AND 100 THEN
        p_limit := 50;
    END IF;

    RETURN QUERY
    SELECT l.id, ap_mod.alias, a_tgt.login_identifier, l.action, l.reason, l.created_at
    FROM replica.global_moderation_audit_log l
    JOIN replica.account_profile ap_mod ON ap_mod.account_id = l.moderator_account_id
    LEFT JOIN replica.access_account a_tgt ON a_tgt.id = l.target_account_id
    ORDER BY l.created_at DESC
    LIMIT p_limit;
END;
$$;

-- Grants para ${backendLogin}
REVOKE ALL ON FUNCTION
    replica.require_global_moderator(uuid),
    replica.assign_global_moderator(uuid, text),
    replica.control_center_search_students(uuid, text, integer),
    replica.control_center_apply_sanction(uuid, uuid, text, text, text, integer),
    replica.control_center_audit_logs(uuid, integer)
FROM PUBLIC;

GRANT EXECUTE ON FUNCTION
    replica.require_global_moderator(uuid),
    replica.assign_global_moderator(uuid, text),
    replica.control_center_search_students(uuid, text, integer),
    replica.control_center_apply_sanction(uuid, uuid, text, text, text, integer),
    replica.control_center_audit_logs(uuid, integer)
TO "${backendLogin}";
