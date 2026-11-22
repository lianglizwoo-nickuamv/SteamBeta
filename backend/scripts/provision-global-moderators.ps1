<#
.SYNOPSIS
    Aprovisionamiento seguro de los 4 Moderadores Generales de SteamBeta Campus.
.DESCRIPTION
    Asigna el rol GLOBAL_MODERATOR a exactamente hasta 4 cuentas registradas en el sistema.
    Requiere permisos de administrador local/migrador.
#>
param(
    [Parameter(Mandatory = $true)]
    [ValidateCount(1, 4)]
    [string[]]$ModeratorLogins,
    [string]$PostgresBin = 'C:\Program Files\PostgreSQL\18\bin',
    [string]$DatabaseHost = 'localhost',
    [int]$Port = 5432,
    [string]$Database = 'steam_academico'
)

$ErrorActionPreference = 'Stop'

if ($ModeratorLogins.Count -gt 4) {
    throw 'SteamBeta limita estrictamente a cuatro (4) moderadores generales autorizados.'
}

$normalized = @($ModeratorLogins | ForEach-Object { $_.Trim().ToLowerInvariant() } | Select-Object -Unique)

Write-Host "Aprovisionando $($normalized.Count) moderador(es) global(es)..." -ForegroundColor Cyan

$role = $env:REPLICA_MIGRATION_USER
$password = $env:REPLICA_MIGRATION_PASSWORD
$previousPassword = $env:PGPASSWORD

try {
    if (-not $role -and -not $password) {
        $path = Join-Path $env:LOCALAPPDATA 'ReplicaSteam/persistence/credentials.dpapi.json'
        if (Test-Path -LiteralPath $path) {
            $stored = Get-Content -LiteralPath $path -Raw | ConvertFrom-Json
            $secure = ConvertTo-SecureString $stored.replica_migrator
            $password = [Net.NetworkCredential]::new('', $secure).Password
            $secure.Dispose()
            $role = 'replica_migrator'
        }
    }

    if (-not $password) {
        throw 'Se requieren credenciales de replica_migrator para configurar moderadores globales.'
    }

    $env:PGPASSWORD = $password
    $arraySql = ($normalized | ForEach-Object { "'$_'" }) -join ','

    $sql = @"
BEGIN;
DO `$\$
DECLARE
    v_login text;
    v_arr text[] := ARRAY[$arraySql];
BEGIN
    FOREACH v_login IN ARRAY v_arr
    LOOP
        IF NOT EXISTS (SELECT 1 FROM replica.access_account WHERE login_identifier = v_login AND enabled) THEN
            RAISE EXCEPTION 'La cuenta % debe existir y estar registrada antes de otorgar moderacion global.', v_login;
        END IF;

        UPDATE replica.access_account
        SET global_role = 'GLOBAL_MODERATOR'
        WHERE login_identifier = v_login;
    END LOOP;
END;
`$\$`$;
COMMIT;
"@

    $options = @('-X', '-w', '-v', 'ON_ERROR_STOP=1', '-h', $DatabaseHost,
                 '-p', [string]$Port, '-U', $role, '-d', $Database)
    $sql | & (Join-Path $PostgresBin 'psql.exe') @options

    if ($LASTEXITCODE -ne 0) {
        throw "Error al ejecutar asignacion en PostgreSQL (Codigo: $LASTEXITCODE)."
    }

    Write-Host "[OK] $($normalized.Count) Moderadores Globales autorizados y configurados exitosamente." -ForegroundColor Green
} finally {
    $env:PGPASSWORD = $previousPassword
    $password = $null
}
