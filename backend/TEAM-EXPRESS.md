# Guía express para probar con el equipo

## Opción disponible hoy: cada compañero ejecuta su copia local

Cada base local es independiente: las publicaciones no se comparten entre
computadoras. Para demostrar conversación compartida hoy, usa varias cuentas
en el mismo equipo anfitrión. El acceso remoto compartido requiere la propuesta
del final de esta guía; todavía no está habilitado.

### 1. Descargar y verificar herramientas — cada compañero, PowerShell

Instalar Git, JDK 21 y PostgreSQL 18, y abrir una terminal nueva.
IntelliJ es opcional para estos comandos. Sustituir la ruta JDK por la instalada.

```powershell
git clone --branch codex/community-foundation https://github.com/lianglizwoo-nickuamv/SteamBeta.git
cd SteamBeta
$env:JAVA_HOME='C:\RUTA\AL\JDK21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
java -version
.\mvnw.cmd -v
```

Los dos últimos deben indicar Java 21. `release version 21 not supported`
significa que Maven usa otro JDK. Todos estos comandos terminan.
Si ya clonaste: revisa `git status` y luego `git pull --ff-only` con árbol limpio.
La preparación de esta sesión debe estar publicada por el responsable para que
el clon incluya los nuevos scripts; no se ha hecho push desde esta guía.

### 2. Base propia — administrador de cada copia

Crear steam_academico y los roles separados de backend/README.md, sección
“Configuración y otra máquina”. Asignar contraseñas con `\password` en psql
o con el gestor local. No usar postgres como identidad del backend.
No copiar el almacén DPAPI del desarrollador ni cambiar pg_hba a trust.

En PowerShell, raíz del clon, introducir los secretos mediante prompt oculto:

```powershell
function Set-LocalSecret([string]$Name) {
    $secret=Read-Host $Name -AsSecureString
    try { [Environment]::SetEnvironmentVariable($Name,[Net.NetworkCredential]::new('',$secret).Password,'Process') }
    finally { $secret.Dispose() }
}
$env:REPLICA_DB_URL='jdbc:postgresql://localhost:5432/steam_academico'
$env:REPLICA_DB_USER='replica_runtime'
Set-LocalSecret REPLICA_DB_PASSWORD
$env:REPLICA_MIGRATION_USER='replica_migrator'
Set-LocalSecret REPLICA_MIGRATION_PASSWORD
```

Estas son contraseñas PostgreSQL propias, no las de acceso de estudiantes.
En base existente: revisar preflight y respaldo antes de migrar.

```powershell
.\backend\scripts\start-local.ps1 -Mode Migration
Set-LocalSecret REPLICA_DB_PASSWORD
.\backend\scripts\start-local.ps1 -Mode Normal
```

Migration termina; Normal permanece abierto. El lanzador limpia secretos al
terminar Migration, por eso se vuelve a introducir la contraseña runtime.
Debe aparecer `Started BackendApplication`. Si 8080 está ocupado, identifica
la otra ejecución; no arranques una segunda. Conexión rechazada a 5432: comprobar
servicio PostgreSQL. Error de credenciales: revisar roles/contraseñas en esa copia.
Flyway checksum mismatch: detenerse, conservar datos y pedir revisión; no repair.

### 3. Abrir cliente y registrar cuentas — segunda terminal

Desde la raíz del clon, configurar JAVA_HOME como en el paso 1 y ejecutar:

```powershell
$env:REPLICA_API_URL='http://localhost:8080'
.\backend\scripts\start-client.ps1
```

Permanece abierto con JavaFX. Pulsar Crear una cuenta. Registrar dos miembros y
una tercera cuenta moderadora con identificadores conocidos. Cada persona
establece su propia contraseña y la guarda en su gestor; mínimo 12 caracteres,
máximo 72 bytes UTF-8. Alias y login son distintos. No existe contraseña universal.

En la máquina original, las contraseñas de prueba existentes se copian así:

```powershell
.\backend\scripts\pilot-credentials.ps1 -CopyPasswordFor 25010333
.\backend\scripts\pilot-credentials.ps1 -CopyPasswordFor 25010323
.\backend\scripts\pilot-credentials.ps1 -CopyPasswordFor 82921019
.\backend\scripts\qa-credentials.ps1 -CopyPasswordFor qa.pilot.outsider
```

Ejecutar solo uno y pegar inmediatamente en JavaFX. Estos comandos terminan y
solo funcionan en el Windows que tiene esos secretos DPAPI. No enviarlos a los
compañeros ni confundirlos con comandos de creación/reset de contraseña.

### 4. Dar membresías — terminal de administrador, raíz del clon

Configurar ambas variables REPLICA_MIGRATION_* con el prompt anterior en esta
terminal nueva; en la máquina original las toma del almacén DPAPI.

```powershell
.\backend\scripts\add-pilot-members.ps1 -Login @('equipo.ana','equipo.luis') -ModeratorLogin @('equipo.mod')
```

Sustituir por los logins efectivamente registrados. Termina sin duplicar
membresías. El script administra sistemas-piloto; no asigna roles globales.
Actualizar comunidades y seguir PILOT-QUICKSTART.md. Para API local:
`check-local-api.ps1` crea/reutiliza una cuenta sintética y conserva su contraseña
cifrada; no abre JavaFX. Para terminar, cerrar sesión y ventana; Ctrl+C en el
servidor. La base conserva los datos al reiniciar.

## Propuesta de prueba compartida desde otra computadora — sin ejecutar todavía

Propuesta: un anfitrión del equipo ejecuta PostgreSQL y un solo backend; todos
los demás ejecutan únicamente JavaFX. El anfitrión necesita una dirección estable
en red privada. Usar un proxy HTTPS en esa red con certificado confiable por los
JDK de los clientes. El backend queda en loopback detrás del proxy; PostgreSQL
no se expone a clientes. Permitir únicamente al grupo la entrada privada 443,
sin abrir Internet ni publicar puerto 8080. No usar el perfil local para ese acceso.

Antes de aprobarlo: elegir anfitrión, nombre DNS privado, certificado y responsable
de instalación/renovación; revisar reenvío de HTTPS del proxy para cookies Secure;
probar login, CSRF, logout, reinicio y certificado desde una segunda computadora.
No desactivar validación TLS. Configurar en el cliente:
`$env:REPLICA_API_URL='https://nombre-privado-acordado'` y lanzar start-client.
localhost en el cliente siempre representa SU propia computadora.

Los compañeros reciben cuentas de aplicación propias; nunca credenciales de
PostgreSQL, migrador, archivos DPAPI ni contraseñas compartidas. No hay recuperación
autónoma: el responsable deberá acordar un procedimiento administrativo de reset
individual e invalidación de sesiones antes del piloto real. Falta validar ese
procedimiento; no asumir que ya hay un comando de reset publicado.

Recuperación propuesta: copia PostgreSQL antes de migrar, custodio y ruta privada,
ensayo de restauración en base aparte; detener escrituras antes de recuperar.
No restaurar automáticamente sobre la base activa. Reiniciar backend exige nuevo
login; registrar errores sin cookies, tokens o contraseñas. Ante tiempo de espera
de publicación, comprobar si ya existe antes de cambiar texto y reenviar.

Pendientes de distribución: paquete JavaFX instalable no generado ni probado;
por ahora se requiere JDK y descarga Maven inicial. DPAPI y scripts .ps1 son
específicos de Windows. Firewall, DNS, proxy, HTTPS y configuración de servidor
remoto son propuesta, no infraestructura instalada ni verificada.
