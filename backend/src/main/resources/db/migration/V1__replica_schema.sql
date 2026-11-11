-- Replica / Fase 2 / v1.0 / 2026-11-10
-- Migración inicial para PostgreSQL 18. Ejecutar con Flyway o psql -1.
-- Aprovisionar replica_app y replica_academic como roles de permisos separados,
-- sin privilegios de dueño, SUPERUSER ni BYPASSRLS. Un tercer rol posee el esquema.
-- Sólo el backend autenticado debe poder usar replica_app. En cada transacción,
-- antes de las consultas, establece ambos valores con parámetros JDBC:
-- SELECT set_config('replica.university_id', ?, true),
--        set_config('replica.student_id', ?, true);
-- Se derivan de la sesión validada, nunca de campos enviados por el cliente.
-- El proceso académico autorizado usa replica_academic y fija el contexto
-- transaccional de su universidad. El rol de estudiante no hereda ese permiso.

CREATE SCHEMA replica;
REVOKE ALL ON SCHEMA replica FROM PUBLIC;
GRANT USAGE ON SCHEMA replica TO replica_app, replica_academic;

CREATE TABLE replica.university (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code text NOT NULL UNIQUE CHECK (btrim(code) <> ''),
    name text NOT NULL CHECK (btrim(name) <> ''),
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE replica.student (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    university_id uuid NOT NULL REFERENCES replica.university (id) ON DELETE RESTRICT,
    alias text NOT NULL CHECK (btrim(alias) <> ''),
    avatar_url text,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (university_id, id)
);

CREATE TABLE replica.subject (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    university_id uuid NOT NULL REFERENCES replica.university (id) ON DELETE RESTRICT,
    code text NOT NULL CHECK (btrim(code) <> ''),
    name text NOT NULL CHECK (btrim(name) <> ''),
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (university_id, id),
    UNIQUE (university_id, code)
);

CREATE TABLE replica.offering (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    university_id uuid NOT NULL,
    subject_id uuid NOT NULL,
    term_code text NOT NULL CHECK (btrim(term_code) <> ''),
    section_code text NOT NULL CHECK (btrim(section_code) <> ''),
    enrollment_opened_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (university_id, id),
    UNIQUE (university_id, subject_id, term_code, section_code),
    FOREIGN KEY (university_id, subject_id)
        REFERENCES replica.subject (university_id, id) ON DELETE RESTRICT
);

CREATE TABLE replica.enrollment (
    university_id uuid NOT NULL,
    student_id uuid NOT NULL,
    offering_id uuid NOT NULL,
    enrolled_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (university_id, student_id, offering_id),
    FOREIGN KEY (university_id, student_id)
        REFERENCES replica.student (university_id, id) ON DELETE RESTRICT,
    FOREIGN KEY (university_id, offering_id)
        REFERENCES replica.offering (university_id, id) ON DELETE RESTRICT
);
CREATE INDEX enrollment_by_offering
    ON replica.enrollment (university_id, offering_id);

CREATE TABLE replica.activity (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    university_id uuid NOT NULL,
    offering_id uuid NOT NULL,
    code text NOT NULL CHECK (btrim(code) <> ''),
    name text NOT NULL CHECK (btrim(name) <> ''),
    required boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (university_id, offering_id, id),
    UNIQUE (university_id, offering_id, code),
    FOREIGN KEY (university_id, offering_id)
        REFERENCES replica.offering (university_id, id) ON DELETE RESTRICT
);
CREATE INDEX activity_required_by_offering
    ON replica.activity (university_id, offering_id, id) WHERE required;

CREATE TABLE replica.activity_completion (
    university_id uuid NOT NULL,
    student_id uuid NOT NULL,
    offering_id uuid NOT NULL,
    activity_id uuid NOT NULL,
    completed_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (university_id, student_id, offering_id, activity_id),
    FOREIGN KEY (university_id, student_id, offering_id)
        REFERENCES replica.enrollment (university_id, student_id, offering_id)
        ON DELETE RESTRICT,
    FOREIGN KEY (university_id, offering_id, activity_id)
        REFERENCES replica.activity (university_id, offering_id, id) ON DELETE RESTRICT
);
CREATE INDEX activity_completion_by_activity
    ON replica.activity_completion (university_id, offering_id, activity_id);

CREATE TABLE replica.merit_definition (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    university_id uuid NOT NULL REFERENCES replica.university (id) ON DELETE RESTRICT,
    code text NOT NULL CHECK (btrim(code) <> ''),
    kind text NOT NULL CHECK (kind IN ('ACHIEVEMENT', 'BADGE')),
    scope text NOT NULL CHECK (scope IN ('PERMANENT', 'OFFERING')),
    title text NOT NULL CHECK (btrim(title) <> ''),
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (university_id, id, scope),
    UNIQUE (university_id, code)
);

CREATE TABLE replica.student_merit (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    university_id uuid NOT NULL,
    student_id uuid NOT NULL,
    merit_id uuid NOT NULL,
    scope text NOT NULL,
    offering_id uuid,
    source_key text NOT NULL CHECK (
        source_key = btrim(source_key) AND char_length(source_key) BETWEEN 1 AND 200
    ),
    awarded_at timestamptz NOT NULL DEFAULT now(),
    CHECK (scope = 'PERMANENT' OR (scope = 'OFFERING' AND offering_id IS NOT NULL)),
    UNIQUE (university_id, student_id, source_key),
    FOREIGN KEY (university_id, student_id)
        REFERENCES replica.student (university_id, id) ON DELETE RESTRICT,
    FOREIGN KEY (university_id, merit_id, scope)
        REFERENCES replica.merit_definition (university_id, id, scope) ON DELETE RESTRICT,
    FOREIGN KEY (university_id, student_id, offering_id)
        REFERENCES replica.enrollment (university_id, student_id, offering_id)
        ON DELETE RESTRICT
);
-- Una distinción permanente puede conservar la edición de origen sin repetirse.
CREATE UNIQUE INDEX student_merit_permanent_once
    ON replica.student_merit (university_id, student_id, merit_id) WHERE scope = 'PERMANENT';
CREATE UNIQUE INDEX student_merit_once_per_offering
    ON replica.student_merit (university_id, student_id, merit_id, offering_id)
    WHERE scope = 'OFFERING';
CREATE INDEX student_merit_by_definition
    ON replica.student_merit (university_id, merit_id, scope);
CREATE INDEX student_merit_by_enrollment
    ON replica.student_merit (university_id, student_id, offering_id)
    WHERE offering_id IS NOT NULL;
CREATE INDEX student_merit_by_community_profile
    ON replica.student_merit (student_id, awarded_at DESC, id);

CREATE TABLE replica.wallet (
    university_id uuid NOT NULL,
    student_id uuid NOT NULL,
    balance bigint NOT NULL DEFAULT 0 CHECK (balance >= 0),
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (university_id, student_id),
    FOREIGN KEY (university_id, student_id)
        REFERENCES replica.student (university_id, id) ON DELETE RESTRICT
);

-- Cada abono positivo pertenece al mérito del mismo origen y al saldo abonado.
CREATE TABLE replica.point_credit (
    university_id uuid NOT NULL,
    student_id uuid NOT NULL,
    source_key text NOT NULL,
    points bigint NOT NULL CHECK (points > 0),
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (university_id, student_id, source_key),
    FOREIGN KEY (university_id, student_id, source_key)
        REFERENCES replica.student_merit (university_id, student_id, source_key)
        ON DELETE RESTRICT,
    FOREIGN KEY (university_id, student_id)
        REFERENCES replica.wallet (university_id, student_id) ON DELETE RESTRICT
);

CREATE TABLE replica.store_item (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    university_id uuid NOT NULL REFERENCES replica.university (id) ON DELETE RESTRICT,
    sku text NOT NULL CHECK (btrim(sku) <> ''),
    name text NOT NULL CHECK (btrim(name) <> ''),
    points_price bigint NOT NULL CHECK (points_price > 0),
    stock integer NOT NULL DEFAULT 0 CHECK (stock >= 0),
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (university_id, id),
    UNIQUE (university_id, sku)
);
CREATE INDEX store_item_active_catalog
    ON replica.store_item (university_id, name, id) WHERE active;

-- El UUID de esta fila es el código del cupón digital.
CREATE TABLE replica.redemption (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    university_id uuid NOT NULL,
    student_id uuid NOT NULL,
    item_id uuid NOT NULL,
    idempotency_key uuid NOT NULL,
    item_name text NOT NULL CHECK (btrim(item_name) <> ''),
    points_charged bigint NOT NULL CHECK (points_charged > 0),
    status text NOT NULL DEFAULT 'PENDING_PICKUP' CHECK (status = 'PENDING_PICKUP'),
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (university_id, student_id, idempotency_key),
    FOREIGN KEY (university_id, student_id)
        REFERENCES replica.wallet (university_id, student_id) ON DELETE RESTRICT,
    FOREIGN KEY (university_id, item_id)
        REFERENCES replica.store_item (university_id, id) ON DELETE RESTRICT
);
CREATE INDEX redemption_by_student
    ON replica.redemption (university_id, student_id, created_at DESC, id);
CREATE INDEX redemption_by_item
    ON replica.redemption (university_id, item_id);

CREATE FUNCTION replica.current_university_id() RETURNS uuid
LANGUAGE sql STABLE
AS $$
    SELECT nullif(current_setting('replica.university_id', true), '')::uuid
$$;

CREATE FUNCTION replica.current_student_id() RETURNS uuid
LANGUAGE sql STABLE
AS $$
    SELECT nullif(current_setting('replica.student_id', true), '')::uuid
$$;

ALTER TABLE replica.university ENABLE ROW LEVEL SECURITY;
ALTER TABLE replica.student ENABLE ROW LEVEL SECURITY;
ALTER TABLE replica.subject ENABLE ROW LEVEL SECURITY;
ALTER TABLE replica.offering ENABLE ROW LEVEL SECURITY;
ALTER TABLE replica.enrollment ENABLE ROW LEVEL SECURITY;
ALTER TABLE replica.activity ENABLE ROW LEVEL SECURITY;
ALTER TABLE replica.activity_completion ENABLE ROW LEVEL SECURITY;
ALTER TABLE replica.merit_definition ENABLE ROW LEVEL SECURITY;
ALTER TABLE replica.student_merit ENABLE ROW LEVEL SECURITY;
ALTER TABLE replica.wallet ENABLE ROW LEVEL SECURITY;
ALTER TABLE replica.point_credit ENABLE ROW LEVEL SECURITY;
ALTER TABLE replica.store_item ENABLE ROW LEVEL SECURITY;
ALTER TABLE replica.redemption ENABLE ROW LEVEL SECURITY;

CREATE POLICY own_university ON replica.university FOR SELECT TO replica_app
    USING (id = replica.current_university_id());
CREATE POLICY own_student ON replica.student FOR SELECT TO replica_app
    USING (university_id = replica.current_university_id() AND id = replica.current_student_id());
CREATE POLICY own_subjects ON replica.subject FOR SELECT TO replica_app
    USING (university_id = replica.current_university_id());
CREATE POLICY own_offerings ON replica.offering FOR SELECT TO replica_app
    USING (university_id = replica.current_university_id());
CREATE POLICY own_enrollments ON replica.enrollment FOR SELECT TO replica_app
    USING (university_id = replica.current_university_id() AND student_id = replica.current_student_id());
CREATE POLICY own_activities ON replica.activity FOR SELECT TO replica_app
    USING (university_id = replica.current_university_id());
CREATE POLICY own_completions ON replica.activity_completion FOR SELECT TO replica_app
    USING (university_id = replica.current_university_id() AND student_id = replica.current_student_id());
CREATE POLICY own_merit_definitions ON replica.merit_definition FOR SELECT TO replica_app
    USING (university_id = replica.current_university_id());
CREATE POLICY own_merits ON replica.student_merit FOR SELECT TO replica_app
    USING (university_id = replica.current_university_id() AND student_id = replica.current_student_id());
CREATE POLICY own_wallet ON replica.wallet FOR SELECT TO replica_app
    USING (university_id = replica.current_university_id() AND student_id = replica.current_student_id());
CREATE POLICY own_credits ON replica.point_credit FOR SELECT TO replica_app
    USING (university_id = replica.current_university_id() AND student_id = replica.current_student_id());
CREATE POLICY active_local_catalog ON replica.store_item FOR SELECT TO replica_app
    USING (university_id = replica.current_university_id() AND active);
CREATE POLICY own_redemptions ON replica.redemption FOR SELECT TO replica_app
    USING (university_id = replica.current_university_id() AND student_id = replica.current_student_id());

-- Respeta las políticas de quien consulta. NULL significa que no hay requisitos.
CREATE VIEW replica.student_progress WITH (security_invoker = true) AS
SELECT e.university_id, e.student_id, e.offering_id,
       count(a.id) AS required_count,
       count(c.activity_id) AS completed_count,
       round(100.0 * count(c.activity_id) / nullif(count(a.id), 0), 2) AS progress_percent
FROM replica.enrollment AS e
LEFT JOIN replica.activity AS a
       ON (a.university_id, a.offering_id) = (e.university_id, e.offering_id)
      AND a.required
LEFT JOIN replica.activity_completion AS c
       ON (c.university_id, c.student_id, c.offering_id, c.activity_id)
        = (e.university_id, e.student_id, e.offering_id, a.id)
GROUP BY e.university_id, e.student_id, e.offering_id;

-- Vistas con permisos del dueño. Excepción deliberada a RLS y limitada
-- a estas columnas. El backend exige sesión comunitaria para exponerlas.
CREATE VIEW replica.community_profile AS
SELECT s.id AS profile_id, s.alias, s.avatar_url, u.name AS university_name
FROM replica.student AS s
JOIN replica.university AS u ON u.id = s.university_id;

CREATE VIEW replica.community_merit AS
SELECT sm.student_id AS profile_id, m.kind, m.title, sm.awarded_at
FROM replica.student_merit AS sm
JOIN replica.merit_definition AS m
  ON (m.university_id, m.id, m.scope) = (sm.university_id, sm.merit_id, sm.scope);

CREATE FUNCTION replica.guard_offering_opened_at() RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    IF OLD.enrollment_opened_at IS NOT NULL
       AND NEW.enrollment_opened_at IS DISTINCT FROM OLD.enrollment_opened_at THEN
        RAISE EXCEPTION USING ERRCODE = 'P1201', MESSAGE = 'enrollment_opening_is_irreversible';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER offering_opened_at_is_immutable
BEFORE UPDATE OF enrollment_opened_at ON replica.offering
FOR EACH ROW EXECUTE FUNCTION replica.guard_offering_opened_at();

CREATE FUNCTION replica.guard_required_activity() RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog, pg_temp
AS $$
DECLARE
    tenant uuid;
    edition uuid;
    affects_required boolean;
    opened_at timestamptz;
BEGIN
    IF TG_OP = 'DELETE' THEN
        tenant := OLD.university_id;
        edition := OLD.offering_id;
        affects_required := OLD.required;
    ELSE
        tenant := NEW.university_id;
        edition := NEW.offering_id;
        affects_required := NEW.required;
        IF TG_OP = 'UPDATE' THEN
            IF (NEW.university_id, NEW.offering_id, NEW.id)
               IS DISTINCT FROM (OLD.university_id, OLD.offering_id, OLD.id) THEN
                RAISE EXCEPTION USING ERRCODE = 'P1202', MESSAGE = 'activity_identity_is_immutable';
            END IF;
            affects_required := OLD.required OR NEW.required;
        END IF;
    END IF;

    IF affects_required THEN
        -- Comparte el bloqueo de la edición con la apertura de matrícula.
        SELECT o.enrollment_opened_at INTO opened_at
        FROM replica.offering AS o
        WHERE o.university_id = tenant AND o.id = edition
        FOR NO KEY UPDATE;
        IF NOT FOUND THEN
            RAISE EXCEPTION USING ERRCODE = '23503', MESSAGE = 'activity_offering_not_found';
        END IF;
        IF opened_at IS NOT NULL THEN
            RAISE EXCEPTION USING ERRCODE = 'P1203', MESSAGE = 'required_activity_plan_is_frozen';
        END IF;
    END IF;

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER freeze_required_activity_plan
BEFORE INSERT OR UPDATE OR DELETE ON replica.activity
FOR EACH ROW EXECUTE FUNCTION replica.guard_required_activity();

CREATE FUNCTION replica.guard_enrollment_is_open() RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog, pg_temp
AS $$
DECLARE
    opened_at timestamptz;
BEGIN
    SELECT o.enrollment_opened_at INTO opened_at
    FROM replica.offering AS o
    WHERE o.university_id = NEW.university_id AND o.id = NEW.offering_id
    FOR SHARE;
    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = '23503', MESSAGE = 'enrollment_offering_not_found';
    END IF;
    IF opened_at IS NULL THEN
        RAISE EXCEPTION USING ERRCODE = 'P1204', MESSAGE = 'enrollment_is_not_open';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER require_open_enrollment
BEFORE INSERT OR UPDATE OF university_id, offering_id ON replica.enrollment
FOR EACH ROW EXECUTE FUNCTION replica.guard_enrollment_is_open();

CREATE FUNCTION replica.open_enrollment(p_offering_id uuid) RETURNS timestamptz
LANGUAGE plpgsql VOLATILE SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
DECLARE
    tenant uuid := replica.current_university_id();
    opened_at timestamptz;
BEGIN
    IF tenant IS NULL THEN
        RAISE EXCEPTION USING ERRCODE = 'P1101', MESSAGE = 'missing_academic_context';
    END IF;

    UPDATE replica.offering AS o
    SET enrollment_opened_at = coalesce(o.enrollment_opened_at, clock_timestamp())
    WHERE o.university_id = tenant AND o.id = p_offering_id
    RETURNING o.enrollment_opened_at INTO opened_at;
    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1205', MESSAGE = 'offering_not_found';
    END IF;
    RETURN opened_at;
END;
$$;

-- La clave de origen identifica un evento académico canónico del estudiante.
-- Leer el mérito después de bloquear el saldo permite repetir la solicitud
-- tras un commit incierto. Un mérito puede otorgarse sin puntos (p_points = 0).
CREATE FUNCTION replica.award_merit(
    p_student_id uuid, p_merit_id uuid, p_offering_id uuid, p_source_key text, p_points bigint
) RETURNS replica.student_merit
LANGUAGE plpgsql VOLATILE SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
DECLARE
    tenant uuid := replica.current_university_id();
    merit_scope text;
    credited_points bigint;
    award replica.student_merit%ROWTYPE;
BEGIN
    IF tenant IS NULL THEN
        RAISE EXCEPTION USING ERRCODE = 'P1101', MESSAGE = 'missing_academic_context';
    END IF;
    IF p_student_id IS NULL OR p_merit_id IS NULL OR p_points IS NULL OR p_points < 0
       OR p_source_key IS NULL OR p_source_key <> btrim(p_source_key)
       OR char_length(p_source_key) NOT BETWEEN 1 AND 200 THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_award_request';
    END IF;

    PERFORM 1 FROM replica.wallet AS w
    WHERE w.university_id = tenant AND w.student_id = p_student_id
    FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1002', MESSAGE = 'wallet_not_found';
    END IF;

    SELECT sm.* INTO award
    FROM replica.student_merit AS sm
    WHERE sm.university_id = tenant AND sm.student_id = p_student_id
      AND sm.source_key = p_source_key;
    IF FOUND THEN
        SELECT c.points INTO credited_points FROM replica.point_credit AS c
        WHERE c.university_id = tenant AND c.student_id = p_student_id
          AND c.source_key = p_source_key;
        IF award.merit_id <> p_merit_id OR award.offering_id IS DISTINCT FROM p_offering_id
           OR coalesce(credited_points, 0) <> p_points THEN
            RAISE EXCEPTION USING ERRCODE = 'P1103', MESSAGE = 'academic_source_conflict';
        END IF;
        RETURN award;
    END IF;

    SELECT m.scope INTO merit_scope
    FROM replica.merit_definition AS m
    WHERE m.university_id = tenant AND m.id = p_merit_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1102', MESSAGE = 'merit_not_found';
    END IF;
    IF merit_scope = 'OFFERING' AND p_offering_id IS NULL THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'merit_requires_offering';
    END IF;

    INSERT INTO replica.student_merit (
        university_id, student_id, merit_id, scope, offering_id, source_key
    ) VALUES (
        tenant, p_student_id, p_merit_id, merit_scope, p_offering_id, p_source_key
    )
    ON CONFLICT DO NOTHING
    RETURNING * INTO award;
    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1104', MESSAGE = 'merit_already_awarded';
    END IF;

    IF p_points > 0 THEN
        INSERT INTO replica.point_credit (university_id, student_id, source_key, points)
        VALUES (tenant, p_student_id, p_source_key, p_points);
        UPDATE replica.wallet SET balance = balance + p_points, updated_at = now()
        WHERE university_id = tenant AND student_id = p_student_id;
    END IF;
    RETURN award;
END;
$$;

-- Canje unitario. Ejecutar a READ COMMITTED. Orden de bloqueo: saldo → artículo.
-- Toda operación futura que toque ambos debe respetar ese orden.
-- La función tiene permisos del dueño y por eso acota cada consulta al contexto.
CREATE FUNCTION replica.redeem_item(
    p_item_id uuid, p_idempotency_key uuid, p_expected_points bigint
) RETURNS replica.redemption
LANGUAGE plpgsql VOLATILE SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
DECLARE
    tenant uuid := replica.current_university_id();
    actor uuid := replica.current_student_id();
    available_points bigint;
    item replica.store_item%ROWTYPE;
    coupon replica.redemption%ROWTYPE;
BEGIN
    IF tenant IS NULL OR actor IS NULL THEN
        RAISE EXCEPTION USING ERRCODE = 'P1001', MESSAGE = 'missing_student_context';
    END IF;
    IF p_item_id IS NULL OR p_idempotency_key IS NULL
       OR p_expected_points IS NULL OR p_expected_points <= 0 THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'invalid_redemption_request';
    END IF;

    SELECT w.balance INTO available_points
    FROM replica.wallet AS w
    WHERE w.university_id = tenant AND w.student_id = actor
    FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1002', MESSAGE = 'wallet_not_found';
    END IF;

    -- El segundo envío espera al primero en el saldo y lee el cupón ya confirmado.
    SELECT r.* INTO coupon
    FROM replica.redemption AS r
    WHERE r.university_id = tenant AND r.student_id = actor
      AND r.idempotency_key = p_idempotency_key;
    IF FOUND THEN
        IF coupon.item_id <> p_item_id OR coupon.points_charged <> p_expected_points THEN
            RAISE EXCEPTION USING ERRCODE = 'P1003', MESSAGE = 'idempotency_key_conflict';
        END IF;
        RETURN coupon;
    END IF;

    SELECT i.* INTO item
    FROM replica.store_item AS i
    WHERE i.university_id = tenant AND i.id = p_item_id AND i.active
    FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P1004', MESSAGE = 'item_not_found';
    END IF;
    IF item.points_price <> p_expected_points THEN
        RAISE EXCEPTION USING ERRCODE = 'P1005', MESSAGE = 'price_changed';
    END IF;
    IF item.stock = 0 THEN
        RAISE EXCEPTION USING ERRCODE = 'P1006', MESSAGE = 'out_of_stock';
    END IF;
    IF available_points < item.points_price THEN
        RAISE EXCEPTION USING ERRCODE = 'P1007', MESSAGE = 'insufficient_points';
    END IF;

    UPDATE replica.store_item SET stock = stock - 1
    WHERE university_id = tenant AND id = item.id;
    UPDATE replica.wallet SET balance = balance - item.points_price, updated_at = now()
    WHERE university_id = tenant AND student_id = actor;

    INSERT INTO replica.redemption (
        university_id, student_id, item_id, idempotency_key, item_name, points_charged
    ) VALUES (
        tenant, actor, item.id, p_idempotency_key, item.name, item.points_price
    ) RETURNING * INTO coupon;
    RETURN coupon;
END;
$$;

REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA replica FROM PUBLIC;
GRANT EXECUTE ON FUNCTION replica.current_university_id(), replica.current_student_id(),
    replica.redeem_item(uuid, uuid, bigint) TO replica_app;
GRANT EXECUTE ON FUNCTION replica.open_enrollment(uuid),
    replica.award_merit(uuid, uuid, uuid, text, bigint) TO replica_academic;
GRANT SELECT ON replica.university, replica.student, replica.subject, replica.offering, replica.enrollment,
    replica.activity, replica.activity_completion, replica.merit_definition, replica.student_merit,
    replica.wallet, replica.point_credit, replica.store_item, replica.redemption,
    replica.student_progress, replica.community_profile, replica.community_merit TO replica_app;
