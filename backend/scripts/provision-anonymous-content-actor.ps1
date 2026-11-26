<#
.SYNOPSIS
    Aprovisionamiento idempotente de la identidad técnica anónima (steambeta.content.anonymous)
    para conservar los aportes compartidos de estudiantes eliminados físicamente.
.DESCRIPTION
    Crea (si no existe) o valida (si ya existe) exactamente una cuenta técnica anónima:
      - login_identifier = 'steambeta.content.anonymous'
      - alias            = 'Usuario eliminado'
      - global_role      = 'USER'
      - enabled          = FALSE
      - email            = NULL
      - email_verified   = FALSE
      - university_id    = NULL
      - student_id       = NULL
      - Sin avatar, biografía, universidad, carrera, semestre ni intereses.
    Genera password_hash con PasswordEncoderFactories.createDelegatingPasswordEncoder()
    a partir de 256 bits aleatorios (SecureRandom) y descarta la contraseña inmediatamente.
    Ejecuta únicamente con conexión DBA autorizada (fuera de Flyway).
#>
param(
    [string]$PostgresBin = 'C:\Program Files\PostgreSQL\18\bin',
    [string]$DatabaseHost = 'localhost',
    [int]$Port = 5432,
    [string]$Database = 'steam_academico'
)

$ErrorActionPreference = 'Stop'

function New-SpringDelegatingHash {
    $m2 = Join-Path $env:USERPROFILE '.m2\repository'
    $jars = @(
        Get-ChildItem -Path (Join-Path $m2 'org\springframework\security\spring-security-crypto') -Recurse -Filter '*.jar' -ErrorAction SilentlyContinue
        Get-ChildItem -Path (Join-Path $m2 'commons-logging\commons-logging') -Recurse -Filter '*.jar' -ErrorAction SilentlyContinue
        Get-ChildItem -Path (Join-Path $m2 'org\springframework\spring-jcl') -Recurse -Filter '*.jar' -ErrorAction SilentlyContinue
    ) | Where-Object { $_ -and $_.Name -notlike '*-sources.jar' -and $_.Name -notlike '*-javadoc.jar' } |
        Select-Object -ExpandProperty FullName -Unique

    if (-not $jars -or $jars.Count -eq 0) {
        throw 'No se encontró spring-security-crypto en el repositorio Maven local.'
    }

    $tmpDir = Join-Path $env:TEMP ("steambeta_anon_hash_" + [Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $tmpDir -Force | Out-Null
    try {
        $javaFile = Join-Path $tmpDir 'GenAnonHash.java'
        $javaCode = @'
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

public class GenAnonHash {
    public static void main(String[] args) {
        byte[] entropy = new byte[32];
        new SecureRandom().nextBytes(entropy);
        String ephemeral = Base64.getEncoder().encodeToString(entropy);
        Arrays.fill(entropy, (byte) 0);
        PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        String hash = encoder.encode(ephemeral);
        ephemeral = null;
        System.out.print(hash);
    }
}
'@
        Set-Content -LiteralPath $javaFile -Value $javaCode -Encoding ASCII
        $cp = ($jars -join ';')
        & javac -encoding UTF-8 -cp $cp -d $tmpDir $javaFile
        if ($LASTEXITCODE -ne 0) { throw 'Error compilando GenAnonHash.' }
        $hash = & java -cp ("$tmpDir;$cp") GenAnonHash
        if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($hash) -or -not $hash.StartsWith('{bcrypt}')) {
            throw 'Hash delegating inválido.'
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
        throw 'Se requieren credenciales DBA (REPLICA_MIGRATION_USER/PASSWORD o replica_migrator en DPAPI).'
    }

    $hash = New-SpringDelegatingHash
    $env:PGPASSWORD = $password

    $sql = @'
BEGIN;

DO $$
DECLARE
    v_count integer;
    v_id uuid;
    v_role text;
    v_enabled boolean;
    v_uni uuid;
    v_stu uuid;
    v_email text;
    v_email_ver boolean;
    v_alias text;
    v_hash text := current_setting('steambeta.anon_provision_hash', true);
BEGIN
    SELECT count(*) INTO v_count
    FROM replica.access_account
    WHERE login_identifier = 'steambeta.content.anonymous';

    IF v_count > 1 THEN
        RAISE EXCEPTION 'Registro ambiguo para steambeta.content.anonymous.';
    END IF;

    IF v_count = 1 THEN
        SELECT a.id, a.global_role, a.enabled, a.university_id, a.student_id, a.email, a.email_verified, ap.alias
        INTO v_id, v_role, v_enabled, v_uni, v_stu, v_email, v_email_ver, v_alias
        FROM replica.access_account a
        LEFT JOIN replica.account_profile ap ON ap.account_id = a.id
        WHERE a.login_identifier = 'steambeta.content.anonymous';

        IF v_role <> 'USER'
           OR v_enabled IS DISTINCT FROM false
           OR v_uni IS NOT NULL
           OR v_stu IS NOT NULL
           OR v_email IS NOT NULL
           OR v_email_ver IS DISTINCT FROM false
           OR v_alias IS DISTINCT FROM 'Usuario eliminado' THEN
            RAISE EXCEPTION 'Registro inconsistente para steambeta.content.anonymous (id=%, role=%, enabled=%, alias=%).',
                v_id, v_role, v_enabled, v_alias;
        END IF;
    ELSE
        IF v_hash IS NULL OR length(v_hash) < 20 THEN
            RAISE EXCEPTION 'Hash de contraseña no provisto.';
        END IF;

        INSERT INTO replica.access_account (
            login_identifier, password_hash, enabled, university_id, student_id,
            global_role, email, email_verified
        )
        VALUES (
            'steambeta.content.anonymous', v_hash, false, NULL, NULL,
            'USER', NULL, false
        )
        RETURNING id INTO v_id;

        UPDATE replica.account_profile
        SET alias = 'Usuario eliminado',
            declared_university = NULL,
            declared_career = NULL,
            declared_semester = NULL,
            bio = NULL,
            avatar_url = NULL,
            academic_interests = NULL,
            email_public = false
        WHERE account_id = v_id;
    END IF;
END $$;

COMMIT;

SELECT a.id, a.login_identifier, a.global_role, a.enabled, ap.alias
FROM replica.access_account a
JOIN replica.account_profile ap ON ap.account_id = a.id
WHERE a.login_identifier = 'steambeta.content.anonymous';
'@

    $sessionSql = "SELECT set_config('steambeta.anon_provision_hash', '$hash', false);`n" + $sql
    $psql = Join-Path $PostgresBin 'psql.exe'
    $psqlArgs = @('-X', '-w', '-v', 'ON_ERROR_STOP=1', '-h', $DatabaseHost, '-p', [string]$Port, '-U', $role, '-d', $Database, '-t', '-A', '-F', '|')
    $output = $sessionSql | & $psql @psqlArgs
    if ($LASTEXITCODE -ne 0) {
        throw "Error aprovisionando steambeta.content.anonymous (código: $LASTEXITCODE)."
    }

    $resultLine = $output | Where-Object { $_ -match '^[0-9a-fA-F-]{36}\|steambeta\.content\.anonymous\|USER\|f\|Usuario eliminado$' } | Select-Object -Last 1
    if (-not $resultLine) {
        throw 'No se pudo verificar la identidad técnica anónima tras la transacción.'
    }
    $parts = $resultLine -split '\|'
    Write-Host "[OK] Identidad técnica anónima verificada en PostgreSQL." -ForegroundColor Green
    Write-Host "ANONYMOUS_ACTOR_ID=$($parts[0]) | LOGIN=$($parts[1]) | ROLE=$($parts[2]) | ENABLED=$($parts[3]) | ALIAS=$($parts[4])"
} finally {
    $env:PGPASSWORD = $previousPassword
    $password = $null
    $hash = $null
}
