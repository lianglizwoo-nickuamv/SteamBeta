$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path

$localJdk = Join-Path $projectRoot '.replica-work/jdk21/jdk-21.0.13+9'
if (Test-Path -LiteralPath $localJdk) {
    $env:JAVA_HOME = (Resolve-Path $localJdk).Path
}
if (-not $env:JAVA_HOME) {
    $candidate = Get-ChildItem "C:\Program Files\Eclipse Adoptium\jdk-21*", "C:\Program Files\Java\jdk-21*" -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($candidate) { $env:JAVA_HOME = $candidate.FullName }
}
if (-not $env:JAVA_HOME) {
    throw 'No se encontró una instalación de JDK 21. Configura la variable de entorno JAVA_HOME con Java 21.'
}
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

if (-not $env:MAVEN_USER_HOME) {
    $env:MAVEN_USER_HOME = Join-Path $projectRoot '.replica-work/m2'
}
if (-not $env:STEAMBETA_API_URL) {
    $env:STEAMBETA_API_URL = 'http://localhost:8080'
}

Write-Host "[SteamBeta Client] Iniciando cliente JavaFX conectado a $env:STEAMBETA_API_URL..." -ForegroundColor Cyan

Push-Location $projectRoot
try {
    & .\mvnw.cmd -B javafx:run
    if ($LASTEXITCODE -ne 0) { throw 'La ejecución del cliente JavaFX ha fallado' }
} finally {
    Pop-Location
}
