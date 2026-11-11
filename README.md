# SteamBeta · Biblioteca académica

Plataforma académica con cliente JavaFX modular y backend Spring Boot independiente.
El recorrido implementado es acceso por sesión, biblioteca personal, actividades,
progreso y salida. Perfil completo, tienda, recuperación de acceso y LMS real
quedan fuera del alcance actual.

## Requisitos

- JDK 21, con JAVA_HOME configurado y su carpeta bin en PATH.
- PostgreSQL 18 y una base propia llamada steam_academico.
- Acceso a Maven Central para la primera descarga. Se incluye Maven Wrapper 3.8.5;
  no hace falta instalar Maven ni descargar un JDK dentro del repositorio.
- PowerShell para los scripts Windows. Docker para Testcontainers, o los binarios
  PostgreSQL locales para el script de pruebas desechables.

Clonar la URL que suministre el responsable del repositorio y entrar en la raíz:

```powershell
git clone <URL-del-repositorio> SteamBeta
cd SteamBeta
java -version
.\mvnw.cmd -v
```

En Linux/macOS usar `./mvnw` en lugar de `mvnw.cmd`; el cliente usa JavaFX y requiere
un escritorio gráfico. Los scripts .ps1 de DPAPI y PostgreSQL nativo son específicos
de Windows. Los comandos Maven y las variables de entorno no dependen de DPAPI.

## Base de datos y credenciales propias

Un administrador crea una base vacía y los cinco roles descritos en
[backend/README.md](backend/README.md#configuración-y-otra-máquina). Runtime y
migraciones usan identidades separadas. No conceder al runtime propiedad,
SUPERUSER, BYPASSRLS ni membresía del migrador o del rol académico.

Definir las variables siguientes en una terminal local. Las contraseñas se
introducen mediante un prompt oculto, sin escribirlas en el historial:

```powershell
function Set-LocalSecret([string]$Name) {
    $secret = Read-Host $Name -AsSecureString
    try {
        [Environment]::SetEnvironmentVariable($Name,
            [Net.NetworkCredential]::new('', $secret).Password, 'Process')
    } finally { $secret.Dispose() }
}
$env:REPLICA_DB_URL='jdbc:postgresql://localhost:5432/steam_academico'
$env:REPLICA_DB_USER='replica_runtime'
Set-LocalSecret REPLICA_DB_PASSWORD
$env:REPLICA_MIGRATION_USER='replica_migrator'
Set-LocalSecret REPLICA_MIGRATION_PASSWORD
```

Ejecutar primero [preflight.sql](backend/scripts/preflight.sql) con una conexión
administrativa propia. Si hay un esquema previo sin historial Flyway o conflictos,
preservar los datos y detenerse. No usar clean, drop ni baseline automático.

```powershell
.\mvnw.cmd -f backend/pom.xml spring-boot:run '-Dspring-boot.run.profiles=migration'
```

Migration es opt-in y termina tras aplicar V1/V2. El historial queda en replica_meta.
V1 es inmutable; V2 concede la función de acceso únicamente a REPLICA_DB_USER.
El nombre del login debe ser un identificador PostgreSQL simple en minúsculas.

## Demostración opcional

Los datos demo no están en las migraciones. Usan dos universidades ficticias y
tres cuentas: demo.alba, demo.bruno y demo.celia. La carga explícita es repetible,
compara los datos existentes y revierte todo ante un conflicto.

En Windows, con las variables de BD anteriores todavía presentes:

```powershell
.\backend\scripts\demo-credentials.ps1 -Prepare
.\backend\scripts\start-local.ps1 -Mode Demo
```

El archivo generado queda cifrado con DPAPI en el perfil del usuario, fuera del
repositorio. Para pegar la contraseña en JavaFX sin imprimirla:

```powershell
.\backend\scripts\demo-credentials.ps1 -CopyPasswordFor demo.alba
# Pegar en el campo Contraseña y después limpiar el portapapeles:
Set-Clipboard -Value ''
```

Alternativa sin DPAPI: configurar las variables REPLICA_DEMO_ALBA_PASSWORD,
REPLICA_DEMO_BRUNO_PASSWORD y REPLICA_DEMO_CELIA_PASSWORD con contraseñas propias
de al menos 12 caracteres, y ejecutar Maven con `-Dspring-boot.run.profiles=demo`.
Conservar esas mismas contraseñas para repetir la carga; no compartirlas por Git.

## Ejecutar servidor y cliente

Retirar las variables administrativas de la terminal que atenderá HTTP:

```powershell
Remove-Item Env:REPLICA_MIGRATION_USER,Env:REPLICA_MIGRATION_PASSWORD -ErrorAction SilentlyContinue
# El script Demo limpia los secretos al terminar; reintroducir solo el de runtime.
Set-LocalSecret REPLICA_DB_PASSWORD
.\mvnw.cmd -f backend/pom.xml spring-boot:run '-Dspring-boot.run.profiles=local'
```

En otra terminal con JDK 21, desde la raíz:

```powershell
.\mvnw.cmd javafx:run
```

El servidor local escucha 127.0.0.1:8080 y permanece activo. El cliente usa
http://localhost:8080; REPLICA_API_URL permite cambiar su URL. No necesita JDBC.
El perfil local desactiva Secure únicamente para HTTP local; fuera de él se
requiere HTTPS. Las sesiones/cookies del cliente no se guardan en disco.

En IntelliJ importar pom.xml y backend/pom.xml como proyectos Maven separados y
seleccionar el JDK 21 propio para el proyecto y Maven Runner. Crear configuraciones
locales con Store as project file desmarcado; las configuraciones personales no
se distribuyen. Los scripts start-local.ps1 y start-client.ps1 son alternativas
Windows; detalles, variables y contratos REST en [backend/README.md](backend/README.md).

## Pruebas y estado de aceptación

```powershell
.\mvnw.cmd test
# Con Docker, PostgreSQL desechable mediante Testcontainers:
.\mvnw.cmd -f backend/pom.xml verify
# Alternativa Windows sin Docker, ajustando el directorio propio:
.\backend\scripts\verify-postgres.ps1 -PostgresBin 'C:\Program Files\PostgreSQL\18\bin'
```

Las pruebas de integración exigen una base vacía replica_test_* en un clúster
desechable; nunca usar datos reales como fixtures. La última ejecución aprobó
2 pruebas del cliente, 3 unitarias backend y 10 IT. La prueba visual JavaFX con
dos cuentas y respuesta tardía sigue pendiente; no se presenta la prueba API como
aceptación visual. Ver [REPLICA-STATE](backend/REPLICA-STATE.md).

## Antes de publicar

Revisar [SHARING-REVIEW.md](SHARING-REVIEW.md) y el listado completo de archivos.
No hay licencia propia seleccionada para el proyecto; el responsable debe decidir
los términos de uso antes de una publicación pública. Se mantienen los avisos
Apache del Maven Wrapper. No se incluyen credenciales, configuraciones IDE,
JDKs, bases temporales, logs ni binarios de compilación.
