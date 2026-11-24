-- =============================================================================
-- V16: Registro Manual de Materias por Estudiante en Biblioteca Universitaria
-- Autorizado exclusivamente para:
--   - Tabla replica.student_manual_subject (RLS habilitada y forzada)
--   - 4 funciones SECURITY DEFINER (list, create, update, delete)
-- No modifica V1-V15, tablas institucionales ni funciones existentes.
-- =============================================================================

CREATE TABLE IF NOT EXISTS replica.student_manual_subject (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id uuid NOT NULL REFERENCES replica.access_account(id) ON DELETE RESTRICT,
    name text NOT NULL CHECK (char_length(btrim(name)) BETWEEN 2 AND 120),
    code text CHECK (code IS NULL OR char_length(btrim(code)) BETWEEN 1 AND 32),
    academic_year integer NOT NULL CHECK (academic_year BETWEEN 2000 AND 2100),
    term_label text NOT NULL CHECK (char_length(btrim(term_label)) BETWEEN 1 AND 40),
    group_section text CHECK (group_section IS NULL OR char_length(btrim(group_section)) BETWEEN 1 AND 40),
    teacher_name text CHECK (teacher_name IS NULL OR char_length(btrim(teacher_name)) BETWEEN 1 AND 120),
    credits integer CHECK (credits IS NULL OR credits BETWEEN 0 AND 40),
    schedule text CHECK (schedule IS NULL OR char_length(btrim(schedule)) BETWEEN 1 AND 160),
    notes text CHECK (notes IS NULL OR char_length(btrim(notes)) BETWEEN 1 AND 1500),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at timestamptz NOT NULL DEFAULT clock_timestamp()
);

-- Índice único por estudiante, nombre normalizado, año académico y período normalizado
-- Protege también frente a solicitudes simultáneas que intenten registrar duplicados.
CREATE UNIQUE INDEX IF NOT EXISTS uq_student_manual_subject_term
    ON replica.student_manual_subject (
        account_id,
        lower(btrim(name)),
        academic_year,
        lower(btrim(term_label))
    );

CREATE INDEX IF NOT EXISTS idx_student_manual_subject_account_period
    ON replica.student_manual_subject (
        account_id,
        academic_year DESC,
        lower(btrim(term_label)) ASC,
        lower(btrim(name)) ASC
    );

ALTER TABLE replica.student_manual_subject ENABLE ROW LEVEL SECURITY;
ALTER TABLE replica.student_manual_subject FORCE ROW LEVEL SECURITY;

REVOKE ALL ON replica.student_manual_subject FROM PUBLIC, replica_app, replica_academic, replica_runtime;

DROP POLICY IF EXISTS student_manual_subject_owner_isolation ON replica.student_manual_subject;
CREATE POLICY student_manual_subject_owner_isolation
    ON replica.student_manual_subject
    FOR ALL
    TO replica_migrator
    USING (account_id = NULLIF(current_setting('replica.current_actor_id', true), '')::uuid)
    WITH CHECK (account_id = NULLIF(current_setting('replica.current_actor_id', true), '')::uuid);

-- =============================================================================
-- 1. LISTAR MATERIAS MANUALES DEL ESTUDIANTE AUTENTICADO
-- =============================================================================
CREATE OR REPLACE FUNCTION replica.list_student_manual_subjects_v16(
    p_actor uuid,
    p_academic_year integer DEFAULT NULL,
    p_term_label text DEFAULT NULL
)
RETURNS TABLE (
    id uuid,
    account_id uuid,
    name text,
    code text,
    academic_year integer,
    term_label text,
    group_section text,
    teacher_name text,
    credits integer,
    schedule text,
    notes text,
    created_at timestamptz,
    updated_at timestamptz
)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
DECLARE
    clean_term text;
BEGIN
    PERFORM 1 FROM replica.access_account a WHERE a.id = p_actor AND a.enabled FOR SHARE;
    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1401', MESSAGE = 'account_unavailable';
    END IF;

    PERFORM set_config('replica.current_actor_id', p_actor::text, true);
    clean_term := NULLIF(btrim(p_term_label), '');

    RETURN QUERY
    SELECT s.id, s.account_id, s.name, s.code, s.academic_year, s.term_label,
           s.group_section, s.teacher_name, s.credits, s.schedule, s.notes,
           s.created_at, s.updated_at
    FROM replica.student_manual_subject s
    WHERE s.account_id = p_actor
      AND (p_academic_year IS NULL OR s.academic_year = p_academic_year)
      AND (clean_term IS NULL OR lower(btrim(s.term_label)) = lower(clean_term))
    ORDER BY s.academic_year DESC, lower(btrim(s.term_label)) ASC, lower(btrim(s.name)) ASC;
END;
$$;

-- =============================================================================
-- 2. CREAR MATERIA MANUAL DEL ESTUDIANTE AUTENTICADO
-- =============================================================================
CREATE OR REPLACE FUNCTION replica.create_student_manual_subject_v16(
    p_actor uuid,
    p_name text,
    p_code text,
    p_academic_year integer,
    p_term_label text,
    p_group_section text,
    p_teacher_name text,
    p_credits integer,
    p_schedule text,
    p_notes text
)
RETURNS TABLE (
    id uuid,
    account_id uuid,
    name text,
    code text,
    academic_year integer,
    term_label text,
    group_section text,
    teacher_name text,
    credits integer,
    schedule text,
    notes text,
    created_at timestamptz,
    updated_at timestamptz
)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
DECLARE
    clean_name text;
    clean_code text;
    clean_term text;
    clean_group text;
    clean_teacher text;
    clean_schedule text;
    clean_notes text;
    saved replica.student_manual_subject%ROWTYPE;
BEGIN
    PERFORM 1 FROM replica.access_account a WHERE a.id = p_actor AND a.enabled FOR SHARE;
    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1401', MESSAGE = 'account_unavailable';
    END IF;

    clean_name := NULLIF(btrim(p_name), '');
    clean_code := NULLIF(btrim(p_code), '');
    clean_term := NULLIF(btrim(p_term_label), '');
    clean_group := NULLIF(btrim(p_group_section), '');
    clean_teacher := NULLIF(btrim(p_teacher_name), '');
    clean_schedule := NULLIF(btrim(p_schedule), '');
    clean_notes := NULLIF(btrim(p_notes), '');

    IF clean_name IS NULL OR char_length(clean_name) < 2 OR char_length(clean_name) > 120 THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_manual_subject_name';
    END IF;
    IF clean_code IS NOT NULL AND char_length(clean_code) > 32 THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_manual_subject_code';
    END IF;
    IF p_academic_year IS NULL OR p_academic_year < 2000 OR p_academic_year > 2100 THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_academic_year';
    END IF;
    IF clean_term IS NULL OR char_length(clean_term) < 1 OR char_length(clean_term) > 40 THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_term_label';
    END IF;
    IF clean_group IS NOT NULL AND char_length(clean_group) > 40 THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_group_section';
    END IF;
    IF clean_teacher IS NOT NULL AND char_length(clean_teacher) > 120 THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_teacher_name';
    END IF;
    IF p_credits IS NOT NULL AND (p_credits < 0 OR p_credits > 40) THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_credits';
    END IF;
    IF clean_schedule IS NOT NULL AND char_length(clean_schedule) > 160 THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_schedule';
    END IF;
    IF clean_notes IS NOT NULL AND char_length(clean_notes) > 1500 THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_notes';
    END IF;

    PERFORM set_config('replica.current_actor_id', p_actor::text, true);

    BEGIN
        INSERT INTO replica.student_manual_subject (
            account_id, name, code, academic_year, term_label,
            group_section, teacher_name, credits, schedule, notes
        )
        VALUES (
            p_actor, clean_name, clean_code, p_academic_year, clean_term,
            clean_group, clean_teacher, p_credits, clean_schedule, clean_notes
        )
        RETURNING * INTO saved;
    EXCEPTION
        WHEN unique_violation THEN
            RAISE EXCEPTION USING ERRCODE = 'P1409', MESSAGE = 'manual_subject_duplicate';
    END;

    RETURN QUERY
    SELECT saved.id, saved.account_id, saved.name, saved.code, saved.academic_year,
           saved.term_label, saved.group_section, saved.teacher_name, saved.credits,
           saved.schedule, saved.notes, saved.created_at, saved.updated_at;
END;
$$;

-- =============================================================================
-- 3. ACTUALIZAR MATERIA MANUAL PROPIA DEL ESTUDIANTE AUTENTICADO
-- =============================================================================
CREATE OR REPLACE FUNCTION replica.update_student_manual_subject_v16(
    p_actor uuid,
    p_subject_id uuid,
    p_name text,
    p_code text,
    p_academic_year integer,
    p_term_label text,
    p_group_section text,
    p_teacher_name text,
    p_credits integer,
    p_schedule text,
    p_notes text
)
RETURNS TABLE (
    id uuid,
    account_id uuid,
    name text,
    code text,
    academic_year integer,
    term_label text,
    group_section text,
    teacher_name text,
    credits integer,
    schedule text,
    notes text,
    created_at timestamptz,
    updated_at timestamptz
)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
DECLARE
    clean_name text;
    clean_code text;
    clean_term text;
    clean_group text;
    clean_teacher text;
    clean_schedule text;
    clean_notes text;
    saved replica.student_manual_subject%ROWTYPE;
BEGIN
    PERFORM 1 FROM replica.access_account a WHERE a.id = p_actor AND a.enabled FOR SHARE;
    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1401', MESSAGE = 'account_unavailable';
    END IF;

    clean_name := NULLIF(btrim(p_name), '');
    clean_code := NULLIF(btrim(p_code), '');
    clean_term := NULLIF(btrim(p_term_label), '');
    clean_group := NULLIF(btrim(p_group_section), '');
    clean_teacher := NULLIF(btrim(p_teacher_name), '');
    clean_schedule := NULLIF(btrim(p_schedule), '');
    clean_notes := NULLIF(btrim(p_notes), '');

    IF clean_name IS NULL OR char_length(clean_name) < 2 OR char_length(clean_name) > 120 THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_manual_subject_name';
    END IF;
    IF clean_code IS NOT NULL AND char_length(clean_code) > 32 THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_manual_subject_code';
    END IF;
    IF p_academic_year IS NULL OR p_academic_year < 2000 OR p_academic_year > 2100 THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_academic_year';
    END IF;
    IF clean_term IS NULL OR char_length(clean_term) < 1 OR char_length(clean_term) > 40 THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_term_label';
    END IF;
    IF clean_group IS NOT NULL AND char_length(clean_group) > 40 THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_group_section';
    END IF;
    IF clean_teacher IS NOT NULL AND char_length(clean_teacher) > 120 THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_teacher_name';
    END IF;
    IF p_credits IS NOT NULL AND (p_credits < 0 OR p_credits > 40) THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_credits';
    END IF;
    IF clean_schedule IS NOT NULL AND char_length(clean_schedule) > 160 THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_schedule';
    END IF;
    IF clean_notes IS NOT NULL AND char_length(clean_notes) > 1500 THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_notes';
    END IF;

    PERFORM set_config('replica.current_actor_id', p_actor::text, true);

    BEGIN
        UPDATE replica.student_manual_subject s
        SET name = clean_name,
            code = clean_code,
            academic_year = p_academic_year,
            term_label = clean_term,
            group_section = clean_group,
            teacher_name = clean_teacher,
            credits = p_credits,
            schedule = clean_schedule,
            notes = clean_notes,
            updated_at = clock_timestamp()
        WHERE s.id = p_subject_id AND s.account_id = p_actor
        RETURNING * INTO saved;
    EXCEPTION
        WHEN unique_violation THEN
            RAISE EXCEPTION USING ERRCODE = 'P1409', MESSAGE = 'manual_subject_duplicate';
    END;

    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1404', MESSAGE = 'manual_subject_not_found';
    END IF;

    RETURN QUERY
    SELECT saved.id, saved.account_id, saved.name, saved.code, saved.academic_year,
           saved.term_label, saved.group_section, saved.teacher_name, saved.credits,
           saved.schedule, saved.notes, saved.created_at, saved.updated_at;
END;
$$;

-- =============================================================================
-- 4. ELIMINAR MATERIA MANUAL PROPIA DEL ESTUDIANTE AUTENTICADO
-- =============================================================================
CREATE OR REPLACE FUNCTION replica.delete_student_manual_subject_v16(
    p_actor uuid,
    p_subject_id uuid
)
RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    PERFORM 1 FROM replica.access_account a WHERE a.id = p_actor AND a.enabled FOR SHARE;
    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1401', MESSAGE = 'account_unavailable';
    END IF;

    PERFORM set_config('replica.current_actor_id', p_actor::text, true);

    DELETE FROM replica.student_manual_subject s
    WHERE s.id = p_subject_id AND s.account_id = p_actor;

    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1404', MESSAGE = 'manual_subject_not_found';
    END IF;
END;
$$;

-- =============================================================================
-- PERMISOS ESTRICTOS DE EJECUCIÓN
-- =============================================================================
REVOKE ALL ON FUNCTION replica.list_student_manual_subjects_v16(uuid, integer, text) FROM PUBLIC;
REVOKE ALL ON FUNCTION replica.create_student_manual_subject_v16(uuid, text, text, integer, text, text, text, integer, text, text) FROM PUBLIC;
REVOKE ALL ON FUNCTION replica.update_student_manual_subject_v16(uuid, uuid, text, text, integer, text, text, text, integer, text, text) FROM PUBLIC;
REVOKE ALL ON FUNCTION replica.delete_student_manual_subject_v16(uuid, uuid) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION replica.list_student_manual_subjects_v16(uuid, integer, text) TO replica_runtime;
GRANT EXECUTE ON FUNCTION replica.create_student_manual_subject_v16(uuid, text, text, integer, text, text, text, integer, text, text) TO replica_runtime;
GRANT EXECUTE ON FUNCTION replica.update_student_manual_subject_v16(uuid, uuid, text, text, integer, text, text, text, integer, text, text) TO replica_runtime;
GRANT EXECUTE ON FUNCTION replica.delete_student_manual_subject_v16(uuid, uuid) TO replica_runtime;
