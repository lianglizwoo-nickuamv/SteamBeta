-- The backend login placeholder comes from trusted deployment configuration, never HTTP.
CREATE TABLE replica.access_account (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    login_identifier text NOT NULL UNIQUE CHECK (
        login_identifier ~ '^[a-z0-9][a-z0-9._@-]{2,79}$'
    ),
    password_hash text NOT NULL CHECK (btrim(password_hash) <> ''),
    enabled boolean NOT NULL DEFAULT true,
    university_id uuid NOT NULL,
    student_id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (university_id, student_id),
    FOREIGN KEY (university_id, student_id)
        REFERENCES replica.student (university_id, id) ON DELETE RESTRICT
);
ALTER TABLE replica.access_account ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON replica.access_account FROM PUBLIC, replica_app, replica_academic;

CREATE FUNCTION replica.lookup_access_account(p_login text)
RETURNS TABLE (account_id uuid, login_identifier text, password_hash text,
               enabled boolean, university_id uuid, student_id uuid)
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
    SELECT a.id, a.login_identifier, a.password_hash, a.enabled, a.university_id, a.student_id
    FROM replica.access_account a
    WHERE a.login_identifier = p_login
      AND p_login ~ '^[a-z0-9][a-z0-9._@-]{2,79}$'
    LIMIT 1
$$;
REVOKE ALL ON FUNCTION replica.lookup_access_account(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION replica.lookup_access_account(text) TO "${backendLogin}";
