-- V13: Registro estudiantil unificado, correo electrónico y autenticación
-- Base de datos oficial: steam_academico (PostgreSQL 18)
-- Secuencia: Se ejecuta después de V12 (o incrementalmente sobre V1 a V11)
--
-- ARQUITECTURA DE SEGURIDAD (ROW LEVEL SECURITY & LEAST PRIVILEGE):
-- 1. V13 es la única autoridad de correo y autenticación.
-- 2. RLS se mantiene habilitado con default-deny en todas las tablas sensibles.
-- 3. replica_runtime NO requiere permisos DML directos sobre las tablas;
--    todas las operaciones se canalizan a través de funciones SECURITY DEFINER auditadas.
-- 4. NO se inventan correos ni se modifican cuentas existentes.
-- 5. Cuentas antiguas sin correo permanecen con email = NULL y email_verified = false.
-- 6. Inicio de sesión por identificador preservado al 100% para todas las cuentas.

-- 1. Soporte de Correo Electrónico en replica.access_account
ALTER TABLE replica.access_account
    ADD COLUMN IF NOT EXISTS email text,
    ADD COLUMN IF NOT EXISTS email_verified boolean NOT NULL DEFAULT false;

-- 2. Restricción de formato para correos electrónicos válidos (admite NULL para cuentas antiguas)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'access_account_email_format'
    ) THEN
        ALTER TABLE replica.access_account
            ADD CONSTRAINT access_account_email_format
            CHECK (email IS NULL OR email ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$');
    END IF;
END $$;

-- 3. Índice Único Normalizado sobre Correo Electrónico
--    Índice parcial: Solo aplica sobre correos no nulos. Cuentas antiguas con email NULL coexisten sin conflicto.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE c.relname = 'access_account_lower_email_uidx' AND n.nspname = 'replica'
    ) THEN
        CREATE UNIQUE INDEX access_account_lower_email_uidx
        ON replica.access_account (lower(btrim(email)))
        WHERE email IS NOT NULL;
    END IF;
END $$;

-- 4. Tabla de Tokens de Verificación de Correo Electrónico (RLS habilitado)
CREATE TABLE IF NOT EXISTS replica.email_verification_token (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id uuid NOT NULL REFERENCES replica.access_account(id) ON DELETE CASCADE,
    token_hash text NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    used boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX IF NOT EXISTS verification_token_lookup
    ON replica.email_verification_token(token_hash)
    WHERE NOT used;

ALTER TABLE replica.email_verification_token ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON replica.email_verification_token FROM PUBLIC, replica_app, replica_academic;

-- 5. Función SECURITY DEFINER: Crear token de verificación
CREATE OR REPLACE FUNCTION replica.create_email_verification_token(
    p_account_id uuid,
    p_token_hash text,
    p_expires_at timestamptz
) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    PERFORM 1 FROM replica.access_account a WHERE a.id = p_account_id AND a.enabled;
    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1401', MESSAGE = 'account_unavailable';
    END IF;

    -- Invalidar tokens anteriores no consumidos para la misma cuenta
    UPDATE replica.email_verification_token
    SET used = true
    WHERE account_id = p_account_id AND NOT used;

    INSERT INTO replica.email_verification_token (account_id, token_hash, expires_at)
    VALUES (p_account_id, btrim(p_token_hash), p_expires_at);
END;
$$;

REVOKE ALL ON FUNCTION replica.create_email_verification_token(uuid, text, timestamptz) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION replica.create_email_verification_token(uuid, text, timestamptz) TO "${backendLogin}";

-- 6. Función SECURITY DEFINER: Consumir token y actualizar verificación atómicamente
CREATE OR REPLACE FUNCTION replica.consume_email_verification_token(
    p_token_hash text
) RETURNS boolean
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
DECLARE
    v_token_id uuid;
    v_account_id uuid;
    v_expires_at timestamptz;
    v_used boolean;
BEGIN
    IF p_token_hash IS NULL OR btrim(p_token_hash) = '' THEN
        RETURN false;
    END IF;

    -- Bloqueo concurrente FOR UPDATE para prevenir reutilización o condiciones de carrera
    SELECT id, account_id, expires_at, used
    INTO v_token_id, v_account_id, v_expires_at, v_used
    FROM replica.email_verification_token
    WHERE token_hash = btrim(p_token_hash)
    FOR UPDATE;

    IF NOT FOUND OR v_used OR v_expires_at <= clock_timestamp() THEN
        RETURN false;
    END IF;

    -- Marcar token como consumido
    UPDATE replica.email_verification_token
    SET used = true
    WHERE id = v_token_id;

    -- Actualizar estado de verificación en replica.access_account dentro de la misma transacción
    UPDATE replica.access_account
    SET email_verified = true
    WHERE id = v_account_id;

    RETURN true;
END;
$$;

REVOKE ALL ON FUNCTION replica.consume_email_verification_token(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION replica.consume_email_verification_token(text) TO "${backendLogin}";

-- 7. Función SECURITY DEFINER: Comprobar estado de verificación de correo
CREATE OR REPLACE FUNCTION replica.check_email_verification_status(
    p_email text
) RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
    SELECT a.email_verified
    FROM replica.access_account a
    WHERE a.email IS NOT NULL
      AND lower(a.email) = lower(btrim(p_email))
      AND a.enabled
    LIMIT 1;
$$;

REVOKE ALL ON FUNCTION replica.check_email_verification_status(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION replica.check_email_verification_status(text) TO "${backendLogin}";

-- 8. Función SECURITY DEFINER: Registro Estudiantil Atómico
CREATE OR REPLACE FUNCTION replica.register_account(
    p_alias text,
    p_login text,
    p_email text,
    p_hash text
) RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
DECLARE
    new_id uuid;
    clean_alias text := btrim(p_alias);
    clean_login text := lower(btrim(p_login));
    clean_email text := lower(btrim(p_email));
BEGIN
    IF clean_alias IS NULL OR char_length(clean_alias) NOT BETWEEN 2 AND 60 THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_alias_length';
    END IF;

    IF clean_login IS NULL OR clean_login !~ '^[a-z0-9][a-z0-9._@-]{2,79}$' THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_login_format';
    END IF;

    IF clean_email IS NULL OR clean_email !~ '^[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}$' THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_email_format';
    END IF;

    IF EXISTS (SELECT 1 FROM replica.access_account WHERE login_identifier = clean_login) THEN
        RAISE EXCEPTION USING ERRCODE = '23505', MESSAGE = 'duplicate_login';
    END IF;

    IF EXISTS (SELECT 1 FROM replica.access_account WHERE lower(email) = clean_email) THEN
        RAISE EXCEPTION USING ERRCODE = '23505', MESSAGE = 'duplicate_email';
    END IF;

    INSERT INTO replica.access_account (login_identifier, email, password_hash, enabled, email_verified)
    VALUES (clean_login, clean_email, p_hash, true, false)
    RETURNING id INTO new_id;

    INSERT INTO replica.account_profile (account_id, alias)
    VALUES (new_id, clean_alias)
    ON CONFLICT (account_id) DO UPDATE SET alias = clean_alias;

    RETURN new_id;
END;
$$;

REVOKE ALL ON FUNCTION replica.register_account(text, text, text, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION replica.register_account(text, text, text, text) TO "${backendLogin}";

-- 9. Función SECURITY DEFINER: Búsqueda de Cuenta para Autenticación
DROP FUNCTION IF EXISTS replica.lookup_access_account(text);

CREATE FUNCTION replica.lookup_access_account(p_login text)
RETURNS TABLE (
    account_id uuid,
    login_identifier text,
    password_hash text,
    enabled boolean,
    university_id uuid,
    student_id uuid,
    global_role text
)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
    SELECT a.id, a.login_identifier, a.password_hash, a.enabled, a.university_id, a.student_id,
           COALESCE(a.global_role, 'USER') AS global_role
    FROM replica.access_account a
    WHERE a.login_identifier = lower(btrim(p_login))
       OR (a.email IS NOT NULL AND lower(a.email) = lower(btrim(p_login)) AND a.email_verified = true)
    LIMIT 1;
$$;

REVOKE ALL ON FUNCTION replica.lookup_access_account(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION replica.lookup_access_account(text) TO "${backendLogin}";

-- 10. Función SECURITY DEFINER: Lectura Completa de Perfil Estudiantil
CREATE OR REPLACE FUNCTION replica.read_full_account_profile(
    p_account_id uuid
)
RETURNS TABLE (
    account_id uuid,
    login_identifier text,
    email text,
    email_verified boolean,
    email_public boolean,
    alias text,
    declared_university text,
    declared_career text,
    declared_semester integer,
    bio text,
    avatar_url text,
    academic_interests text,
    global_role text,
    communities_count integer,
    posts_count integer
)
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
DECLARE
    v_communities_count integer := 0;
    v_posts_count integer := 0;
BEGIN
    PERFORM 1 FROM replica.access_account a WHERE a.id = p_account_id AND a.enabled;
    IF NOT FOUND THEN
        RETURN;
    END IF;

    SELECT count(*)::integer INTO v_communities_count
    FROM replica.community_membership m
    WHERE m.account_id = p_account_id AND m.active;

    SELECT count(*)::integer INTO v_posts_count
    FROM replica.community_post p
    WHERE p.author_account_id = p_account_id AND p.status = 'ACTIVE';

    RETURN QUERY
    SELECT
        a.id AS account_id,
        a.login_identifier,
        a.email,
        a.email_verified,
        COALESCE(ap.email_public, false) AS email_public,
        ap.alias,
        ap.declared_university,
        ap.declared_career,
        ap.declared_semester,
        ap.bio,
        ap.avatar_url,
        ap.academic_interests,
        COALESCE(a.global_role, 'USER') AS global_role,
        v_communities_count,
        v_posts_count
    FROM replica.access_account a
    JOIN replica.account_profile ap ON ap.account_id = a.id
    WHERE a.id = p_account_id AND a.enabled;
END;
$$;

REVOKE ALL ON FUNCTION replica.read_full_account_profile(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION replica.read_full_account_profile(uuid) TO "${backendLogin}";
