param([switch]$Prepare,[ValidateSet('demo.alba','demo.bruno','demo.celia')][string]$CopyPasswordFor)
$ErrorActionPreference='Stop'
$localRoot=Join-Path $env:LOCALAPPDATA 'ReplicaSteam/persistence'
$path=Join-Path $localRoot 'demo.credentials.dpapi.json'
if($Prepare -and -not (Test-Path -LiteralPath $path)) {
    New-Item -ItemType Directory -Force -Path $localRoot | Out-Null
    $sid=[Security.Principal.WindowsIdentity]::GetCurrent().User
    & icacls.exe $localRoot /inheritance:r /grant:r "*$($sid.Value):(OI)(CI)F" '*S-1-5-18:(OI)(CI)F' | Out-Null
    if($LASTEXITCODE -ne 0) { throw 'Could not protect the local demo credential directory' }
    $allowed=@($sid.Value,'S-1-5-18')
    foreach($rule in (Get-Acl -LiteralPath $localRoot).Access) {
        if($rule.AccessControlType -eq 'Allow' -and $rule.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value -notin $allowed) {
            throw 'Unexpected credential-directory ACL; inspect it locally before generating secrets'
        }
    }
    $values=@{}; $rng=[Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        foreach($name in @('alba','bruno','celia')) {
            $bytes=New-Object byte[] 24; $rng.GetBytes($bytes)
            $secret=[Convert]::ToBase64String($bytes)
            $values[$name]=ConvertFrom-SecureString (ConvertTo-SecureString $secret -AsPlainText -Force)
            $secret=$null
        }
        [IO.File]::WriteAllText($path,($values|ConvertTo-Json),[Text.UTF8Encoding]::new($false))
    } finally { $rng.Dispose(); $values=$null }
}
if($CopyPasswordFor) {
    if(-not(Test-Path -LiteralPath $path)){throw 'Prepare demo credentials before copying a password'}
    $values=Get-Content -LiteralPath $path -Raw|ConvertFrom-Json
    $secure=ConvertTo-SecureString $values.PSObject.Properties[$CopyPasswordFor.Substring(5)].Value
    Set-Clipboard -Value ([Net.NetworkCredential]::new('',$secure).Password)
    $values=$null
    Write-Output 'Demo password copied locally. Paste into the password field; clear the clipboard afterwards.'
} else { Write-Output 'Demo accounts: demo.alba, demo.bruno, demo.celia. Passwords remain in the local DPAPI store.' }
