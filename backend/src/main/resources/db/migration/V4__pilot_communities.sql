CREATE TABLE replica.community (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    slug text NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9][a-z0-9-]{2,79}$'),
    name text NOT NULL CHECK (char_length(btrim(name)) BETWEEN 3 AND 100),
    description text NOT NULL CHECK (char_length(description) <= 1000),
    rules text NOT NULL CHECK (char_length(btrim(rules)) BETWEEN 1 AND 3000),
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE replica.community_membership (
    community_id uuid NOT NULL REFERENCES replica.community (id) ON DELETE RESTRICT,
    account_id uuid NOT NULL REFERENCES replica.access_account (id) ON DELETE RESTRICT,
    role text NOT NULL DEFAULT 'MEMBER' CHECK (role IN ('MEMBER', 'MODERATOR', 'ADMIN')),
    active boolean NOT NULL DEFAULT true,
    joined_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (community_id, account_id)
);
CREATE INDEX membership_by_account ON replica.community_membership (account_id, community_id);

CREATE TABLE replica.community_post (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    community_id uuid NOT NULL REFERENCES replica.community (id) ON DELETE RESTRICT,
    author_account_id uuid NOT NULL REFERENCES replica.access_account (id) ON DELETE RESTRICT,
    request_key uuid NOT NULL,
    title text NOT NULL CHECK (char_length(btrim(title)) BETWEEN 3 AND 160),
    body text NOT NULL CHECK (char_length(btrim(body)) BETWEEN 1 AND 10000),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT community_post_request_once UNIQUE (community_id, author_account_id, request_key)
);
CREATE INDEX post_chronology ON replica.community_post (community_id, created_at DESC, id DESC);
CREATE INDEX post_by_author ON replica.community_post (author_account_id);

ALTER TABLE replica.community ENABLE ROW LEVEL SECURITY;
ALTER TABLE replica.community_membership ENABLE ROW LEVEL SECURITY;
ALTER TABLE replica.community_post ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON replica.community, replica.community_membership, replica.community_post
    FROM PUBLIC, replica_app, replica_academic;

-- Backend supplies p_actor from the authenticated principal, never from JSON.
-- Locks serialize membership revocation with reads/writes in flight.
CREATE FUNCTION replica.require_community_member(p_actor uuid, p_community uuid) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    PERFORM 1 FROM replica.access_account a WHERE a.id = p_actor AND a.enabled FOR SHARE;
    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1401', MESSAGE = 'account_unavailable';
    END IF;
    PERFORM 1 FROM replica.community_membership m
    JOIN replica.community c ON c.id = m.community_id
    WHERE m.account_id = p_actor AND m.community_id = p_community AND m.active AND c.active
    FOR SHARE OF m, c;
    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1404', MESSAGE = 'community_unavailable';
    END IF;
END;
$$;
REVOKE ALL ON FUNCTION replica.require_community_member(uuid, uuid) FROM PUBLIC;

CREATE FUNCTION replica.my_communities(p_actor uuid)
RETURNS TABLE (id uuid, slug text, name text, description text, rules text, role text)
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    PERFORM 1 FROM replica.access_account a WHERE a.id = p_actor AND a.enabled FOR SHARE;
    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1401', MESSAGE = 'account_unavailable';
    END IF;
    RETURN QUERY
    SELECT c.id, c.slug, c.name, c.description, c.rules, m.role
    FROM replica.community c
    JOIN replica.community_membership m ON m.community_id = c.id
    WHERE m.account_id = p_actor AND m.active AND c.active
    ORDER BY c.name, c.id;
END;
$$;

CREATE FUNCTION replica.community_posts(
    p_actor uuid, p_community uuid, p_before_time timestamptz, p_before_id uuid, p_limit integer
)
RETURNS TABLE (
    id uuid, community_id uuid, author_account_id uuid, author_alias text,
    title text, body text, created_at timestamptz
)
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    PERFORM replica.require_community_member(p_actor, p_community);
    IF p_limit IS NULL OR p_limit NOT BETWEEN 1 AND 51
       OR (p_before_time IS NULL) <> (p_before_id IS NULL) THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_page';
    END IF;
    RETURN QUERY
    SELECT p.id, p.community_id, p.author_account_id, a.alias, p.title, p.body, p.created_at
    FROM replica.community_post p
    JOIN replica.account_profile a ON a.account_id = p.author_account_id
    WHERE p.community_id = p_community
      AND (p_before_time IS NULL OR (p.created_at, p.id) < (p_before_time, p_before_id))
    ORDER BY p.created_at DESC, p.id DESC
    LIMIT p_limit;
END;
$$;

CREATE FUNCTION replica.create_community_post(
    p_actor uuid, p_community uuid, p_request_key uuid, p_title text, p_body text
)
RETURNS TABLE (
    id uuid, community_id uuid, author_account_id uuid, author_alias text,
    title text, body text, created_at timestamptz
)
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
DECLARE
    saved replica.community_post%ROWTYPE;
BEGIN
    PERFORM replica.require_community_member(p_actor, p_community);
    IF p_request_key IS NULL THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'request_key_required';
    END IF;
    INSERT INTO replica.community_post (community_id, author_account_id, request_key, title, body)
    VALUES (p_community, p_actor, p_request_key, p_title, p_body)
    ON CONFLICT ON CONSTRAINT community_post_request_once DO NOTHING
    RETURNING * INTO saved;
    IF NOT FOUND THEN
        SELECT p.* INTO saved FROM replica.community_post p
        WHERE p.community_id = p_community AND p.author_account_id = p_actor AND p.request_key = p_request_key;
        IF saved.title IS DISTINCT FROM p_title OR saved.body IS DISTINCT FROM p_body THEN
            RAISE EXCEPTION USING ERRCODE = 'P1409', MESSAGE = 'post_request_conflict';
        END IF;
    END IF;
    RETURN QUERY SELECT saved.id, saved.community_id, saved.author_account_id, a.alias,
                        saved.title, saved.body, saved.created_at
    FROM replica.account_profile a WHERE a.account_id = p_actor;
END;
$$;

REVOKE ALL ON FUNCTION replica.my_communities(uuid),
    replica.community_posts(uuid, uuid, timestamptz, uuid, integer),
    replica.create_community_post(uuid, uuid, uuid, text, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION replica.my_communities(uuid),
    replica.community_posts(uuid, uuid, timestamptz, uuid, integer),
    replica.create_community_post(uuid, uuid, uuid, text, text) TO "${backendLogin}";
