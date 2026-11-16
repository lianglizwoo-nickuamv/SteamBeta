-- Conversations use one comment level. Deletion is a tombstone: text is erased,
-- existing replies remain readable, and new replies are rejected.
ALTER TABLE replica.community_post
    ADD COLUMN status text NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'HIDDEN', 'DELETED')),
    ALTER COLUMN title DROP NOT NULL,
    ALTER COLUMN body DROP NOT NULL;

CREATE TABLE replica.community_comment (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id uuid NOT NULL REFERENCES replica.community_post(id) ON DELETE RESTRICT,
    author_account_id uuid NOT NULL REFERENCES replica.access_account(id) ON DELETE RESTRICT,
    body text CHECK (body IS NULL OR char_length(btrim(body)) BETWEEN 1 AND 5000),
    status text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'HIDDEN', 'DELETED')),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX comment_by_post ON replica.community_comment(post_id, created_at, id);
CREATE INDEX comment_by_author ON replica.community_comment(author_account_id);

CREATE TABLE replica.community_report (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    community_id uuid NOT NULL REFERENCES replica.community(id) ON DELETE RESTRICT,
    reporter_account_id uuid NOT NULL REFERENCES replica.access_account(id) ON DELETE RESTRICT,
    post_id uuid REFERENCES replica.community_post(id) ON DELETE RESTRICT,
    comment_id uuid REFERENCES replica.community_comment(id) ON DELETE RESTRICT,
    reason text NOT NULL CHECK (char_length(btrim(reason)) BETWEEN 3 AND 1000),
    status text NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'RESOLVED', 'DISMISSED')),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    resolved_at timestamptz,
    resolved_by uuid REFERENCES replica.access_account(id) ON DELETE RESTRICT,
    resolution_note text,
    CHECK ((post_id IS NULL) <> (comment_id IS NULL)),
    CHECK ((status = 'OPEN' AND resolved_at IS NULL AND resolved_by IS NULL)
        OR (status <> 'OPEN' AND resolved_at IS NOT NULL AND resolved_by IS NOT NULL))
);
CREATE UNIQUE INDEX one_open_report_per_account_post
    ON replica.community_report(reporter_account_id, post_id) WHERE status = 'OPEN' AND post_id IS NOT NULL;
CREATE UNIQUE INDEX one_open_report_per_account_comment
    ON replica.community_report(reporter_account_id, comment_id) WHERE status = 'OPEN' AND comment_id IS NOT NULL;
CREATE INDEX reports_for_moderation ON replica.community_report(community_id, created_at, id) WHERE status = 'OPEN';

CREATE TABLE replica.community_moderation_event (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    community_id uuid NOT NULL REFERENCES replica.community(id) ON DELETE RESTRICT,
    actor_account_id uuid NOT NULL REFERENCES replica.access_account(id) ON DELETE RESTRICT,
    post_id uuid REFERENCES replica.community_post(id) ON DELETE RESTRICT,
    comment_id uuid REFERENCES replica.community_comment(id) ON DELETE RESTRICT,
    report_id uuid REFERENCES replica.community_report(id) ON DELETE RESTRICT,
    action text NOT NULL CHECK (action IN ('HIDE_POST', 'HIDE_COMMENT', 'RESOLVE_REPORT', 'DISMISS_REPORT')),
    reason text NOT NULL CHECK (char_length(btrim(reason)) BETWEEN 3 AND 1000),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    CHECK ((post_id IS NULL) <> (comment_id IS NULL) OR report_id IS NOT NULL)
);
CREATE INDEX moderation_event_by_community ON replica.community_moderation_event(community_id, created_at DESC);

ALTER TABLE replica.community_comment ENABLE ROW LEVEL SECURITY;
ALTER TABLE replica.community_report ENABLE ROW LEVEL SECURITY;
ALTER TABLE replica.community_moderation_event ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON replica.community_comment, replica.community_report, replica.community_moderation_event
    FROM PUBLIC, replica_app, replica_academic;

DROP FUNCTION replica.community_posts(uuid, uuid, timestamptz, uuid, integer);
CREATE FUNCTION replica.community_posts(
    p_actor uuid, p_community uuid, p_before_time timestamptz, p_before_id uuid, p_limit integer
)
RETURNS TABLE (
    id uuid, community_id uuid, author_account_id uuid, author_alias text,
    title text, body text, created_at timestamptz, status text, comment_count bigint
)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    PERFORM replica.require_community_member(p_actor, p_community);
    IF p_limit IS NULL OR p_limit NOT BETWEEN 1 AND 51
       OR (p_before_time IS NULL) <> (p_before_id IS NULL) THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_page';
    END IF;
    RETURN QUERY
    SELECT p.id, p.community_id, p.author_account_id, a.alias,
           CASE WHEN p.status = 'DELETED' THEN 'Publicación eliminada' ELSE p.title END,
           CASE WHEN p.status = 'ACTIVE' THEN p.body ELSE NULL END,
           p.created_at, p.status,
           (SELECT count(*) FROM replica.community_comment c WHERE c.post_id = p.id AND c.status = 'ACTIVE')
    FROM replica.community_post p
    JOIN replica.account_profile a ON a.account_id = p.author_account_id
    WHERE p.community_id = p_community AND p.status <> 'HIDDEN'
      AND (p_before_time IS NULL OR (p.created_at, p.id) < (p_before_time, p_before_id))
    ORDER BY p.created_at DESC, p.id DESC LIMIT p_limit;
END;
$$;

CREATE OR REPLACE FUNCTION replica.create_community_post(
    p_actor uuid, p_community uuid, p_request_key uuid, p_title text, p_body text
)
RETURNS TABLE (
    id uuid, community_id uuid, author_account_id uuid, author_alias text,
    title text, body text, created_at timestamptz
)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
DECLARE saved replica.community_post%ROWTYPE;
BEGIN
    PERFORM replica.require_community_member(p_actor, p_community);
    IF p_request_key IS NULL OR char_length(btrim(p_title)) NOT BETWEEN 3 AND 160
       OR char_length(btrim(p_body)) NOT BETWEEN 1 AND 10000 THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_post';
    END IF;
    INSERT INTO replica.community_post(community_id, author_account_id, request_key, title, body)
    VALUES (p_community, p_actor, p_request_key, btrim(p_title), btrim(p_body))
    ON CONFLICT ON CONSTRAINT community_post_request_once DO NOTHING RETURNING * INTO saved;
    IF NOT FOUND THEN
        SELECT p.* INTO saved FROM replica.community_post p
        WHERE p.community_id = p_community AND p.author_account_id = p_actor AND p.request_key = p_request_key;
        IF saved.title IS DISTINCT FROM btrim(p_title) OR saved.body IS DISTINCT FROM btrim(p_body) THEN
            RAISE EXCEPTION USING ERRCODE = 'P1409', MESSAGE = 'post_request_conflict';
        END IF;
    END IF;
    RETURN QUERY SELECT saved.id, saved.community_id, saved.author_account_id, a.alias,
                        saved.title, saved.body, saved.created_at
    FROM replica.account_profile a WHERE a.account_id = p_actor;
END;
$$;

CREATE FUNCTION replica.community_comments(p_actor uuid, p_community uuid, p_post uuid)
RETURNS TABLE(id uuid, post_id uuid, author_account_id uuid, author_alias text,
              body text, status text, created_at timestamptz, updated_at timestamptz)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
DECLARE post_status text;
BEGIN
    PERFORM replica.require_community_member(p_actor, p_community);
    SELECT p.status INTO post_status FROM replica.community_post p
    WHERE p.id = p_post AND p.community_id = p_community;
    IF NOT FOUND OR post_status = 'HIDDEN' THEN
        RAISE EXCEPTION USING ERRCODE = 'P1404', MESSAGE = 'content_unavailable';
    END IF;
    RETURN QUERY SELECT c.id, c.post_id, c.author_account_id, a.alias,
           CASE WHEN c.status = 'ACTIVE' THEN c.body ELSE NULL END,
           c.status, c.created_at, c.updated_at
    FROM replica.community_comment c JOIN replica.account_profile a ON a.account_id = c.author_account_id
    WHERE c.post_id = p_post ORDER BY c.created_at, c.id;
END;
$$;

CREATE FUNCTION replica.create_community_comment(p_actor uuid,p_community uuid,p_post uuid,p_body text)
RETURNS TABLE(id uuid,post_id uuid,author_account_id uuid,author_alias text,body text,status text,created_at timestamptz,updated_at timestamptz)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
DECLARE new_id uuid;
BEGIN
    PERFORM replica.require_community_member(p_actor,p_community);
    IF char_length(btrim(p_body)) NOT BETWEEN 1 AND 5000 THEN
        RAISE EXCEPTION USING ERRCODE='22023',MESSAGE='invalid_comment';
    END IF;
    PERFORM 1 FROM replica.community_post p WHERE p.id=p_post AND p.community_id=p_community AND p.status='ACTIVE' FOR SHARE;
    IF NOT FOUND THEN RAISE EXCEPTION USING ERRCODE='P1404',MESSAGE='content_unavailable'; END IF;
    INSERT INTO replica.community_comment(post_id,author_account_id,body) VALUES(p_post,p_actor,btrim(p_body)) RETURNING community_comment.id INTO new_id;
    RETURN QUERY SELECT c.id,c.post_id,c.author_account_id,a.alias,c.body,c.status,c.created_at,c.updated_at
        FROM replica.community_comment c JOIN replica.account_profile a ON a.account_id=c.author_account_id WHERE c.id=new_id;
END;
$$;

CREATE FUNCTION replica.edit_community_comment(p_actor uuid,p_community uuid,p_comment uuid,p_body text)
RETURNS TABLE(id uuid,post_id uuid,author_account_id uuid,author_alias text,body text,status text,created_at timestamptz,updated_at timestamptz)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
DECLARE saved replica.community_comment%ROWTYPE;
BEGIN
    PERFORM replica.require_community_member(p_actor,p_community);
    IF char_length(btrim(p_body)) NOT BETWEEN 1 AND 5000 THEN RAISE EXCEPTION USING ERRCODE='22023',MESSAGE='invalid_comment'; END IF;
    UPDATE replica.community_comment c SET body=btrim(p_body),updated_at=clock_timestamp()
    FROM replica.community_post p WHERE c.id=p_comment AND p.id=c.post_id AND p.community_id=p_community
      AND c.author_account_id=p_actor AND c.status='ACTIVE' AND p.status='ACTIVE' RETURNING c.* INTO saved;
    IF NOT FOUND THEN RAISE EXCEPTION USING ERRCODE='P1404',MESSAGE='content_unavailable'; END IF;
    RETURN QUERY SELECT saved.id,saved.post_id,saved.author_account_id,a.alias,saved.body,saved.status,saved.created_at,saved.updated_at
        FROM replica.account_profile a WHERE a.account_id=p_actor;
END;
$$;

CREATE FUNCTION replica.delete_community_comment(p_actor uuid,p_community uuid,p_comment uuid)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    PERFORM replica.require_community_member(p_actor,p_community);
    UPDATE replica.community_comment c SET body=NULL,status='DELETED',updated_at=clock_timestamp()
    FROM replica.community_post p WHERE c.id=p_comment AND p.id=c.post_id AND p.community_id=p_community
      AND c.author_account_id=p_actor AND c.status='ACTIVE' AND p.status='ACTIVE';
    IF NOT FOUND THEN RAISE EXCEPTION USING ERRCODE='P1404',MESSAGE='content_unavailable'; END IF;
END;
$$;

CREATE FUNCTION replica.edit_community_post(p_actor uuid,p_community uuid,p_post uuid,p_title text,p_body text)
RETURNS TABLE(id uuid,community_id uuid,author_account_id uuid,author_alias text,title text,body text,created_at timestamptz)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
DECLARE saved replica.community_post%ROWTYPE;
BEGIN
    PERFORM replica.require_community_member(p_actor,p_community);
    IF char_length(btrim(p_title)) NOT BETWEEN 3 AND 160 OR char_length(btrim(p_body)) NOT BETWEEN 1 AND 10000 THEN
        RAISE EXCEPTION USING ERRCODE='22023',MESSAGE='invalid_post';
    END IF;
    UPDATE replica.community_post p SET title=btrim(p_title),body=btrim(p_body)
    WHERE p.id=p_post AND p.community_id=p_community AND p.author_account_id=p_actor AND p.status='ACTIVE' RETURNING p.* INTO saved;
    IF NOT FOUND THEN RAISE EXCEPTION USING ERRCODE='P1404',MESSAGE='content_unavailable'; END IF;
    RETURN QUERY SELECT saved.id,saved.community_id,saved.author_account_id,a.alias,saved.title,saved.body,saved.created_at FROM replica.account_profile a WHERE a.account_id=p_actor;
END;
$$;

CREATE FUNCTION replica.delete_community_post(p_actor uuid,p_community uuid,p_post uuid)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    PERFORM replica.require_community_member(p_actor,p_community);
    UPDATE replica.community_post p SET title='Publicación eliminada',body=NULL,status='DELETED'
    WHERE p.id=p_post AND p.community_id=p_community AND p.author_account_id=p_actor AND p.status='ACTIVE';
    IF NOT FOUND THEN RAISE EXCEPTION USING ERRCODE='P1404',MESSAGE='content_unavailable'; END IF;
END;
$$;

CREATE FUNCTION replica.report_community_content(p_actor uuid,p_community uuid,p_post uuid,p_comment uuid,p_reason text)
RETURNS uuid LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
DECLARE report_id uuid;
BEGIN
    PERFORM replica.require_community_member(p_actor,p_community);
    IF (p_post IS NULL) = (p_comment IS NULL) OR char_length(btrim(p_reason)) NOT BETWEEN 3 AND 1000 THEN
        RAISE EXCEPTION USING ERRCODE='22023',MESSAGE='invalid_report';
    END IF;
    IF p_post IS NOT NULL THEN
        PERFORM 1 FROM replica.community_post p WHERE p.id=p_post AND p.community_id=p_community AND p.author_account_id<>p_actor AND p.status='ACTIVE';
    ELSE
        PERFORM 1 FROM replica.community_comment c JOIN replica.community_post p ON p.id=c.post_id
        WHERE c.id=p_comment AND p.community_id=p_community AND c.author_account_id<>p_actor AND c.status='ACTIVE' AND p.status='ACTIVE';
    END IF;
    IF NOT FOUND THEN RAISE EXCEPTION USING ERRCODE='P1404',MESSAGE='content_unavailable'; END IF;
    INSERT INTO replica.community_report(community_id,reporter_account_id,post_id,comment_id,reason)
      VALUES(p_community,p_actor,p_post,p_comment,btrim(p_reason)) RETURNING id INTO report_id;
    RETURN report_id;
EXCEPTION WHEN unique_violation THEN
    RAISE EXCEPTION USING ERRCODE='P1409',MESSAGE='report_already_open';
END;
$$;

CREATE FUNCTION replica.moderation_reports(p_actor uuid,p_community uuid)
RETURNS TABLE(id uuid,post_id uuid,comment_id uuid,reason text,reporter_alias text,created_at timestamptz,
              content_author_alias text,content text,content_status text)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    PERFORM replica.require_community_member(p_actor,p_community);
    IF NOT EXISTS(SELECT 1 FROM replica.community_membership m WHERE m.community_id=p_community AND m.account_id=p_actor AND m.role IN ('MODERATOR','ADMIN') AND m.active) THEN
       RAISE EXCEPTION USING ERRCODE='P1404',MESSAGE='community_unavailable';
    END IF;
    RETURN QUERY SELECT r.id,r.post_id,r.comment_id,r.reason,reporter.alias,r.created_at,
      CASE WHEN r.post_id IS NOT NULL THEN pa.alias ELSE ca.alias END,
      CASE WHEN r.post_id IS NOT NULL THEN p.body ELSE c.body END,
      CASE WHEN r.post_id IS NOT NULL THEN p.status ELSE c.status END
    FROM replica.community_report r JOIN replica.account_profile reporter ON reporter.account_id=r.reporter_account_id
    LEFT JOIN replica.community_post p ON p.id=r.post_id LEFT JOIN replica.account_profile pa ON pa.account_id=p.author_account_id
    LEFT JOIN replica.community_comment c ON c.id=r.comment_id LEFT JOIN replica.account_profile ca ON ca.account_id=c.author_account_id
    WHERE r.community_id=p_community AND r.status='OPEN' ORDER BY r.created_at,r.id;
END;
$$;

CREATE FUNCTION replica.moderate_community_content(p_actor uuid,p_community uuid,p_post uuid,p_comment uuid,p_reason text)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
DECLARE affected integer; action_name text;
BEGIN
    PERFORM replica.require_community_member(p_actor,p_community);
    IF NOT EXISTS(SELECT 1 FROM replica.community_membership m WHERE m.community_id=p_community AND m.account_id=p_actor AND m.role IN ('MODERATOR','ADMIN') AND m.active) THEN
       RAISE EXCEPTION USING ERRCODE='P1404',MESSAGE='community_unavailable';
    END IF;
    IF (p_post IS NULL) = (p_comment IS NULL) OR char_length(btrim(p_reason)) NOT BETWEEN 3 AND 1000 THEN RAISE EXCEPTION USING ERRCODE='22023',MESSAGE='invalid_moderation'; END IF;
    IF p_post IS NOT NULL THEN
      UPDATE replica.community_post SET status='HIDDEN' WHERE id=p_post AND community_id=p_community AND status='ACTIVE';
      action_name := 'HIDE_POST';
    ELSE
      UPDATE replica.community_comment c SET status='HIDDEN',updated_at=clock_timestamp() FROM replica.community_post p
        WHERE c.id=p_comment AND p.id=c.post_id AND p.community_id=p_community AND c.status='ACTIVE';
      action_name := 'HIDE_COMMENT';
    END IF;
    GET DIAGNOSTICS affected = ROW_COUNT;
    IF affected=0 THEN RAISE EXCEPTION USING ERRCODE='P1404',MESSAGE='content_unavailable'; END IF;
    INSERT INTO replica.community_moderation_event(community_id,actor_account_id,post_id,comment_id,action,reason)
      VALUES(p_community,p_actor,p_post,p_comment,action_name,btrim(p_reason));
END;
$$;

CREATE FUNCTION replica.resolve_community_report(p_actor uuid,p_community uuid,p_report uuid,p_resolution text,p_note text)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
DECLARE new_status text; event_action text;
BEGIN
    PERFORM replica.require_community_member(p_actor,p_community);
    IF NOT EXISTS(SELECT 1 FROM replica.community_membership m WHERE m.community_id=p_community AND m.account_id=p_actor AND m.role IN ('MODERATOR','ADMIN') AND m.active) THEN
       RAISE EXCEPTION USING ERRCODE='P1404',MESSAGE='community_unavailable';
    END IF;
    IF p_resolution NOT IN ('RESOLVED','DISMISSED') OR char_length(btrim(p_note)) NOT BETWEEN 3 AND 1000 THEN RAISE EXCEPTION USING ERRCODE='22023',MESSAGE='invalid_resolution'; END IF;
    UPDATE replica.community_report SET status=p_resolution,resolved_at=clock_timestamp(),resolved_by=p_actor,resolution_note=btrim(p_note)
      WHERE id=p_report AND community_id=p_community AND status='OPEN' RETURNING status INTO new_status;
    IF NOT FOUND THEN RAISE EXCEPTION USING ERRCODE='P1404',MESSAGE='report_unavailable'; END IF;
    event_action := CASE new_status WHEN 'RESOLVED' THEN 'RESOLVE_REPORT' ELSE 'DISMISS_REPORT' END;
    INSERT INTO replica.community_moderation_event(community_id,actor_account_id,post_id,comment_id,report_id,action,reason)
      SELECT p_community,p_actor,post_id,comment_id,p_report,event_action,btrim(p_note) FROM replica.community_report WHERE id=p_report;
END;
$$;

CREATE FUNCTION replica.hide_reported_content(p_actor uuid,p_community uuid,p_report uuid,p_note text)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
DECLARE saved replica.community_report%ROWTYPE; affected integer; hide_action text;
BEGIN
    PERFORM replica.require_community_member(p_actor,p_community);
    IF NOT EXISTS(SELECT 1 FROM replica.community_membership m WHERE m.community_id=p_community AND m.account_id=p_actor AND m.role IN ('MODERATOR','ADMIN') AND m.active) THEN
       RAISE EXCEPTION USING ERRCODE='P1404',MESSAGE='community_unavailable';
    END IF;
    IF char_length(btrim(p_note)) NOT BETWEEN 3 AND 1000 THEN RAISE EXCEPTION USING ERRCODE='22023',MESSAGE='invalid_resolution'; END IF;
    SELECT * INTO saved FROM replica.community_report r WHERE r.id=p_report AND r.community_id=p_community AND r.status='OPEN' FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION USING ERRCODE='P1404',MESSAGE='report_unavailable'; END IF;
    IF saved.post_id IS NOT NULL THEN
        UPDATE replica.community_post SET status='HIDDEN' WHERE id=saved.post_id AND community_id=p_community AND status='ACTIVE';
        hide_action := 'HIDE_POST';
    ELSE
        UPDATE replica.community_comment c SET status='HIDDEN',updated_at=clock_timestamp()
          FROM replica.community_post p WHERE c.id=saved.comment_id AND p.id=c.post_id AND p.community_id=p_community AND c.status='ACTIVE' AND p.status='ACTIVE';
        hide_action := 'HIDE_COMMENT';
    END IF;
    GET DIAGNOSTICS affected = ROW_COUNT;
    IF affected=0 THEN RAISE EXCEPTION USING ERRCODE='P1404',MESSAGE='content_unavailable'; END IF;
    UPDATE replica.community_report SET status='RESOLVED',resolved_at=clock_timestamp(),resolved_by=p_actor,resolution_note=btrim(p_note) WHERE id=p_report;
    INSERT INTO replica.community_moderation_event(community_id,actor_account_id,post_id,comment_id,report_id,action,reason)
      VALUES(p_community,p_actor,saved.post_id,saved.comment_id,p_report,hide_action,btrim(p_note));
    INSERT INTO replica.community_moderation_event(community_id,actor_account_id,post_id,comment_id,report_id,action,reason)
      VALUES(p_community,p_actor,saved.post_id,saved.comment_id,p_report,'RESOLVE_REPORT',btrim(p_note));
END;
$$;

REVOKE ALL ON FUNCTION replica.community_posts(uuid,uuid,timestamptz,uuid,integer),
 replica.create_community_post(uuid,uuid,uuid,text,text),
 replica.community_comments(uuid,uuid,uuid),
 replica.create_community_comment(uuid,uuid,uuid,text),
 replica.edit_community_comment(uuid,uuid,uuid,text),
 replica.delete_community_comment(uuid,uuid,uuid),
 replica.edit_community_post(uuid,uuid,uuid,text,text),
 replica.delete_community_post(uuid,uuid,uuid),
 replica.report_community_content(uuid,uuid,uuid,uuid,text),
 replica.moderation_reports(uuid,uuid),
 replica.moderate_community_content(uuid,uuid,uuid,uuid,text),
 replica.resolve_community_report(uuid,uuid,uuid,text,text),
 replica.hide_reported_content(uuid,uuid,uuid,text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION replica.community_posts(uuid,uuid,timestamptz,uuid,integer),
 replica.create_community_post(uuid,uuid,uuid,text,text),
 replica.community_comments(uuid,uuid,uuid),
 replica.create_community_comment(uuid,uuid,uuid,text),
 replica.edit_community_comment(uuid,uuid,uuid,text),
 replica.delete_community_comment(uuid,uuid,uuid),
 replica.edit_community_post(uuid,uuid,uuid,text,text),
 replica.delete_community_post(uuid,uuid,uuid),
 replica.report_community_content(uuid,uuid,uuid,uuid,text),
 replica.moderation_reports(uuid,uuid),
 replica.moderate_community_content(uuid,uuid,uuid,uuid,text),
 replica.resolve_community_report(uuid,uuid,uuid,text,text),
 replica.hide_reported_content(uuid,uuid,uuid,text) TO "${backendLogin}";
