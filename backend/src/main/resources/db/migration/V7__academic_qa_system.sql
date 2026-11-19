-- V7: Sistema de Preguntas y Respuestas (Q&A) Académico
-- SteamBeta Campus: preguntas temáticas, etiquetas y respuestas aceptadas como solución

-- 1. Ampliación de publicaciones para soportar Preguntas Académicas
ALTER TABLE replica.community_post
    ADD COLUMN IF NOT EXISTS post_type text NOT NULL DEFAULT 'POST'
        CHECK (post_type IN ('POST', 'QUESTION')),
    ADD COLUMN IF NOT EXISTS subject_tag text
        CHECK (subject_tag IS NULL OR char_length(btrim(subject_tag)) BETWEEN 2 AND 80),
    ADD COLUMN IF NOT EXISTS tags text[] DEFAULT '{}',
    ADD COLUMN IF NOT EXISTS resolved_comment_id uuid
        REFERENCES replica.community_comment(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS post_by_type ON replica.community_post (community_id, post_type, created_at DESC);
CREATE INDEX IF NOT EXISTS post_by_subject ON replica.community_post (community_id, subject_tag) WHERE subject_tag IS NOT NULL;

-- 2. Ampliación de comentarios para marcar soluciones
ALTER TABLE replica.community_comment
    ADD COLUMN IF NOT EXISTS is_solution boolean NOT NULL DEFAULT false;

-- 3. Función para crear pregunta académica
CREATE OR REPLACE FUNCTION replica.create_community_question(
    p_actor uuid,
    p_community uuid,
    p_request_key uuid,
    p_title text,
    p_body text,
    p_subject text,
    p_tags text[]
)
RETURNS TABLE (
    id uuid,
    community_id uuid,
    author_account_id uuid,
    author_alias text,
    title text,
    body text,
    subject_tag text,
    tags text[],
    created_at timestamptz
)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
DECLARE
    saved replica.community_post%ROWTYPE;
    clean_title text;
    clean_body text;
    clean_subj text;
BEGIN
    PERFORM replica.require_community_member(p_actor, p_community);

    clean_title := btrim(p_title);
    clean_body := btrim(p_body);
    clean_subj := NULLIF(btrim(p_subject), '');

    IF p_request_key IS NULL
       OR char_length(clean_title) NOT BETWEEN 3 AND 160
       OR char_length(clean_body) NOT BETWEEN 1 AND 10000 THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_question';
    END IF;

    IF clean_subj IS NOT NULL AND char_length(clean_subj) NOT BETWEEN 2 AND 80 THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_subject_tag';
    END IF;

    INSERT INTO replica.community_post(
        community_id, author_account_id, request_key, title, body,
        post_type, subject_tag, tags
    )
    VALUES (
        p_community, p_actor, p_request_key, clean_title, clean_body,
        'QUESTION', clean_subj, coalesce(p_tags, '{}'::text[])
    )
    ON CONFLICT ON CONSTRAINT community_post_request_once DO NOTHING RETURNING * INTO saved;

    IF NOT FOUND THEN
        SELECT p.* INTO saved FROM replica.community_post p
        WHERE p.community_id = p_community AND p.author_account_id = p_actor AND p.request_key = p_request_key;
        IF saved.title IS DISTINCT FROM clean_title OR saved.body IS DISTINCT FROM clean_body THEN
            RAISE EXCEPTION USING ERRCODE = 'P1409', MESSAGE = 'post_request_conflict';
        END IF;
    END IF;

    RETURN QUERY
    SELECT saved.id, saved.community_id, saved.author_account_id, a.alias,
           saved.title, saved.body, saved.subject_tag, saved.tags, saved.created_at
    FROM replica.account_profile a WHERE a.account_id = p_actor;
END;
$$;

-- 4. Función para aceptar una respuesta como solución
-- CRÍTICO: Solo el autor de la pregunta puede marcarla como solución.
CREATE OR REPLACE FUNCTION replica.accept_answer(
    p_actor uuid,
    p_community uuid,
    p_post uuid,
    p_comment uuid
)
RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
DECLARE
    post_author uuid;
    post_comm uuid;
    post_status text;
    comment_post uuid;
    comment_status text;
BEGIN
    PERFORM replica.require_community_member(p_actor, p_community);

    SELECT p.author_account_id, p.community_id, p.status
    INTO post_author, post_comm, post_status
    FROM replica.community_post p
    WHERE p.id = p_post FOR UPDATE;

    IF NOT FOUND OR post_comm <> p_community OR post_status <> 'ACTIVE' THEN
        RAISE EXCEPTION USING ERRCODE = 'P1404', MESSAGE = 'content_unavailable';
    END IF;

    -- Solo el autor de la pregunta puede seleccionar la solución
    IF post_author <> p_actor THEN
        RAISE EXCEPTION USING ERRCODE = 'P1404', MESSAGE = 'only_author_can_accept_solution';
    END IF;

    -- Verificar comentario
    SELECT c.post_id, c.status
    INTO comment_post, comment_status
    FROM replica.community_comment c
    WHERE c.id = p_comment FOR UPDATE;

    IF NOT FOUND OR comment_post <> p_post OR comment_status <> 'ACTIVE' THEN
        RAISE EXCEPTION USING ERRCODE = 'P1404', MESSAGE = 'comment_unavailable';
    END IF;

    -- Desmarcar solución previa si existía
    UPDATE replica.community_comment
    SET is_solution = false
    WHERE post_id = p_post AND is_solution;

    -- Marcar nuevo comentario como solución
    UPDATE replica.community_comment
    SET is_solution = true
    WHERE id = p_comment;

    -- Actualizar post
    UPDATE replica.community_post
    SET resolved_comment_id = p_comment
    WHERE id = p_post;
END;
$$;

-- 5. Listar y buscar preguntas de la comunidad
CREATE OR REPLACE FUNCTION replica.community_questions(
    p_actor uuid,
    p_community uuid,
    p_status_filter text, -- 'ALL', 'RESOLVED', 'PENDING'
    p_subject_filter text,
    p_search text,
    p_limit integer
)
RETURNS TABLE (
    id uuid,
    community_id uuid,
    author_account_id uuid,
    author_alias text,
    title text,
    body text,
    subject_tag text,
    tags text[],
    created_at timestamptz,
    status text,
    comment_count bigint,
    is_resolved boolean,
    resolved_comment_id uuid
)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    PERFORM replica.require_community_member(p_actor, p_community);

    IF p_limit IS NULL OR p_limit NOT BETWEEN 1 AND 50 THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_page';
    END IF;

    RETURN QUERY
    SELECT p.id, p.community_id, p.author_account_id, a.alias,
           p.title, p.body, p.subject_tag, p.tags, p.created_at, p.status,
           (SELECT count(*) FROM replica.community_comment c WHERE c.post_id = p.id AND c.status = 'ACTIVE'),
           (p.resolved_comment_id IS NOT NULL),
           p.resolved_comment_id
    FROM replica.community_post p
    JOIN replica.account_profile a ON a.account_id = p.author_account_id
    WHERE p.community_id = p_community
      AND p.post_type = 'QUESTION'
      AND p.status = 'ACTIVE'
      AND (
          p_status_filter = 'ALL'
          OR (p_status_filter = 'RESOLVED' AND p.resolved_comment_id IS NOT NULL)
          OR (p_status_filter = 'PENDING' AND p.resolved_comment_id IS NULL)
      )
      AND (
          p_subject_filter IS NULL OR p_subject_filter = ''
          OR lower(p.subject_tag) = lower(btrim(p_subject_filter))
      )
      AND (
          p_search IS NULL OR p_search = ''
          OR p.title ILIKE '%' || btrim(p_search) || '%'
          OR p.body ILIKE '%' || btrim(p_search) || '%'
      )
    ORDER BY p.created_at DESC, p.id DESC
    LIMIT p_limit;
END;
$$;

-- Conceder permisos
REVOKE ALL ON FUNCTION
    replica.create_community_question(uuid, uuid, uuid, text, text, text, text[]),
    replica.accept_answer(uuid, uuid, uuid, uuid),
    replica.community_questions(uuid, uuid, text, text, text, integer)
FROM PUBLIC;

GRANT EXECUTE ON FUNCTION
    replica.create_community_question(uuid, uuid, uuid, text, text, text, text[]),
    replica.accept_answer(uuid, uuid, uuid, uuid),
    replica.community_questions(uuid, uuid, text, text, text, integer)
TO "${backendLogin}";
