param([string]$PostgresBin = 'C:\Program Files\PostgreSQL\18\bin')
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$workRoot = Join-Path $projectRoot '.replica-work'
$fixtureRoot = Join-Path $workRoot ('pg-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $fixtureRoot -Force | Out-Null
$cluster = Join-Path $fixtureRoot 'data'
$passwordFile = Join-Path $fixtureRoot 'init-password'
$fixturePassword = [guid]::NewGuid().ToString('N')
[IO.File]::WriteAllText($passwordFile, $fixturePassword, [Text.UTF8Encoding]::new($false))
$listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
$listener.Start()
$fixturePort = $listener.LocalEndpoint.Port
$listener.Stop()
$started = $false
$names = @('REPLICA_TEST_ADMIN_URL','REPLICA_TEST_ADMIN_USER','REPLICA_TEST_ADMIN_PASSWORD','PGPASSWORD','MAVEN_USER_HOME')
$previous = @{}
foreach ($name in $names) { $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
try {
    & (Join-Path $PostgresBin 'initdb.exe') -D $cluster -U fixture_admin --auth=scram-sha-256 "--pwfile=$passwordFile" --encoding=UTF8 --locale=C | Out-File (Join-Path $fixtureRoot 'init.log')
    if ($LASTEXITCODE -ne 0) { throw 'initdb failed; inspect .replica-work fixture init.log' }
    Remove-Item -LiteralPath $passwordFile
    $serverLog = Join-Path $fixtureRoot 'postgres.log'
    $startProcess = Start-Process -FilePath (Join-Path $PostgresBin 'pg_ctl.exe') -WindowStyle Hidden -PassThru -ArgumentList @('-D', "`"$cluster`"", '-l', "`"$serverLog`"", '-o', "`"-h 127.0.0.1 -p $fixturePort`"", '-w', 'start') -RedirectStandardOutput (Join-Path $fixtureRoot 'start.log') -RedirectStandardError (Join-Path $fixtureRoot 'start-error.log')
    $startProcess.WaitForExit()
    $started = Test-Path (Join-Path $cluster 'postmaster.pid')
    & (Join-Path $PostgresBin 'pg_isready.exe') -h 127.0.0.1 -p $fixturePort -U fixture_admin
    if ($LASTEXITCODE -ne 0) { throw 'Disposable PostgreSQL did not start' }
    $env:PGPASSWORD = $fixturePassword
    & (Join-Path $PostgresBin 'createdb.exe') -h 127.0.0.1 -p $fixturePort -U fixture_admin replica_test_persistence
    if ($LASTEXITCODE -ne 0) { throw 'Could not create disposable test database' }
    $env:REPLICA_TEST_ADMIN_URL = "jdbc:postgresql://127.0.0.1:$fixturePort/replica_test_persistence"
    $env:REPLICA_TEST_ADMIN_USER = 'fixture_admin'
    $env:REPLICA_TEST_ADMIN_PASSWORD = $fixturePassword
    if (-not $env:MAVEN_USER_HOME) { $env:MAVEN_USER_HOME = Join-Path $workRoot 'm2' }
    Push-Location $projectRoot
    try {
        & .\mvnw.cmd -B -f .\backend\pom.xml verify
        if ($LASTEXITCODE -ne 0) { throw 'Backend verify failed' }
    } finally { Pop-Location }
} finally {
    if ($started) { & (Join-Path $PostgresBin 'pg_ctl.exe') -D $cluster -m fast -w stop }
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
}
