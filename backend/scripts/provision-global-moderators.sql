-- Script administrativo para aprovisionar a los 4 moderadores globales autorizados.
-- Ejecutar únicamente por el administrador del sistema con credenciales de replica_migrator / dba.
-- Uso: Reemplazar los marcadores 'mod.login1', 'mod.login2', etc., con los 4 identificadores definitivos.

BEGIN;

DO $$
DECLARE
    v_login_list text[] := ARRAY['mod.login1', 'mod.login2', 'mod.login3', 'mod.login4'];
    v_login text;
    v_count integer;
BEGIN
    -- Validar que la lista tenga como máximo 4 moderadores
    IF cardinality(v_login_list) > 4 THEN
        RAISE EXCEPTION 'SteamBeta solo autoriza un maximo de 4 moderadores globales iniciales.';
    END IF;

    -- Verificar que las cuentas existan
    FOREACH v_login IN ARRAY v_login_list
    LOOP
        IF NOT EXISTS (SELECT 1 FROM replica.access_account WHERE login_identifier = v_login AND enabled) THEN
            RAISE NOTICE 'La cuenta % aun no se ha registrado. Se configurara cuando el usuario complete su registro.', v_login;
        ELSE
            UPDATE replica.access_account
            SET global_role = 'GLOBAL_MODERATOR'
            WHERE login_identifier = v_login;

            RAISE NOTICE 'Cuenta % promovida exitosamente a GLOBAL_MODERATOR.', v_login;
        END IF;
    END LOOP;
END;
$$;

COMMIT;
