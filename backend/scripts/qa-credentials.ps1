param([Parameter(Mandatory=$true)][ValidateSet('qa.pilot.outsider','qa.pilot.second.member')][string]$CopyPasswordFor)
$ErrorActionPreference='Stop'
$path=Join-Path $env:LOCALAPPDATA "ReplicaSteam/persistence/api-check-$CopyPasswordFor.credentials.dpapi.json"
$stored=Get-Content -LiteralPath $path -Raw|ConvertFrom-Json
if($stored.login -cne $CopyPasswordFor){throw 'Credential identity mismatch'}
$secure=ConvertTo-SecureString $stored.password
try { Set-Clipboard -Value ([Net.NetworkCredential]::new('', $secure).Password); Write-Output 'Contrasena QA copiada. Pegala solo en el acceso local.' }
finally { $secure.Dispose(); $stored=$null }
