<#
.SYNOPSIS
    Aprovisionamiento idempotente de la única identidad técnica administrativa compartida de SteamBeta Admin.
.DESCRIPTION
    Crea (si no existe) o valida (si ya existe) exactamente una cuenta técnica interna en PostgreSQL:
      - login_identifier = 'steambeta.admin.system'
      - alias            = 'SteamBeta Admin - Sistema'
      - global_role      = 'ADMIN'
      - enabled          = TRUE
      - university_id    = NULL
      - student_id       = NULL
      - email            = NULL
      - email_verified   = FALSE
    Genera el password_hash con Spring Security PasswordEncoderFactories.createDelegatingPasswordEncoder()
    a partir de 256 bits aleatorios (SecureRandom) y descarta la contraseña inmediatamente sin mostrarla ni guardarla.
    Ejecuta únicamente operaciones de datos en una transacción con conexión DBA (NO usa Flyway ni modifica esquema).
#>
param(
    [string]$PostgresBin = 'C:\Program Files\PostgreSQL\18\bin',
    [string]$DatabaseHost = 'localhost',
    [int]$Port = 5432,
    [string]$Database = 'steam_academico'
)

$ErrorActionPreference = 'Stop'

$ReservedLogin = 'steambeta.admin.system'
$InternalAlias = 'SteamBeta Admin - Sistema'

function New-SpringDelegatingHash {
    $m2 = Join-Path $env:USERPROFILE '.m2\repository'
    $jars = @(
        Get-ChildItem -Path (Join-Path $m2 'org\springframework\security\spring-security-crypto') -Recurse -Filter '*.jar' -ErrorAction SilentlyContinue
        Get-ChildItem -Path (Join-Path $m2 'commons-logging\commons-logging') -Recurse -Filter '*.jar' -ErrorAction SilentlyContinue
        Get-ChildItem -Path (Join-Path $m2 'org\springframework\spring-jcl') -Recurse -Filter '*.jar' -ErrorAction SilentlyContinue
    ) | Where-Object { $_ -and $_.Name -notlike '*-sources.jar' -and $_.Name -notlike '*-javadoc.jar' } |
        Select-Object -ExpandProperty FullName -Unique

    if (-not $jars -or $jars.Count -eq 0) {
        throw 'No se encontró spring-security-crypto en el repositorio Maven local. Compila el backend primero.'
    }

    $tmpDir = Join-Path $env:TEMP ("steambeta_hash_" + [Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $tmpDir -Force | Out-Null
    try {
        $javaFile = Join-Path $tmpDir 'GenAdminHash.java'
        $javaCode = @'
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

public class GenAdminHash {
    public static void main(String[] args) {
        byte[] entropy = new byte[32]; // 256 bits criptográficamente aleatorios
        new SecureRandom().nextBytes(entropy);
        String ephemeralSecret = Base64.getEncoder().encodeToString(entropy);
        Arrays.fill(entropy, (byte) 0);
        PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        String hash = encoder.encode(ephemeralSecret);
        ephemeralSecret = null;
        System.out.print(hash);
    }
}
'@
        Set-Content -LiteralPath $javaFile -Value $javaCode -Encoding ASCII
        $cp = ($jars -join ';')
        & javac -encoding UTF-8 -cp $cp -d $tmpDir $javaFile
        if ($LASTEXITCODE -ne 0) { throw 'No se pudo compilar el generador de PasswordEncoder.' }

        $hash = & java -cp ("$tmpDir;$cp") GenAdminHash
        if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($hash) -or -not $hash.StartsWith('{bcrypt}')) {
            throw 'El generador de PasswordEncoder no produjo un hash delegating válido.'
        }
        return $hash.Trim()
    } finally {
        Remove-Item -LiteralPath $tmpDir -Recurse -Force -ErrorAction SilentlyContinue
    }
}

$role = $env:REPLICA_MIGRATION_USER
$password = $env:REPLICA_MIGRATION_PASSWORD
$previousPassword = $env:PGPASSWORD
$hash = $null

try {
    if (-not $role -and -not $password) {
        $storePath = Join-Path $env:LOCALAPPDATA 'ReplicaSteam/persistence/credentials.dpapi.json'
        if (Test-Path -LiteralPath $storePath) {
            $stored = Get-Content -LiteralPath $storePath -Raw | ConvertFrom-Json
            if ($stored.PSObject.Properties['replica_migrator']) {
                $secure = ConvertTo-SecureString $stored.replica_migrator
                $password = [Net.NetworkCredential]::new('', $secure).Password
                $secure.Dispose()
                $role = 'replica_migrator'
            }
        }
    }

    if (-not $role -or -not $password) {
        throw 'Se requieren credenciales DBA autorizadas (REPLICA_MIGRATION_USER / REPLICA_MIGRATION_PASSWORD o replica_migrator en DPAPI).'
    }

    $hash = New-SpringDelegatingHash
    $env:PGPASSWORD = $password

    $sql = @'
BEGIN;

DO $$
DECLARE
    v_count integer;
    v_id uuid;
    v_login text;
    v_role text;
    v_enabled boolean;
    v_uni uuid;
    v_stu uuid;
    v_email text;
    v_email_ver boolean;
    v_alias text;
    v_hash text := current_setting('steambeta.provision_hash', true);
BEGIN
    SELECT count(*) INTO v_count
    FROM replica.access_account
    WHERE login_identifier = 'steambeta.admin.system'
       OR global_role = 'ADMIN';

    IF v_count > 1 THEN
        RAISE EXCEPTION 'Registro ambiguo: existen % cuentas que coinciden con identidad ADMIN o login reservado.', v_count;
    END IF;

    IF v_count = 1 THEN
        SELECT a.id, a.login_identifier, a.global_role, a.enabled,
               a.university_id, a.student_id, a.email, a.email_verified, ap.alias
        INTO v_id, v_login, v_role, v_enabled, v_uni, v_stu, v_email, v_email_ver, v_alias
        FROM replica.access_account a
        LEFT JOIN replica.account_profile ap ON ap.account_id = a.id
        WHERE a.login_identifier = 'steambeta.admin.system'
           OR a.global_role = 'ADMIN';

        IF v_login <> 'steambeta.admin.system'
           OR v_role <> 'ADMIN'
           OR v_enabled IS DISTINCT FROM true
           OR v_uni IS NOT NULL
           OR v_stu IS NOT NULL
           OR v_email IS NOT NULL
           OR v_email_ver IS DISTINCT FROM false
           OR v_alias IS DISTINCT FROM 'SteamBeta Admin - Sistema' THEN
            RAISE EXCEPTION 'Registro inconsistente para identidad técnica ADMIN (id=%, login=%, role=%, enabled=%, alias=%). Abortando sin modificar datos.',
                v_id, v_login, v_role, v_enabled, v_alias;
        END IF;
    ELSE
        IF v_hash IS NULL OR length(v_hash) < 20 THEN
            RAISE EXCEPTION 'Hash de contraseña no provisto para aprovisionamiento.';
        END IF;

        INSERT INTO replica.access_account (
            login_identifier,
            password_hash,
            enabled,
            university_id,
            student_id,
            global_role,
            email,
            email_verified
        )
        VALUES (
            'steambeta.admin.system',
            v_hash,
            true,
            NULL,
            NULL,
            'ADMIN',
            NULL,
            false
        )
        RETURNING id INTO v_id;

        -- El trigger initialize_account_profile de V3 crea la fila en replica.account_profile
        UPDATE replica.account_profile
        SET alias = 'SteamBeta Admin - Sistema'
        WHERE account_id = v_id;
    END IF;

    -- Verificar que la función V11 require_global_moderator acepta el actor técnico ADMIN
    PERFORM replica.require_global_moderator(v_id);
END $$;

COMMIT;

SELECT a.id AS technical_actor_id,
       a.global_role,
       a.enabled,
       ap.alias,
       (a.university_id IS NULL AND a.student_id IS NULL) AS academic_links_null,
       (a.email IS NULL AND a.email_verified = false) AS email_unset
FROM replica.access_account a
JOIN replica.account_profile ap ON ap.account_id = a.id
WHERE a.login_identifier = 'steambeta.admin.system'
  AND a.global_role = 'ADMIN';
'@

    $psql = Join-Path $PostgresBin 'psql.exe'
    $options = @(
        '-X', '-w', '-v', 'ON_ERROR_STOP=1',
        '-h', $DatabaseHost, '-p', [string]$Port,
        '-U', $role, '-d', $Database,
        '-c', "SET steambeta.provision_hash = '$hash';"
    )

    # Ejecutar todo en una sola sesión psql para preservar la variable de sesión de configuración
    $fullSql = "SET LOCAL steambeta.provision_hash = '$hash';"
    $sessionSql = "SELECT set_config('steambeta.provision_hash', '$hash', false);`n" + $sql

    $psqlArgs = @('-X', '-w', '-v', 'ON_ERROR_STOP=1', '-h', $DatabaseHost, '-p', [string]$Port, '-U', $role, '-d', $Database, '-t', '-A', '-F', '|')
    $output = $sessionSql | & $psql @psqlArgs
    if ($LASTEXITCODE -ne 0) {
        throw "Error al aprovisionar la identidad técnica en PostgreSQL (código: $LASTEXITCODE)."
    }

    $resultLine = $output | Where-Object { $_ -match '^[0-9a-fA-F-]{36}\|ADMIN\|t\|' } | Select-Object -Last 1
    if (-not $resultLine) {
        throw 'No se pudo verificar la fila de la identidad técnica tras la transacción.'
    }

    $parts = $resultLine -split '\|'
    $actorUuid = $parts[0]
    Write-Host "[OK] Identidad técnica administrativa verificada en PostgreSQL." -ForegroundColor Green
    Write-Host "STEAMBETA_ADMIN_ACTOR_ID=$actorUuid"
    Write-Host "GLOBAL_ROLE=$($parts[1]) | ENABLED=$($parts[2]) | ALIAS=$($parts[3])"
} finally {
    $env:PGPASSWORD = $previousPassword
    $password = $null
    $hash = $null
}
