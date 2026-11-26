<#
.SYNOPSIS
    Configuración permanente en Windows de la clave compartida y el actor técnico de STEAMBETA ADMIN.
.DESCRIPTION
    Solicita la clave privada administrativa de forma oculta (Read-Host -AsSecureString),
    calcula su digestión SHA-256 hexadecimal de 64 caracteres, descarta la contraseña en texto plano
    y persiste únicamente STEAMBETA_ADMIN_KEY_SHA256 y STEAMBETA_ADMIN_ACTOR_ID en el entorno de usuario
    de Windows (HKCU\Environment) y en la sesión actual.
    No modifica archivos .env, no escribe secretos en el repositorio y no imprime el hash ni la clave.
#>
param(
    [string]$TechnicalActorId = '02037b26-f70f-41ef-98b2-8aef1094f0a6'
)

$ErrorActionPreference = 'Stop'

if (-not ($TechnicalActorId -match '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$')) {
    throw 'TechnicalActorId debe ser un UUID válido.'
}

Write-Host "=== Configuración permanente de STEAMBETA ADMIN (Entorno de usuario Windows) ===" -ForegroundColor Cyan
Write-Host "Esta operación guarda únicamente el hash SHA-256 en las variables de usuario de Windows." -ForegroundColor Gray
Write-Host "La clave original nunca se muestra, ni se guarda en archivos ni en el repositorio." -ForegroundColor Gray

$sec1 = Read-Host -Prompt "Ingresa la clave privada administrativa compartida" -AsSecureString
$sec2 = Read-Host -Prompt "Confirma la clave privada administrativa compartida" -AsSecureString

$bstr1 = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec1)
$bstr2 = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec2)
$plain1 = $null
$plain2 = $null
$bytes = $null
$hashHex = $null

try {
    $plain1 = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr1).Trim()
    $plain2 = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr2).Trim()

    if ([string]::IsNullOrWhiteSpace($plain1) -or $plain1.Length -lt 8) {
        throw 'La clave administrativa debe tener al menos 8 caracteres.'
    }
    if (-not [string]::Equals($plain1, $plain2, [StringComparison]::Ordinal)) {
        throw 'Las claves ingresadas no coinciden. Operación cancelada.'
    }

    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($plain1)
        $hashBytes = $sha.ComputeHash($bytes)
        $hashHex = ([BitConverter]::ToString($hashBytes) -replace '-', '').ToLowerInvariant()
        [Array]::Clear($hashBytes, 0, $hashBytes.Length)
    } finally {
        $sha.Dispose()
    }

    if ($hashHex.Length -ne 64) {
        throw 'Error interno al calcular SHA-256.'
    }

    # Persistir en entorno de usuario de Windows (permanece al cerrar PowerShell)
    [Environment]::SetEnvironmentVariable('STEAMBETA_ADMIN_KEY_SHA256', $hashHex, 'User')
    [Environment]::SetEnvironmentVariable('STEAMBETA_ADMIN_ACTOR_ID', $TechnicalActorId, 'User')

    # Establecer también en la sesión actual para poder iniciar el backend de inmediato
    $env:STEAMBETA_ADMIN_KEY_SHA256 = $hashHex
    $env:STEAMBETA_ADMIN_ACTOR_ID = $TechnicalActorId

    Write-Host ""
    Write-Host "[OK] Configuración administrativa persistida en el entorno de usuario de Windows." -ForegroundColor Green
    Write-Host "     STEAMBETA_ADMIN_KEY_SHA256 : configurada (hash SHA-256 de 64 caracteres, oculto)." -ForegroundColor Green
    Write-Host "     STEAMBETA_ADMIN_ACTOR_ID   : $TechnicalActorId" -ForegroundColor Green
    Write-Host "     Ambas variables también quedaron activas en esta sesión actual." -ForegroundColor Cyan
} finally {
    if ($bstr1 -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr1) }
    if ($bstr2 -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr2) }
    if ($bytes) { [Array]::Clear($bytes, 0, $bytes.Length) }
    $plain1 = $null
    $plain2 = $null
    $hashHex = $null
    if ($sec1) { $sec1.Dispose() }
    if ($sec2) { $sec2.Dispose() }
}
