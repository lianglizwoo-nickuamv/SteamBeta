param([ValidateSet('Normal','Migration','Demo')][string]$Mode = 'Normal')
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path

# 1. Comprobar si el puerto 8080 ya está ocupado para no iniciar instancias duplicadas
$port8080 = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
if ($port8080) {
    Write-Host "[SteamBeta Backend] El puerto 8080 ya se encuentra en uso por una instancia activa del backend." -ForegroundColor Yellow
    Write-Host "[SteamBeta Backend] El servidor Spring Boot ya está disponible en http://localhost:8080" -ForegroundColor Green
    Write-Host "[SteamBeta Backend] No se iniciará una instancia duplicada." -ForegroundColor Yellow
    return
}

# 2. Localizar almacén de credenciales seguras DPAPI
$storePath = Join-Path $env:LOCALAPPDATA 'ReplicaSteam/persistence/credentials.dpapi.json'
$credentials = if(Test-Path -LiteralPath $storePath) { Get-Content -LiteralPath $storePath -Raw | ConvertFrom-Json } else { $null }

function Read-LocalPassword([string]$role) {
    $encrypted = $credentials.PSObject.Properties[$role].Value
    if ([string]::IsNullOrWhiteSpace($encrypted)) { throw "Falta la identidad protegida para el rol: $role" }
    $secure = ConvertTo-SecureString $encrypted
    return [Net.NetworkCredential]::new('', $secure).Password
}

# 3. Selección y verificación de Java 21 LTS
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
$env:MAVEN_USER_HOME = Join-Path $projectRoot '.replica-work/m2'

# 4. Parámetros de conexión a PostgreSQL (steam_academico)
if (-not $env:REPLICA_DB_URL) { $env:REPLICA_DB_URL = 'jdbc:postgresql://localhost:5432/steam_academico' }
if (-not $env:REPLICA_DB_USER) { $env:REPLICA_DB_USER = 'replica_runtime' }

if ($credentials) {
    $env:REPLICA_DB_PASSWORD = Read-LocalPassword 'replica_runtime'
}

# 5. Verificación de contraseña local y asistencia si falta DPAPI
if (-not $env:REPLICA_DB_PASSWORD) {
    Write-Host ""
    Write-Host "==========================================================================" -ForegroundColor Red
    Write-Host " [ADVERTENCIA] No se encontraron credenciales locales protegidas con DPAPI." -ForegroundColor Yellow
    Write-Host "==========================================================================" -ForegroundColor Red
    Write-Host "Para configurar de forma segura las contraseñas de PostgreSQL (steam_academico),"
    Write-Host "ejecuta UNA SOLA VEZ el asistente interactivo en PowerShell:" -ForegroundColor Cyan
    Write-Host "   powershell -ExecutionPolicy Bypass -File .\backend\scripts\setup-local-credentials.ps1" -ForegroundColor White
    Write-Host "==========================================================================" -ForegroundColor Yellow
    Write-Host ""
    throw 'Configure REPLICA_DB_PASSWORD localmente o ejecute setup-local-credentials.ps1'
}

# 6. Verificación de configuración persistida para STEAMBETA ADMIN (entorno de usuario Windows)
if ([string]::IsNullOrWhiteSpace($env:STEAMBETA_ADMIN_KEY_SHA256)) {
    $persistedHash = [Environment]::GetEnvironmentVariable('STEAMBETA_ADMIN_KEY_SHA256', 'User')
    if (-not [string]::IsNullOrWhiteSpace($persistedHash)) {
        $env:STEAMBETA_ADMIN_KEY_SHA256 = $persistedHash.Trim()
    }
}
if ([string]::IsNullOrWhiteSpace($env:STEAMBETA_ADMIN_ACTOR_ID)) {
    $persistedActor = [Environment]::GetEnvironmentVariable('STEAMBETA_ADMIN_ACTOR_ID', 'User')
    if (-not [string]::IsNullOrWhiteSpace($persistedActor)) {
        $env:STEAMBETA_ADMIN_ACTOR_ID = $persistedActor.Trim()
    }
}
if ([string]::IsNullOrWhiteSpace($env:STEAMBETA_ADMIN_KEY_SHA256) -or [string]::IsNullOrWhiteSpace($env:STEAMBETA_ADMIN_ACTOR_ID)) {
    Write-Host ""
    Write-Host "==========================================================================" -ForegroundColor Red
    Write-Host " [ERROR] Faltan variables persistidas de STEAMBETA ADMIN." -ForegroundColor Yellow
    Write-Host "==========================================================================" -ForegroundColor Red
    Write-Host "Se requieren STEAMBETA_ADMIN_KEY_SHA256 y STEAMBETA_ADMIN_ACTOR_ID en el"
    Write-Host "entorno de usuario de Windows. Ejecuta UNA SOLA VEZ:" -ForegroundColor Cyan
    Write-Host "   powershell -ExecutionPolicy Bypass -File .\backend\scripts\setup-admin-environment.ps1" -ForegroundColor White
    Write-Host "==========================================================================" -ForegroundColor Yellow
    Write-Host ""
    throw 'Faltan variables persistidas de STEAMBETA ADMIN (STEAMBETA_ADMIN_KEY_SHA256 / STEAMBETA_ADMIN_ACTOR_ID).'
}

# 7. Preparación de variables de entorno y ejecución
$migrationUser = $env:REPLICA_MIGRATION_USER
$migrationPassword = $env:REPLICA_MIGRATION_PASSWORD
$env:REPLICA_MIGRATION_USER = $null
$env:REPLICA_MIGRATION_PASSWORD = $null
$env:SPRING_PROFILES_ACTIVE = $null

Push-Location $projectRoot
try {
    if ($Mode -in @('Migration','Demo')) {
        $env:REPLICA_MIGRATION_USER = if($credentials) { 'replica_migrator' } else { $migrationUser }
        $env:REPLICA_MIGRATION_PASSWORD = if($credentials) { Read-LocalPassword 'replica_migrator' } else { $migrationPassword }
        if(-not $env:REPLICA_MIGRATION_USER -or -not $env:REPLICA_MIGRATION_PASSWORD) { throw 'Separate migration credentials required' }
        if($Mode -eq 'Demo') {
            $demoPath = Join-Path $env:LOCALAPPDATA 'ReplicaSteam/persistence/demo.credentials.dpapi.json'
            if(Test-Path -LiteralPath $demoPath) {
                $demo = Get-Content -LiteralPath $demoPath -Raw | ConvertFrom-Json
                foreach($name in @('alba','bruno','celia')) {
                    $secure = ConvertTo-SecureString $demo.PSObject.Properties[$name].Value
                    [Environment]::SetEnvironmentVariable("REPLICA_DEMO_$($name.ToUpper())_PASSWORD", [Net.NetworkCredential]::new('', $secure).Password, 'Process')
                }
            }
            & .\mvnw.cmd -B -f .\backend\pom.xml spring-boot:run '-Dspring-boot.run.profiles=demo'
        } else {
            & .\mvnw.cmd -B -f .\backend\pom.xml spring-boot:run '-Dspring-boot.run.profiles=migration'
        }
    } else {
        Write-Host "[SteamBeta Backend] Iniciando Spring Boot en perfil 'local' sobre steam_academico (puerto 8080)..." -ForegroundColor Cyan
        & .\mvnw.cmd -B -f .\backend\pom.xml spring-boot:run '-Dspring-boot.run.profiles=local'
    }
    if ($LASTEXITCODE -ne 0) { throw "Backend $Mode execution failed (exit $LASTEXITCODE)" }
} finally {
    $env:REPLICA_DB_PASSWORD = $null
    $env:REPLICA_MIGRATION_USER = $null
    $env:REPLICA_MIGRATION_PASSWORD = $null
    $credentials = $null
    $migrationPassword = $null
    foreach($name in @('ALBA','BRUNO','CELIA')) { [Environment]::SetEnvironmentVariable("REPLICA_DEMO_${name}_PASSWORD", $null, 'Process') }
    Pop-Location
}
