param(
    [string]$BaseUrl = 'http://localhost:8080',
    [string]$Login = 'local.check.member'
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$base = [Uri]$BaseUrl
if (-not $base.IsLoopback -or $base.Scheme -notin @('http', 'https') -or
    $base.UserInfo -or $base.AbsolutePath -ne '/' -or $base.Query -or $base.Fragment) {
    throw 'Usa una URL local, por ejemplo http://localhost:8080.'
}
if ($Login -cnotmatch '^[a-z0-9][a-z0-9._@-]{2,79}$') {
    throw 'El identificador debe tener 3 a 80 caracteres simples en minusculas.'
}
$baseAddress = $BaseUrl.TrimEnd('/')
$apiSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$password = $null
$headers = $null
$requestBody = $null
$authenticated = $false
$step = 'conexion'

function Invoke-LocalApi {
    param([string]$Path, [string]$Method = 'GET', $Body = $null, $Headers = @{})
    $options = @{
        Uri = "$baseAddress$Path"
        Method = $Method
        WebSession = $apiSession
        Headers = $Headers
        UseBasicParsing = $true
        TimeoutSec = 15
        ErrorAction = 'Stop'
    }
    if ($null -ne $Body) {
        $options.Body = $Body
        $options.ContentType = 'application/x-www-form-urlencoded'
        if ($Body -is [byte[]]) {
            $options.ContentType = 'application/json; charset=utf-8'
        }
    }
    try {
        $reply = Invoke-WebRequest @options
        return [PSCustomObject]@{ Status = [int]$reply.StatusCode; Content = $reply.Content }
    } catch {
        if ($null -eq $_.Exception.Response) {
            throw 'No se recibio una respuesta HTTP. Comprueba backend, URL y puerto.'
        }
        return [PSCustomObject]@{ Status = [int]$_.Exception.Response.StatusCode; Content = $null }
    }
}

function Assert-Status {
    param($Reply, [int]$Expected, [string]$Operation)
    if ($Reply.Status -ne $Expected) {
        Write-Host "  ERROR $Operation (HTTP $($Reply.Status); esperado $Expected)"
        throw "$Operation devolvio HTTP $($Reply.Status); se esperaba $Expected."
    }
    Write-Host "  OK $Operation (HTTP $Expected)"
}

function Get-CsrfHeaders {
    $reply = Invoke-LocalApi '/api/auth/csrf'
    Assert-Status $reply 200 'Obtener CSRF y cookie de sesion'
    $csrf = $reply.Content | ConvertFrom-Json
    if (-not $csrf.headerName -or -not $csrf.token) {
        throw 'El backend no entrego el contrato CSRF esperado.'
    }
    $result = @{}
    $result[$csrf.headerName] = $csrf.token
    return $result
}

try {
    Write-Host '1/5 Comprobar backend y rechazo de peticiones sin sesion.'
    Assert-Status (Invoke-LocalApi '/api/account') 401 'Perfil sin autenticar'
    $headers = Get-CsrfHeaders

    $step = 'credencial local cifrada'
    $localRoot = Join-Path $env:LOCALAPPDATA 'ReplicaSteam/persistence'
    $credentialPath = Join-Path $localRoot "api-check-$Login.credentials.dpapi.json"
    if (Test-Path -LiteralPath $credentialPath) {
        $stored = Get-Content -LiteralPath $credentialPath -Raw | ConvertFrom-Json
        if ($stored.login -cne $Login) { throw 'La credencial guardada pertenece a otra cuenta.' }
        $secure = ConvertTo-SecureString $stored.password
        $password = [Net.NetworkCredential]::new('', $secure).Password
    } else {
        New-Item -ItemType Directory -Force -Path $localRoot | Out-Null
        $sid = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
        & icacls.exe $localRoot /inheritance:r /grant:r "*${sid}:(OI)(CI)F" '*S-1-5-18:(OI)(CI)F' | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'No se pudo proteger el directorio de credenciales.' }
        foreach ($rule in (Get-Acl -LiteralPath $localRoot).Access) {
            if ($rule.AccessControlType -eq 'Allow' -and
                $rule.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value -notin @($sid, 'S-1-5-18')) {
                throw 'El directorio tiene permisos adicionales. Revisa sus ACL antes de continuar.'
            }
        }
        $bytes = New-Object byte[] 24
        $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
        try { $generator.GetBytes($bytes) } finally { $generator.Dispose() }
        $password = [Convert]::ToBase64String($bytes)
        $secure = ConvertTo-SecureString $password -AsPlainText -Force
        $stored = @{ login = $Login; password = (ConvertFrom-SecureString $secure) }
        [IO.File]::WriteAllText($credentialPath, ($stored | ConvertTo-Json), [Text.UTF8Encoding]::new($false))
    }
    $stored = $null
    if ($secure) { $secure.Dispose(); $secure = $null }

    $step = 'registro'
    Write-Host '2/5 Registrar una cuenta de prueba sin matricula. Se conserva para repetir la comprobacion.'
    $requestBody = [Text.Encoding]::UTF8.GetBytes((@{
        login = $Login
        password = $password
        alias = 'Miembro de prueba local'
        declaredUniversity = 'Universidad declarada de prueba'
        declaredCareer = 'Ingenieria en Sistemas'
    } | ConvertTo-Json))
    $registration = Invoke-LocalApi '/api/auth/register' 'POST' $requestBody $headers
    if ($registration.Status -eq 409) {
        Write-Host '  Registro existente (HTTP 409). Se verificara con la credencial local conservada.'
    } else {
        Assert-Status $registration 201 'Crear cuenta'
    }
    $requestBody = $null

    $step = 'inicio de sesion'
    Write-Host '3/5 Iniciar sesion con formulario, cookie y CSRF.'
    $requestBody = @{ login = $Login; password = $password }
    $loginReply = Invoke-LocalApi '/api/auth/login' 'POST' $requestBody $headers
    Assert-Status $loginReply 200 'Login'
    $authenticated = $true
    $identity = $loginReply.Content | ConvertFrom-Json
    if ($null -ne $identity.studentId) { throw 'La cuenta de prueba tiene un vinculo academico inesperado.' }
    $requestBody = $null
    $password = $null
    # Spring Security retira el token anterior al autenticar.
    $headers = Get-CsrfHeaders

    $step = 'perfil propio'
    Write-Host '4/5 Consultar perfil y comprobar que no expone credenciales.'
    $profileReply = Invoke-LocalApi '/api/account'
    Assert-Status $profileReply 200 'Perfil propio'
    $profile = $profileReply.Content | ConvertFrom-Json
    $expectedFields = @('accountId', 'alias', 'declaredUniversity', 'declaredCareer', 'institutionalIdentityVerified')
    if (@(Compare-Object ($profile.PSObject.Properties.Name | Sort-Object) ($expectedFields | Sort-Object)).Count -ne 0) {
        throw 'El perfil tiene campos inesperados.'
    }
    if (-not $profile.accountId -or $profile.alias -ne 'Miembro de prueba local' -or
        $profile.institutionalIdentityVerified -ne $false) {
        throw 'El perfil no coincide con la cuenta independiente esperada.'
    }
    Write-Host '  OK Cuenta independiente; datos institucionales declarados y no verificados.'
    Write-Host '  OK Respuesta limitada a los cinco campos de perfil, sin login ni contrasena.'
    Assert-Status (Invoke-LocalApi '/api/me/library') 403 'Biblioteca sin vinculo academico'

    $step = 'cierre de sesion'
    Write-Host '5/5 Cerrar sesion y comprobar que el perfil vuelve a estar protegido.'
    Assert-Status (Invoke-LocalApi '/api/auth/logout' 'POST' '' $headers) 204 'Logout'
    $authenticated = $false
    Assert-Status (Invoke-LocalApi '/api/account') 401 'Perfil despues del logout'
    Write-Host ''
    Write-Host "RECORRIDO API LOCAL COMPLETADO. Cuenta conservada: $Login"
    Write-Host 'Este script termina. El backend debe seguir ejecutandose en la otra terminal.'
    Write-Host 'Esta comprobacion no abre ni valida la interfaz JavaFX.'
} catch {
    # No exception chain, request body, cookie or token is printed.
    Write-Host "FALLO en el paso: $step."
    Write-Host 'Comprueba que V3 esta aplicada y que el backend actual usa el perfil local en el puerto indicado.'
    Write-Host 'Si registro devolvio 409 y login 401, el identificador pertenece a otra credencial; usa -Login con otro nombre.'
    throw "No se completo el recorrido local ($step)."
} finally {
    if ($authenticated) {
        try {
            $headers = Get-CsrfHeaders
            $null = Invoke-LocalApi '/api/auth/logout' 'POST' '' $headers
        } catch { Write-Host 'No se pudo confirmar la salida del servidor tras el fallo.' }
    }
    $password = $null
    $requestBody = $null
    $headers = $null
    $apiSession = $null
    if ($secure) { $secure.Dispose() }
}
