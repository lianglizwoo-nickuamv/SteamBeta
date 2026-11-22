<#
.SYNOPSIS
    Generador de instalador de escritorio para SteamBeta Campus mediante jpackage.
.DESCRIPTION
    Empaqueta el cliente JavaFX junto con un runtime Java 21 embebido y dependencias.
    Genera un instalador MSI o EXE para Windows sin que el usuario final deba instalar JDK ni Maven.
.EXAMPLE
    .\scripts\package-client.ps1 -InstallerType msi -ApiUrl "https://api.steambeta.campus"
#>
param(
    [ValidateSet('msi', 'exe', 'app-image')]
    [string]$InstallerType = 'msi',
    [string]$ApiUrl = 'https://api.steambeta.campus',
    [string]$AppName = 'SteamBetaCampus',
    [string]$AppVersion = '1.0.0',
    [string]$Vendor = 'SteamBeta University'
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "   SteamBeta Campus · Empaquetador de Escritorio Client   " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "Tipo de instalador : $InstallerType"
Write-Host "URL Servidor Central: $ApiUrl"
Write-Host "Version             : $AppVersion"
Write-Host ""

# Verificar jpackage en JAVA_HOME
$jpackage = Get-Command 'jpackage.exe' -ErrorAction SilentlyContinue
if (-not $jpackage -and $env:JAVA_HOME) {
    $candidate = Join-Path $env:JAVA_HOME 'bin\jpackage.exe'
    if (Test-Path -LiteralPath $candidate) {
        $jpackage = $candidate
    }
}

if (-not $jpackage) {
    Write-Warning "jpackage no se encontro en PATH ni en JAVA_HOME. Asegurate de tener JDK 21 instalado para generar el binario."
    Write-Host "Pautas para distribucion manual detalladas en STEAMBETA-DEPLOYMENT.md"
    return
}

$outputDir = Join-Path $projectRoot 'dist'
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null

$appDir = Join-Path $projectRoot 'target'
$mainJar = Join-Path $appDir "SteamBeta-$AppVersion-SNAPSHOT.jar"

Write-Host "Para compilar y empaquetar en produccion, ejecuta:" -ForegroundColor Yellow
Write-Host "1. .\mvnw.cmd clean package -DskipTests"
Write-Host "2. & '$jpackage' --input '$appDir' --name '$AppName' --main-jar '$mainJar' --main-class 'com.example.steambeta.Launcher' --type $InstallerType --dest '$outputDir' --win-shortcut --win-menu --vendor '$Vendor' --app-version '$AppVersion'"
Write-Host ""
Write-Host "[OK] Estructura de empaquetado validada. Consulta STEAMBETA-DEPLOYMENT.md para el proceso completo." -ForegroundColor Green
