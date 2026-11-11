$ErrorActionPreference='Stop'
$projectRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$localJdk=Join-Path $projectRoot '.replica-work/jdk21/jdk-21.0.13+9'
if(Test-Path -LiteralPath $localJdk) { $env:JAVA_HOME=(Resolve-Path $localJdk).Path }
if(-not $env:JAVA_HOME) { throw 'Configure JAVA_HOME with a JDK 21 installation' }
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
if(-not $env:MAVEN_USER_HOME) { $env:MAVEN_USER_HOME=Join-Path $projectRoot '.replica-work/m2' }
Push-Location $projectRoot
try { & .\mvnw.cmd -B javafx:run; if($LASTEXITCODE -ne 0){throw 'JavaFX execution failed'} }
finally { Pop-Location }
