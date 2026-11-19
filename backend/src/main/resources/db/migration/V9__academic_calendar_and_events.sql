-- V9: Calendario y Organización Estudiantil
-- SteamBeta Campus: exámenes, entregas, talleres y recordatorios por comunidad

CREATE TABLE IF NOT EXISTS replica.community_event (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    community_id uuid NOT NULL REFERENCES replica.community(id) ON DELETE RESTRICT,
    creator_account_id uuid NOT NULL REFERENCES replica.access_account(id) ON DELETE RESTRICT,
    title text NOT NULL CHECK (char_length(btrim(title)) BETWEEN 3 AND 160),
    description text CHECK (description IS NULL OR char_length(description) <= 2000),
    event_type text NOT NULL CHECK (event_type IN ('EXAM', 'DELIVERY', 'WORKSHOP', 'MEETING', 'REMINDER')),
    event_date timestamptz NOT NULL,
    status text NOT NULL DEFAULT 'SCHEDULED' CHECK (status IN ('SCHEDULED', 'CANCELLED', 'COMPLETED')),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX IF NOT EXISTS event_by_community_date ON replica.community_event (community_id, event_date ASC);

ALTER TABLE replica.community_event ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON replica.community_event FROM PUBLIC, replica_app, replica_academic;

-- Crear evento en la comunidad (MEMBER, MODERATOR o ADMIN)
CREATE OR REPLACE FUNCTION replica.create_community_event(
    p_actor uuid,
    p_community uuid,
    p_title text,
    p_description text,
    p_event_type text,
    p_event_date timestamptz
)
RETURNS TABLE (
    id uuid,
    community_id uuid,
    creator_alias text,
    title text,
    description text,
    event_type text,
    event_date timestamptz,
    status text,
    created_at timestamptz
)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
DECLARE
    new_evt replica.community_event%ROWTYPE;
    clean_title text;
    clean_desc text;
    clean_type text;
BEGIN
    PERFORM replica.require_community_member(p_actor, p_community);

    clean_title := btrim(p_title);
    clean_desc := NULLIF(btrim(p_description), '');
    clean_type := upper(btrim(p_event_type));

    IF char_length(clean_title) NOT BETWEEN 3 AND 160 THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_title';
    END IF;

    IF clean_type NOT IN ('EXAM', 'DELIVERY', 'WORKSHOP', 'MEETING', 'REMINDER') THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_event_type';
    END IF;

    IF p_event_date IS NULL THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_event_date';
    END IF;

    INSERT INTO replica.community_event (
        community_id, creator_account_id, title, description, event_type, event_date
    )
    VALUES (
        p_community, p_actor, clean_title, clean_desc, clean_type, p_event_date
    )
    RETURNING * INTO new_evt;

    RETURN QUERY
    SELECT new_evt.id, new_evt.community_id, a.alias, new_evt.title, new_evt.description,
           new_evt.event_type, new_evt.event_date, new_evt.status, new_evt.created_at
    FROM replica.account_profile a WHERE a.account_id = p_actor;
END;
$$;

-- Listar eventos de la comunidad
CREATE OR REPLACE FUNCTION replica.community_events(
    p_actor uuid,
    p_community uuid,
    p_from_date timestamptz,
    p_to_date timestamptz
)
RETURNS TABLE (
    id uuid,
    community_id uuid,
    creator_account_id uuid,
    creator_alias text,
    title text,
    description text,
    event_type text,
    event_date timestamptz,
    status text,
    created_at timestamptz
)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    PERFORM replica.require_community_member(p_actor, p_community);

    RETURN QUERY
    SELECT e.id, e.community_id, e.creator_account_id, a.alias,
           e.title, e.description, e.event_type, e.event_date, e.status, e.created_at
    FROM replica.community_event e
    JOIN replica.account_profile a ON a.account_id = e.creator_account_id
    WHERE e.community_id = p_community AND e.status = 'SCHEDULED'
      AND (p_from_date IS NULL OR e.event_date >= p_from_date)
      AND (p_to_date IS NULL OR e.event_date <= p_to_date)
    ORDER BY e.event_date ASC;
END;
$$;

-- Próximas actividades globales del estudiante (de todas sus comunidades)
CREATE OR REPLACE FUNCTION replica.my_upcoming_events(
    p_actor uuid,
    p_limit integer
)
RETURNS TABLE (
    id uuid,
    community_id uuid,
    community_name text,
    title text,
    description text,
    event_type text,
    event_date timestamptz,
    status text
)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    PERFORM 1 FROM replica.access_account a WHERE a.id = p_actor AND a.enabled FOR SHARE;
    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1401', MESSAGE = 'account_unavailable';
    END IF;

    IF p_limit IS NULL OR p_limit NOT BETWEEN 1 AND 20 THEN
        p_limit := 10;
    END IF;

    RETURN QUERY
    SELECT e.id, e.community_id, c.name, e.title, e.description, e.event_type, e.event_date, e.status
    FROM replica.community_event e
    JOIN replica.community c ON c.id = e.community_id
    JOIN replica.community_membership m ON m.community_id = c.id
    WHERE m.account_id = p_actor AND m.active AND c.active
      AND e.status = 'SCHEDULED'
      AND e.event_date >= clock_timestamp() - interval '2 hours'
    ORDER BY e.event_date ASC
    LIMIT p_limit;
END;
$$;

REVOKE ALL ON FUNCTION
    replica.create_community_event(uuid, uuid, text, text, text, timestamptz),
    replica.community_events(uuid, uuid, timestamptz, timestamptz),
    replica.my_upcoming_events(uuid, integer)
FROM PUBLIC;

GRANT EXECUTE ON FUNCTION
    replica.create_community_event(uuid, uuid, text, text, text, timestamptz),
    replica.community_events(uuid, uuid, timestamptz, timestamptz),
    replica.my_upcoming_events(uuid, integer)
TO "${backendLogin}";
