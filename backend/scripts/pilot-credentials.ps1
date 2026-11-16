param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[a-z0-9][a-z0-9._@-]{2,79}$')]
    [string]$CopyPasswordFor
)

$ErrorActionPreference = 'Stop'
$path = Join-Path $env:LOCALAPPDATA 'ReplicaSteam/persistence/pilot.credentials.dpapi.json'
if (-not (Test-Path -LiteralPath $path)) {
    throw 'Las contrasenas piloto todavia no estan configuradas para este usuario de Windows.'
}
$values = Get-Content -LiteralPath $path -Raw | ConvertFrom-Json
$encrypted = $values.PSObject.Properties[$CopyPasswordFor].Value
if ([string]::IsNullOrWhiteSpace($encrypted)) {
    throw 'No hay una contrasena local guardada para esa cuenta.'
}
$secure = ConvertTo-SecureString $encrypted
try {
    Set-Clipboard -Value ([Net.NetworkCredential]::new('', $secure).Password)
    Write-Output "Contrasena copiada para $CopyPasswordFor. Pegala en el campo de JavaFX."
} finally {
    $secure.Dispose()
    $values = $null
}
