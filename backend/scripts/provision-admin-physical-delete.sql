-- ============================================================================
-- SteamBeta Campus — Script de mantenimiento manual (fuera de Flyway)
-- Propósito: Aprovisionar el borrado físico real de cuentas estudiantiles con
--            conservación de aportes académicos bajo la identidad técnica
--            anónima 'steambeta.content.anonymous' (alias: 'Usuario eliminado').
--
-- PRERREQUISITO:
--   Ejecutar previamente backend/scripts/provision-anonymous-content-actor.ps1
--   para asegurar la existencia de 'steambeta.content.anonymous'.
--
-- SEGURIDAD:
-- - SECURITY DEFINER, SET search_path = pg_catalog, pg_temp, owner replica_migrator.
-- - REVOKE ALL FROM PUBLIC; GRANT EXECUTE únicamente a replica_runtime.
-- - NO modifica tablas, columnas, constraints, RLS ni migraciones V1–V17.
-- - Comprueba dinámicamente en pg_catalog que no quede ninguna FK apuntando al
--   UUID original antes del DELETE físico; si existe cualquier referencia no
--   contemplada, aborta y revierte toda la transacción.
-- - Aplicar también en cualquier entorno reconstruido desde cero a partir de V1–V17.
-- ============================================================================

BEGIN;

DROP FUNCTION IF EXISTS replica.admin_anonymize_student_account(uuid, uuid, text, text);

CREATE OR REPLACE FUNCTION replica.admin_preview_student_removal(
    p_actor uuid,
    p_target uuid
)
RETURNS TABLE (
    account_id uuid,
    alias text,
    login_identifier text,
    email text,
    created_at timestamptz,
    tasks_count bigint,
    manual_subjects_count bigint,
    study_profile_exists boolean,
    notifications_count bigint,
    email_tokens_count bigint,
    exchange_items_count bigint,
    exchange_attachments_count bigint,
    has_avatar boolean,
    communities_count bigint,
    posts_count bigint,
    questions_count bigint,
    comments_count bigint,
    resources_count bigint,
    opportunities_count bigint,
    events_count bigint,
    sanctions_count bigint,
    audit_logs_count bigint
)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
DECLARE
    v_role text;
    v_login text;
    v_prev_actor text;
    v_manual_subjects bigint;
BEGIN
    PERFORM replica.require_global_moderator(p_actor);

    IF p_target IS NULL OR p_target = p_actor THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_target_account';
    END IF;

    SELECT a.global_role, a.login_identifier INTO v_role, v_login
    FROM replica.access_account a
    WHERE a.id = p_target;

    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1404', MESSAGE = 'student_not_found';
    END IF;

    IF v_role <> 'USER'
       OR v_login IN ('steambeta.admin.system', 'steambeta.content.anonymous') THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'cannot_delete_protected_account';
    END IF;

    -- student_manual_subject usa FORCE ROW LEVEL SECURITY con replica.current_actor_id
    v_prev_actor := pg_catalog.current_setting('replica.current_actor_id', true);
    PERFORM pg_catalog.set_config('replica.current_actor_id', p_target::text, true);
    SELECT count(*) INTO v_manual_subjects FROM replica.student_manual_subject ms WHERE ms.account_id = p_target;
    PERFORM pg_catalog.set_config('replica.current_actor_id', COALESCE(v_prev_actor, ''), true);

    RETURN QUERY
    SELECT
        a.id,
        ap.alias,
        a.login_identifier,
        a.email,
        a.created_at,
        (SELECT count(*) FROM replica.academic_task t WHERE t.account_id = a.id),
        v_manual_subjects,
        EXISTS(SELECT 1 FROM replica.study_profile sp WHERE sp.account_id = a.id),
        (SELECT count(*) FROM replica.account_notification n WHERE n.account_id = a.id),
        (SELECT count(*) FROM replica.email_verification_token et WHERE et.account_id = a.id),
        (SELECT count(*) FROM replica.campus_exchange_item ei WHERE ei.owner_account_id = a.id),
        (SELECT count(*) FROM replica.campus_exchange_attachment ea
            WHERE ea.uploader_account_id = a.id
               OR ea.exchange_item_id IN (SELECT id FROM replica.campus_exchange_item WHERE owner_account_id = a.id)),
        (ap.avatar_url IS NOT NULL AND btrim(ap.avatar_url) <> ''),
        (SELECT count(*) FROM replica.community_membership cm WHERE cm.account_id = a.id AND cm.active),
        (SELECT count(*) FROM replica.community_post cp WHERE cp.author_account_id = a.id AND coalesce(cp.post_type, 'POST') <> 'QUESTION'),
        (SELECT count(*) FROM replica.community_post cp WHERE cp.author_account_id = a.id AND cp.post_type = 'QUESTION'),
        (SELECT count(*) FROM replica.community_comment cc WHERE cc.author_account_id = a.id),
        (SELECT count(*) FROM replica.community_resource cr WHERE cr.uploader_account_id = a.id AND cr.active),
        (SELECT count(*) FROM replica.campus_opportunity co WHERE co.owner_account_id = a.id),
        (SELECT count(*) FROM replica.community_event ce WHERE ce.creator_account_id = a.id),
        (SELECT count(*) FROM replica.account_sanction s WHERE s.target_account_id = a.id),
        (SELECT count(*) FROM replica.global_moderation_audit_log l WHERE l.target_account_id = a.id OR l.moderator_account_id = a.id)
    FROM replica.access_account a
    JOIN replica.account_profile ap ON ap.account_id = a.id
    WHERE a.id = p_target;
END;
$$;

CREATE OR REPLACE FUNCTION replica.admin_delete_student_account(
    p_actor uuid,
    p_target uuid,
    p_reason text
)
RETURNS TABLE (
    deleted_account_id uuid,
    anonymous_actor_id uuid,
    removed_avatar_url text,
    removed_exchange_storage_keys text[]
)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
DECLARE
    v_role text;
    v_login text;
    v_anon_id uuid;
    v_avatar text;
    v_exch_keys text[];
    v_clean_reason text;
    v_fk record;
    v_has_ref boolean;
    v_still_exists boolean;
    v_prev_actor text;
BEGIN
    -- 1. Validar actor administrativo
    PERFORM replica.require_global_moderator(p_actor);

    IF p_target IS NULL OR p_target = p_actor THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_target_account';
    END IF;

    v_clean_reason := COALESCE(NULLIF(btrim(p_reason), ''), 'Eliminación física controlada desde STEAMBETA ADMIN');
    IF char_length(v_clean_reason) < 5 OR char_length(v_clean_reason) > 2000 THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_reason_length';
    END IF;

    -- 2. Bloquear y validar la cuenta objetivo
    SELECT a.global_role, a.login_identifier INTO v_role, v_login
    FROM replica.access_account a
    WHERE a.id = p_target
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1404', MESSAGE = 'student_not_found';
    END IF;

    IF v_role <> 'USER'
       OR v_login IN ('steambeta.admin.system', 'steambeta.content.anonymous') THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'cannot_delete_protected_account';
    END IF;

    -- 3. Verificar que existe la identidad técnica anónima reservada
    SELECT a.id INTO v_anon_id
    FROM replica.access_account a
    JOIN replica.account_profile ap ON ap.account_id = a.id
    WHERE a.login_identifier = 'steambeta.content.anonymous'
      AND a.enabled = false
      AND a.global_role = 'USER'
      AND ap.alias = 'Usuario eliminado'
    FOR SHARE;

    IF NOT FOUND OR v_anon_id IS NULL THEN
        RAISE EXCEPTION USING ERRCODE = 'P1500', MESSAGE = 'anonymous_content_actor_missing';
    END IF;

    -- 4. Recopilar referencias de almacenamiento externo personal (NUNCA community_resource)
    SELECT ap.avatar_url INTO v_avatar
    FROM replica.account_profile ap
    WHERE ap.account_id = p_target;

    SELECT COALESCE(array_agg(ea.storage_key), ARRAY[]::text[]) INTO v_exch_keys
    FROM replica.campus_exchange_attachment ea
    WHERE ea.uploader_account_id = p_target
       OR ea.exchange_item_id IN (SELECT id FROM replica.campus_exchange_item WHERE owner_account_id = p_target);

    -- 5. Eliminar datos estrictamente personales
    DELETE FROM replica.email_verification_token WHERE account_id = p_target;
    DELETE FROM replica.academic_task WHERE account_id = p_target;

    -- student_manual_subject aplica FORCE ROW LEVEL SECURITY filtrando por replica.current_actor_id
    v_prev_actor := pg_catalog.current_setting('replica.current_actor_id', true);
    PERFORM pg_catalog.set_config('replica.current_actor_id', p_target::text, true);
    DELETE FROM replica.student_manual_subject WHERE account_id = p_target;
    PERFORM pg_catalog.set_config('replica.current_actor_id', COALESCE(v_prev_actor, ''), true);

    DELETE FROM replica.study_profile WHERE account_id = p_target;
    DELETE FROM replica.account_notification WHERE account_id = p_target;
    DELETE FROM replica.campus_exchange_attachment
    WHERE uploader_account_id = p_target
       OR exchange_item_id IN (SELECT id FROM replica.campus_exchange_item WHERE owner_account_id = p_target);
    DELETE FROM replica.campus_exchange_item WHERE owner_account_id = p_target;

    -- 6. Reasignar aportes compartidos y resolver restricciones únicas
    -- 6a. Publicaciones y Q&A: regenerar request_key para evitar colisión UNIQUE (community_id, author_account_id, request_key)
    UPDATE replica.community_post
    SET author_account_id = v_anon_id,
        request_key = gen_random_uuid()
    WHERE author_account_id = p_target;

    -- 6b. Comentarios, recursos comunitarios (conservando archivos), eventos
    UPDATE replica.community_comment
    SET author_account_id = v_anon_id
    WHERE author_account_id = p_target;

    UPDATE replica.community_resource
    SET uploader_account_id = v_anon_id
    WHERE uploader_account_id = p_target;

    UPDATE replica.community_event
    SET creator_account_id = v_anon_id
    WHERE creator_account_id = p_target;

    -- 6c. Invitaciones: reasignar creador y revocar las que sigan activas
    UPDATE replica.community_invitation
    SET creator_account_id = v_anon_id,
        revoked = true
    WHERE creator_account_id = p_target;

    -- 6d. Reportes y eventos de moderación comunitaria (respetando NULL en resolved_by)
    UPDATE replica.community_report
    SET reporter_account_id = v_anon_id
    WHERE reporter_account_id = p_target;

    UPDATE replica.community_report
    SET resolved_by = v_anon_id
    WHERE resolved_by = p_target;

    UPDATE replica.community_moderation_event
    SET actor_account_id = v_anon_id
    WHERE actor_account_id = p_target;

    -- 6e. Oportunidades campus (respetando NULL en owner_account_id)
    UPDATE replica.campus_opportunity
    SET owner_account_id = v_anon_id
    WHERE owner_account_id = p_target;

    -- 7. Resolver membresías comunitarias (PK compuesta (community_id, account_id))
    -- Si la identidad anónima ya tiene membresía en la misma comunidad, desactivarla y borrar la duplicada;
    -- si no, reasignar la membresía como inactiva con rol MEMBER.
    UPDATE replica.community_membership m_anon
    SET active = false
    WHERE m_anon.account_id = v_anon_id
      AND EXISTS (
          SELECT 1 FROM replica.community_membership m_tgt
          WHERE m_tgt.account_id = p_target AND m_tgt.community_id = m_anon.community_id
      );

    DELETE FROM replica.community_membership m_tgt
    WHERE m_tgt.account_id = p_target
      AND EXISTS (
          SELECT 1 FROM replica.community_membership m_anon
          WHERE m_anon.account_id = v_anon_id AND m_anon.community_id = m_tgt.community_id
      );

    UPDATE replica.community_membership
    SET account_id = v_anon_id,
        active = false,
        role = 'MEMBER'
    WHERE account_id = p_target;

    -- 8. Reasignar referencias históricas en sanciones y auditoría global (sin borrar registros)
    UPDATE replica.account_sanction
    SET target_account_id = v_anon_id
    WHERE target_account_id = p_target;

    UPDATE replica.account_sanction
    SET moderator_account_id = v_anon_id
    WHERE moderator_account_id = p_target;

    UPDATE replica.account_sanction
    SET appeal_resolved_by = v_anon_id
    WHERE appeal_resolved_by = p_target;

    UPDATE replica.global_moderation_audit_log
    SET target_account_id = v_anon_id
    WHERE target_account_id = p_target;

    UPDATE replica.global_moderation_audit_log
    SET moderator_account_id = v_anon_id
    WHERE moderator_account_id = p_target;

    -- 9. Eliminar perfil personal original
    DELETE FROM replica.account_profile WHERE account_id = p_target;

    -- 10. Comprobación dinámica en pg_catalog: ninguna tabla puede seguir referenciando p_target
    v_prev_actor := pg_catalog.current_setting('replica.current_actor_id', true);
    PERFORM pg_catalog.set_config('replica.current_actor_id', p_target::text, true);
    FOR v_fk IN
        SELECT n.nspname AS schema_name,
               cl.relname AS table_name,
               att.attname AS col_name
        FROM pg_catalog.pg_constraint c
        JOIN pg_catalog.pg_class cl ON cl.oid = c.conrelid
        JOIN pg_catalog.pg_namespace n ON n.oid = cl.relnamespace
        JOIN pg_catalog.pg_attribute att ON att.attrelid = c.conrelid AND att.attnum = c.conkey[1]
        WHERE c.contype = 'f'
          AND c.confrelid = 'replica.access_account'::regclass
    LOOP
        EXECUTE format(
            'SELECT EXISTS (SELECT 1 FROM %I.%I WHERE %I = $1)',
            v_fk.schema_name, v_fk.table_name, v_fk.col_name
        ) INTO v_has_ref USING p_target;

        IF v_has_ref THEN
            PERFORM pg_catalog.set_config('replica.current_actor_id', COALESCE(v_prev_actor, ''), true);
            RAISE EXCEPTION USING ERRCODE = '23503',
                MESSAGE = format('remaining_fk_reference_found:%I.%I.%I', v_fk.schema_name, v_fk.table_name, v_fk.col_name);
        END IF;
    END LOOP;
    PERFORM pg_catalog.set_config('replica.current_actor_id', COALESCE(v_prev_actor, ''), true);

    -- 11. Registrar auditoría institucional de eliminación física usando la identidad anónima como destino
    INSERT INTO replica.global_moderation_audit_log (
        moderator_account_id, target_account_id, target_content_type, action, reason
    )
    VALUES (
        p_actor, v_anon_id, 'ACCOUNT', 'STUDENT_ACCOUNT_PHYSICALLY_DELETED', v_clean_reason
    );

    -- 12. DELETE físico real de la cuenta original en replica.access_account
    DELETE FROM replica.access_account WHERE id = p_target;

    -- 13. Verificar dentro de la transacción que el UUID original ya no existe
    SELECT EXISTS(SELECT 1 FROM replica.access_account WHERE id = p_target) INTO v_still_exists;
    IF v_still_exists THEN
        RAISE EXCEPTION USING ERRCODE = 'P1500', MESSAGE = 'physical_delete_verification_failed';
    END IF;

    -- 14. Devolver datos mínimos para limpieza post-commit de archivos personales
    RETURN QUERY SELECT p_target, v_anon_id, v_avatar, v_exch_keys;
END;
$$;

REVOKE ALL ON FUNCTION
    replica.admin_preview_student_removal(uuid, uuid),
    replica.admin_delete_student_account(uuid, uuid, text)
FROM PUBLIC;

GRANT EXECUTE ON FUNCTION
    replica.admin_preview_student_removal(uuid, uuid),
    replica.admin_delete_student_account(uuid, uuid, text)
TO replica_runtime;

COMMIT;
