-- Accounts can participate in the community without an academic record.
-- Existing academic links and all V1/V2 data remain valid.
ALTER TABLE replica.access_account
    ALTER COLUMN university_id DROP NOT NULL,
    ALTER COLUMN student_id DROP NOT NULL,
    ADD CONSTRAINT academic_link_complete
        CHECK ((university_id IS NULL) = (student_id IS NULL));

CREATE TABLE replica.account_profile (
    account_id uuid PRIMARY KEY REFERENCES replica.access_account (id) ON DELETE RESTRICT,
    alias text NOT NULL CHECK (char_length(btrim(alias)) BETWEEN 2 AND 60),
    declared_university text CHECK (char_length(declared_university) BETWEEN 1 AND 120),
    declared_career text CHECK (char_length(declared_career) BETWEEN 1 AND 120),
    created_at timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE replica.account_profile ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON replica.account_profile FROM PUBLIC, replica_app, replica_academic;

-- Login identifiers can contain an email address, so they never become aliases.
INSERT INTO replica.account_profile (account_id, alias)
SELECT a.id, CASE WHEN char_length(btrim(s.alias)) BETWEEN 2 AND 60
                      THEN btrim(s.alias) ELSE 'Miembro' END
FROM replica.access_account a
LEFT JOIN replica.student s ON (s.university_id, s.id) = (a.university_id, a.student_id);

-- Preserve legacy administrative/demo account creation after this migration.
CREATE FUNCTION replica.initialize_account_profile() RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog, pg_temp
AS $$
DECLARE
    initial_alias text;
BEGIN
    SELECT s.alias INTO initial_alias FROM replica.student s
    WHERE s.university_id = NEW.university_id AND s.id = NEW.student_id;
    IF initial_alias IS NULL OR char_length(btrim(initial_alias)) NOT BETWEEN 2 AND 60 THEN
        initial_alias := 'Miembro';
    END IF;
    INSERT INTO replica.account_profile (account_id, alias) VALUES (NEW.id, btrim(initial_alias));
    RETURN NEW;
END;
$$;
CREATE TRIGGER initialize_account_profile
AFTER INSERT ON replica.access_account
FOR EACH ROW EXECUTE FUNCTION replica.initialize_account_profile();
REVOKE ALL ON FUNCTION replica.initialize_account_profile() FROM PUBLIC;

-- No student, enrollment, wallet or institutional verification is created here.
CREATE FUNCTION replica.register_account(
    p_login text, p_hash text, p_alias text, p_university text, p_career text
) RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
DECLARE
    new_id uuid;
BEGIN
    INSERT INTO replica.access_account (login_identifier, password_hash)
    VALUES (p_login, p_hash) RETURNING id INTO new_id;
    UPDATE replica.account_profile
    SET alias = p_alias, declared_university = p_university, declared_career = p_career
    WHERE account_id = new_id;
    RETURN new_id;
END;
$$;
REVOKE ALL ON FUNCTION replica.register_account(text, text, text, text, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION replica.register_account(text, text, text, text, text) TO "${backendLogin}";

-- Only the backend calls this function, using the account ID from its principal.
-- Return a projection without login identifiers, password hashes or email fields.
CREATE FUNCTION replica.read_account_profile(p_account_id uuid)
RETURNS TABLE (
    account_id uuid, alias text, declared_university text, declared_career text,
    student_id uuid, academic_university_name text
)
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
    SELECT a.id, p.alias, p.declared_university, p.declared_career, a.student_id, u.name
    FROM replica.access_account a
    JOIN replica.account_profile p ON p.account_id = a.id
    LEFT JOIN replica.university u ON u.id = a.university_id
    WHERE a.id = p_account_id AND a.enabled
$$;
REVOKE ALL ON FUNCTION replica.read_account_profile(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION replica.read_account_profile(uuid) TO "${backendLogin}";
