<#
.SYNOPSIS
    Asistente interactivo para configurar credenciales locales seguras con DPAPI en Windows.
.DESCRIPTION
    Guarda las contraseñas de replica_runtime y replica_migrator cifradas con la API DPAPI
    de Windows en el perfil local del usuario (%LOCALAPPDATA%\ReplicaSteam\persistence\credentials.dpapi.json).
    Nunca guarda contraseñas en texto claro, archivos del repositorio ni historial de comandos.
#>
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "   SteamBeta Campus · Asistente de Credenciales Locales   " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Este asistente guardara de forma segura las credenciales de PostgreSQL"
Write-Host "cifradas con DPAPI (Data Protection API) para tu usuario de Windows."
Write-Host "Solo necesitas ejecutar este paso UNA SOLA VEZ."
Write-Host ""

$localRoot = Join-Path $env:LOCALAPPDATA 'ReplicaSteam\persistence'
$credPath = Join-Path $localRoot 'credentials.dpapi.json'

# Asegurar directorio con permisos restringidos
if (-not (Test-Path -LiteralPath $localRoot)) {
    New-Item -ItemType Directory -Force -Path $localRoot | Out-Null
}

$userSid = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
try {
    & icacls.exe $localRoot /inheritance:r /grant:r "*$($userSid):(OI)(CI)F" "*S-1-5-18:(OI)(CI)F" | Out-Null
} catch {
    Write-Warning "No se pudieron verificar permisos de ACL, continuando con permisos de perfil..."
}

Write-Host "Introduce la contrasena para el rol de ejecucion (replica_runtime):" -ForegroundColor Yellow
$runtimePass = Read-Host "Contrasena de replica_runtime" -AsSecureString

Write-Host "Introduce la contrasena para el rol de migraciones (replica_migrator):" -ForegroundColor Yellow
$migratorPass = Read-Host "Contrasena de replica_migrator" -AsSecureString

if ($null -eq $runtimePass -or $null -eq $migratorPass) {
    throw "Las contrasenas no pueden estar vacias."
}

try {
    $runtimeEncrypted = ConvertFrom-SecureString $runtimePass
    $migratorEncrypted = ConvertFrom-SecureString $migratorPass

    $credObject = [ordered]@{
        "replica_runtime"  = $runtimeEncrypted
        "replica_migrator" = $migratorEncrypted
    }

    $json = $credObject | ConvertTo-Json -Depth 2
    [System.IO.File]::WriteAllText($credPath, $json, [System.Text.UTF8Encoding]::new($false))

    Write-Host ""
    Write-Host "[OK] Credenciales protegidas exitosamente con DPAPI en:" -ForegroundColor Green
    Write-Host "     $credPath" -ForegroundColor Gray
    Write-Host ""
    Write-Host "Ahora puedes iniciar SteamBeta Backend desde IntelliJ IDEA o start-local.ps1" -ForegroundColor Green
    Write-Host "sin tener que introducir las contrasenas manualmente." -ForegroundColor Green
    Write-Host ""
} finally {
    if ($runtimePass) { $runtimePass.Dispose() }
    if ($migratorPass) { $migratorPass.Dispose() }
    $runtimeEncrypted = $null
    $migratorEncrypted = $null
    $credObject = $null
}
