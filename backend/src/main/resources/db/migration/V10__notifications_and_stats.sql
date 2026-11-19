-- V10: Centro de Notificaciones y Estadísticas Agregadas
-- SteamBeta Campus: avisos internos y métricas comunitarias respetando la privacidad

CREATE TABLE IF NOT EXISTS replica.account_notification (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id uuid NOT NULL REFERENCES replica.access_account(id) ON DELETE RESTRICT,
    community_id uuid REFERENCES replica.community(id) ON DELETE SET NULL,
    source_actor_alias text,
    type text NOT NULL CHECK (type IN ('COMMENT_REPLY', 'QUESTION_ANSWERED', 'ANSWER_ACCEPTED', 'COMMUNITY_INVITE', 'MODERATION_ALERT', 'EVENT_REMINDER')),
    title text NOT NULL CHECK (char_length(btrim(title)) BETWEEN 2 AND 160),
    message text NOT NULL CHECK (char_length(btrim(message)) BETWEEN 1 AND 500),
    read boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX IF NOT EXISTS notification_by_account ON replica.account_notification (account_id, created_at DESC);
CREATE INDEX IF NOT EXISTS notification_unread ON replica.account_notification (account_id) WHERE NOT read;

ALTER TABLE replica.account_notification ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON replica.account_notification FROM PUBLIC, replica_app, replica_academic;

-- Obtener notificaciones del usuario
CREATE OR REPLACE FUNCTION replica.my_notifications(
    p_actor uuid,
    p_limit integer
)
RETURNS TABLE (
    id uuid,
    community_id uuid,
    source_actor_alias text,
    type text,
    title text,
    message text,
    read boolean,
    created_at timestamptz
)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    PERFORM 1 FROM replica.access_account a WHERE a.id = p_actor AND a.enabled FOR SHARE;
    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1401', MESSAGE = 'account_unavailable';
    END IF;

    IF p_limit IS NULL OR p_limit NOT BETWEEN 1 AND 50 THEN
        p_limit := 20;
    END IF;

    RETURN QUERY
    SELECT n.id, n.community_id, n.source_actor_alias, n.type,
           n.title, n.message, n.read, n.created_at
    FROM replica.account_notification n
    WHERE n.account_id = p_actor
    ORDER BY n.created_at DESC
    LIMIT p_limit;
END;
$$;

-- Marcar notificación como leída
CREATE OR REPLACE FUNCTION replica.mark_notification_read(
    p_actor uuid,
    p_notification uuid
)
RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    UPDATE replica.account_notification
    SET read = true
    WHERE id = p_notification AND account_id = p_actor;
END;
$$;

-- Contar notificaciones no leídas
CREATE OR REPLACE FUNCTION replica.unread_notifications_count(p_actor uuid)
RETURNS bigint
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    RETURN (SELECT count(*) FROM replica.account_notification
            WHERE account_id = p_actor AND NOT read);
END;
$$;

-- Estadísticas agregadas de comunidad (solo para ADMIN/MODERATOR o miembros)
-- No expone actividad individual ni sirve como vigilancia
CREATE OR REPLACE FUNCTION replica.community_aggregate_stats(
    p_actor uuid,
    p_community uuid
)
RETURNS TABLE (
    community_id uuid,
    total_members bigint,
    total_posts bigint,
    total_questions bigint,
    resolved_questions bigint,
    total_resources bigint,
    upcoming_events bigint
)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    PERFORM replica.require_community_member(p_actor, p_community);

    RETURN QUERY
    SELECT p_community,
           (SELECT count(*) FROM replica.community_membership WHERE community_id = p_community AND active),
           (SELECT count(*) FROM replica.community_post WHERE community_id = p_community AND post_type = 'POST' AND status = 'ACTIVE'),
           (SELECT count(*) FROM replica.community_post WHERE community_id = p_community AND post_type = 'QUESTION' AND status = 'ACTIVE'),
           (SELECT count(*) FROM replica.community_post WHERE community_id = p_community AND post_type = 'QUESTION' AND status = 'ACTIVE' AND resolved_comment_id IS NOT NULL),
           (SELECT count(*) FROM replica.community_resource WHERE community_id = p_community AND active),
           (SELECT count(*) FROM replica.community_event WHERE community_id = p_community AND status = 'SCHEDULED' AND event_date >= clock_timestamp());
END;
$$;

REVOKE ALL ON FUNCTION
    replica.my_notifications(uuid, integer),
    replica.mark_notification_read(uuid, uuid),
    replica.unread_notifications_count(uuid),
    replica.community_aggregate_stats(uuid, uuid)
FROM PUBLIC;

GRANT EXECUTE ON FUNCTION
    replica.my_notifications(uuid, integer),
    replica.mark_notification_read(uuid, uuid),
    replica.unread_notifications_count(uuid),
    replica.community_aggregate_stats(uuid, uuid)
TO "${backendLogin}";
