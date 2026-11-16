param(
    [Parameter(Mandatory = $true)][string[]]$Login,
    [string[]]$ModeratorLogin = @(),
    [string]$PostgresBin = 'C:\Program Files\PostgreSQL\18\bin',
    [string]$DatabaseHost = 'localhost',
    [int]$Port = 5432,
    [string]$Database = 'steam_academico'
)

$ErrorActionPreference = 'Stop'
$members = @($Login | Select-Object -Unique)
$moderators = @($ModeratorLogin | Select-Object -Unique)
$allAccounts = @($members + $moderators | Select-Object -Unique)
foreach ($name in $allAccounts) {
    if ($name -cnotmatch '^[a-z0-9][a-z0-9._@-]{2,79}$') {
        throw 'Usa identificadores de acceso existentes, normalizados en minusculas.'
    }
}
if ($members.Count -eq 0) { throw 'Indica al menos una cuenta.' }
$role = $env:REPLICA_MIGRATION_USER
$password = $env:REPLICA_MIGRATION_PASSWORD
$previousPassword = $env:PGPASSWORD
try {
    if (-not $role -and -not $password) {
        $path = Join-Path $env:LOCALAPPDATA 'ReplicaSteam/persistence/credentials.dpapi.json'
        if (-not (Test-Path -LiteralPath $path)) {
            throw 'Configura REPLICA_MIGRATION_USER y REPLICA_MIGRATION_PASSWORD de tu administrador local.'
        }
        $stored = Get-Content -LiteralPath $path -Raw | ConvertFrom-Json
        $secure = ConvertTo-SecureString $stored.replica_migrator
        $password = [Net.NetworkCredential]::new('', $secure).Password
        $secure.Dispose()
        $stored = $null
        $role = 'replica_migrator'
    }
    if (-not $role -or -not $password) { throw 'Se necesitan ambas variables de migracion.' }
    $env:PGPASSWORD = $password
    # Validation above excludes SQL quotes and separators.
    $quotedMembers = ($allAccounts | ForEach-Object { "'$_'" }) -join ','
    $quotedModerators = if ($moderators.Count) { ($moderators | ForEach-Object { "'$_'" }) -join ',' } else { "'__no_moderator__'" }
    $expected = $allAccounts.Count
    $sql = @'
BEGIN;
DO $$
BEGIN
    IF (SELECT count(*) FROM replica.access_account
        WHERE login_identifier IN (__MEMBERS__) AND enabled) <> __COUNT__ THEN
        RAISE EXCEPTION 'Todas las cuentas deben existir y estar habilitadas. No se aplicaron cambios.';
    END IF;
END;
$$;
INSERT INTO replica.community (id,slug,name,description,rules)
VALUES ('b4d90f34-ef24-4aa2-9d03-99d6f93742b8','sistemas-piloto','Piloto de Sistemas',
        'Comunidad de prueba para compartir dudas de clase. Sin respaldo institucional.',
        'Respeta a las personas. Publica dudas claras. No compartas datos privados. Utiliza solo cuentas de prueba.')
ON CONFLICT (id) DO NOTHING;
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM replica.community
        WHERE id='b4d90f34-ef24-4aa2-9d03-99d6f93742b8' AND slug='sistemas-piloto' AND active) THEN
        RAISE EXCEPTION 'El espacio piloto existente no coincide o esta desactivado.';
    END IF;
END;
$$;
INSERT INTO replica.community_membership (community_id,account_id,role)
SELECT 'b4d90f34-ef24-4aa2-9d03-99d6f93742b8',id,
       CASE WHEN login_identifier IN (__MODERATORS__) THEN 'MODERATOR' ELSE 'MEMBER' END
FROM replica.access_account WHERE login_identifier IN (__MEMBERS__) AND enabled
ON CONFLICT (community_id,account_id) DO UPDATE SET
    role=CASE WHEN EXCLUDED.role='MODERATOR' THEN 'MODERATOR'
              WHEN replica.community_membership.role IN ('MODERATOR','ADMIN') THEN replica.community_membership.role
              ELSE 'MEMBER' END,
    active=true;
COMMIT;
'@
    $sql = $sql.Replace('__MEMBERS__', $quotedMembers).Replace('__MODERATORS__', $quotedModerators).Replace('__COUNT__', [string]$expected)
    $options = @('-X', '-w', '-v', 'ON_ERROR_STOP=1', '-h', $DatabaseHost,
                 '-p', [string]$Port, '-U', $role, '-d', $Database)
    $sql | & (Join-Path $PostgresBin 'psql.exe') @options
    if ($LASTEXITCODE -ne 0) { throw 'No se completo el alta del piloto. Revisa la salida de PostgreSQL.' }
    Write-Output "Piloto preparado. Membresias activas solicitadas: $expected; moderadores promovidos: $($moderators.Count)."
    Write-Output 'Las cuentas ya conectadas pueden pulsar Actualizar comunidades.'
} finally {
    $env:PGPASSWORD = $previousPassword
    $password = $null
}
