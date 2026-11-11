param([ValidateSet('Normal','Migration','Demo')][string]$Mode = 'Normal')
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$storePath = Join-Path $env:LOCALAPPDATA 'ReplicaSteam/persistence/credentials.dpapi.json'
$credentials = if(Test-Path -LiteralPath $storePath) { Get-Content -LiteralPath $storePath -Raw | ConvertFrom-Json } else { $null }
function Read-LocalPassword([string]$role) {
    $encrypted = $credentials.PSObject.Properties[$role].Value
    if ([string]::IsNullOrWhiteSpace($encrypted)) { throw "Missing local identity: $role" }
    $secure = ConvertTo-SecureString $encrypted
    return [Net.NetworkCredential]::new('', $secure).Password
}
$localJdk=Join-Path $projectRoot '.replica-work/jdk21/jdk-21.0.13+9'
if(Test-Path -LiteralPath $localJdk) { $env:JAVA_HOME=(Resolve-Path $localJdk).Path }
if(-not $env:JAVA_HOME) { throw 'Configure JAVA_HOME with a JDK 21 installation' }
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:MAVEN_USER_HOME = Join-Path $projectRoot '.replica-work/m2'
if(-not $env:REPLICA_DB_URL) { $env:REPLICA_DB_URL='jdbc:postgresql://localhost:5432/steam_academico' }
if(-not $env:REPLICA_DB_USER) { $env:REPLICA_DB_USER='replica_runtime' }
if($credentials) { $env:REPLICA_DB_PASSWORD = Read-LocalPassword 'replica_runtime' }
if(-not $env:REPLICA_DB_PASSWORD) { throw 'Configure REPLICA_DB_PASSWORD locally' }
# Always remove inherited migration context before selecting this execution mode.
$migrationUser=$env:REPLICA_MIGRATION_USER
$migrationPassword=$env:REPLICA_MIGRATION_PASSWORD
$env:REPLICA_MIGRATION_USER=$null
$env:REPLICA_MIGRATION_PASSWORD=$null
$env:SPRING_PROFILES_ACTIVE = $null
Push-Location $projectRoot
try {
    if ($Mode -in @('Migration','Demo')) {
        $env:REPLICA_MIGRATION_USER = if($credentials) { 'replica_migrator' } else { $migrationUser }
        $env:REPLICA_MIGRATION_PASSWORD = if($credentials) { Read-LocalPassword 'replica_migrator' } else { $migrationPassword }
        if(-not $env:REPLICA_MIGRATION_USER -or -not $env:REPLICA_MIGRATION_PASSWORD) { throw 'Separate migration credentials required' }
        if($Mode -eq 'Demo') {
            $demoPath=Join-Path $env:LOCALAPPDATA 'ReplicaSteam/persistence/demo.credentials.dpapi.json'
            if(Test-Path -LiteralPath $demoPath) {
                $demo=Get-Content -LiteralPath $demoPath -Raw|ConvertFrom-Json
                foreach($name in @('alba','bruno','celia')) {
                    $secure=ConvertTo-SecureString $demo.PSObject.Properties[$name].Value
                    [Environment]::SetEnvironmentVariable("REPLICA_DEMO_$($name.ToUpper())_PASSWORD",[Net.NetworkCredential]::new('',$secure).Password,'Process')
                }
            }
            & .\mvnw.cmd -B -f .\backend\pom.xml spring-boot:run '-Dspring-boot.run.profiles=demo'
        } else {
            & .\mvnw.cmd -B -f .\backend\pom.xml spring-boot:run '-Dspring-boot.run.profiles=migration'
        }
    } else {
        & .\mvnw.cmd -B -f .\backend\pom.xml spring-boot:run '-Dspring-boot.run.profiles=local'
    }
    if ($LASTEXITCODE -ne 0) { throw "Backend $Mode execution failed (exit $LASTEXITCODE)" }
} finally {
    $env:REPLICA_DB_PASSWORD = $null
    $env:REPLICA_MIGRATION_USER = $null
    $env:REPLICA_MIGRATION_PASSWORD = $null
    $credentials = $null
    $migrationPassword=$null
    foreach($name in @('ALBA','BRUNO','CELIA')) { [Environment]::SetEnvironmentVariable("REPLICA_DEMO_${name}_PASSWORD",$null,'Process') }
    Pop-Location
}
