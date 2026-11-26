# Descargar, Configurar y Ejecutar SteamBeta Campus

Esta guía explica paso a paso cómo descargar **SteamBeta Campus** desde GitHub, preparar **PostgreSQL 18**, configurar el acceso administrativo (**STAMBETA ADMIN**) y ejecutar tanto el **backend Spring Boot** como el **cliente de escritorio JavaFX** en Windows desde cero.

---

## A. Descargar desde GitHub

Repositorio oficial: `https://github.com/lianglizwoo-nickuamv/SteamBeta`  
Rama estable de trabajo: `feature/steambeta-v13-stable`

### Método 1: Descargar ZIP (sin usar Git)

1. Abre el repositorio en el navegador: [https://github.com/lianglizwoo-nickuamv/SteamBeta](https://github.com/lianglizwoo-nickuamv/SteamBeta)
2. En el selector de ramas (parte superior izquierda), selecciona la rama **`feature/steambeta-v13-stable`**.
3. Pulsa el botón verde **`Code`**.
4. Selecciona **`Download ZIP`**.
5. Extrae el archivo comprimido en una carpeta local (por ejemplo, `C:\Users\<tu-usuario>\Documents\SteamBeta`).
6. Abre la carpeta extraída en PowerShell o en IntelliJ IDEA.

### Método 2: Clonar con Git (recomendado)

Abre PowerShell en la carpeta donde deseas guardar el proyecto y ejecuta:

```powershell
git clone --branch feature/steambeta-v13-stable https://github.com/lianglizwoo-nickuamv/SteamBeta.git
cd SteamBeta
```

---

## B. Requisitos

Requisitos reales verificados en [pom.xml](pom.xml) y [backend/pom.xml](backend/pom.xml):

| Requisito | Versión / Detalle |
| :--- | :--- |
| **Sistema operativo** | Windows 10 u 11 (x64) con PowerShell 5.1 o superior |
| **Java JDK** | **Java 21 LTS** (`<release>21</release>`, `<java.version>21</java.version>`), p. ej. Eclipse Temurin 21 |
| **Maven** | No es obligatorio instalarlo globalmente: el proyecto incluye **Maven Wrapper** (`mvnw.cmd` / `mvnw`) |
| **PostgreSQL** | **PostgreSQL 18** ejecutándose en `localhost:5432` |
| **IDE recomendado** | **IntelliJ IDEA** (para abrir el proyecto Maven y ejecutar el cliente JavaFX) |
| **Conexión a Internet** | Necesaria la primera vez para descargar dependencias Maven (Spring Boot 4.1.2, JavaFX 21.0.6, JasperReports 7.1.1, PostgreSQL JDBC, Flyway, Gson, JUnit 5) |

Comprueba Java 21 en PowerShell:

```powershell
java -version
```

---

## C. Configurar PostgreSQL 18

> **IMPORTANTE:** El esquema oficial de Flyway se encuentra en **V1–V17**. **No** crees migraciones `V18` ni `V19`, **no** modifiques `V1–V17` y ejecuta los scripts de mantenimiento únicamente con el rol DBA autorizado (`replica_migrator`), nunca con `replica_runtime`.

### 1. Instalar PostgreSQL 18

Instala **PostgreSQL 18** en Windows asegurándote de incluir las herramientas de línea de comandos (`psql.exe`, habitualmente en `C:\Program Files\PostgreSQL\18\bin`) y que el servicio `postgresql-x64-18` esté iniciado en el puerto `5432`.

### 2. Crear los roles y la base de datos `steam_academico`

Conéctate a PostgreSQL como superusuario `postgres` (con `psql` o pgAdmin) y ejecuta:

```sql
CREATE ROLE replica_migrator LOGIN PASSWORD 'TU_CONTRASENA_SEGURA_MIGRADOR' NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
CREATE ROLE replica_runtime  LOGIN PASSWORD 'TU_CONTRASENA_SEGURA_RUNTIME'  NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;

CREATE DATABASE steam_academico OWNER replica_migrator ENCODING 'UTF8';
GRANT CONNECT ON DATABASE steam_academico TO replica_runtime;
```

- `replica_migrator`: rol propietario exclusivo para migraciones iniciales (`V1–V17`) y scripts DBA de mantenimiento.
- `replica_runtime`: rol restringido usado por el backend Spring Boot en ejecución normal (sin `SUPERUSER` ni `BYPASSRLS`).

### 3. Configurar credenciales y variables de entorno

Consulta [backend/.env.example](backend/.env.example) como referencia de los nombres de variables admitidas (`REPLICA_DB_URL`, `REPLICA_DB_USER`, `REPLICA_DB_PASSWORD`, `REPLICA_MIGRATION_USER`, `REPLICA_MIGRATION_PASSWORD`, `SPRING_PROFILES_ACTIVE`, `REPLICA_HTTP_PORT`). **Nunca guardes contraseñas reales dentro del repositorio.**

En Windows, el método recomendado es guardar las contraseñas cifradas por usuario mediante **DPAPI** (`%LOCALAPPDATA%\ReplicaSteam\persistence\credentials.dpapi.json`, fuera del repositorio). Ejecuta una sola vez desde la raíz del proyecto:

```powershell
powershell -ExecutionPolicy Bypass -File .\backend\scripts\setup-local-credentials.ps1
```

Opcionalmente, verifica conectividad con PostgreSQL:

```powershell
powershell -ExecutionPolicy Bypass -File .\backend\scripts\verify-postgres.ps1
```

### 4. Aplicar las migraciones iniciales V1–V17 (solo en una base nueva)

En una base `steam_academico` recién creada, ejecuta **una sola vez** Flyway en modo migración:

```powershell
.\backend\scripts\start-local.ps1 -Mode Migration
```

Este comando aplica `V1` a `V17` usando `replica_migrator` y finaliza automáticamente al terminar.

### 5. Ejecutar los scripts de mantenimiento manual (requieren conexión DBA `replica_migrator`)

Después de aplicar `V1–V17` en una instalación nueva de PostgreSQL 18, ejecuta los cuatro scripts de mantenimiento manual en este **orden exacto** desde la raíz del proyecto:

1. **Aprovisionar la identidad técnica administrativa (`steambeta.admin.system`):**
   ```powershell
   powershell -ExecutionPolicy Bypass -File .\backend\scripts\provision-steambeta-admin-actor.ps1
   ```
   > Anota el UUID impreso en pantalla para `steambeta.admin.system`: lo usarás en la sección **D**.

2. **Aprovisionar la identidad técnica anónima (`steambeta.content.anonymous`, alias `Usuario eliminado`):**
   ```powershell
   powershell -ExecutionPolicy Bypass -File .\backend\scripts\provision-anonymous-content-actor.ps1
   ```

3. **Ampliar el límite de búsqueda del directorio administrativo a 200 resultados:**
   ```powershell
   & "C:\Program Files\PostgreSQL\18\bin\psql.exe" -h localhost -p 5432 -U replica_migrator -d steam_academico -v ON_ERROR_STOP=1 -f .\backend\scripts\patch-control-center-search-limit.sql
   ```

4. **Aprovisionar la función `SECURITY DEFINER` de borrado físico de estudiantes con conservación de aportes:**
   ```powershell
   & "C:\Program Files\PostgreSQL\18\bin\psql.exe" -h localhost -p 5432 -U replica_migrator -d steam_academico -v ON_ERROR_STOP=1 -f .\backend\scripts\provision-admin-physical-delete.sql
   ```

Todos los scripts anteriores requieren conexión autorizada con `replica_migrator` (los scripts `.ps1` leen automáticamente `replica_migrator` desde el almacén DPAPI configurado en el paso 3, o bien desde `REPLICA_MIGRATION_USER`/`REPLICA_MIGRATION_PASSWORD`). Una vez ejecutados, el backend trabajará únicamente con `replica_runtime`.

---

## D. Configuración de STEAMBETA ADMIN

La consola administrativa independiente **STAMBETA ADMIN** valida una clave privada compartida mediante su hash SHA-256 (`STEAMBETA_ADMIN_KEY_SHA256`) y el UUID del actor técnico (`STEAMBETA_ADMIN_ACTOR_ID`).

Ejecuta una sola vez el asistente interactivo indicando el UUID de `steambeta.admin.system` obtenido en el paso C.5.1:

```powershell
powershell -ExecutionPolicy Bypass -File .\backend\scripts\setup-admin-environment.ps1 -TechnicalActorId "<UUID_DE_STEAMBETA_ADMIN_SYSTEM>"
```

Qué hace este script:
- Solicita la clave administrativa privada de forma oculta (`Read-Host -AsSecureString`) y pide confirmación.
- Calcula su hash SHA-256 (64 caracteres hexadecimales) y descarta la clave en texto plano inmediatamente.
- Guarda **únicamente** `STEAMBETA_ADMIN_KEY_SHA256` y `STEAMBETA_ADMIN_ACTOR_ID` en las variables de entorno de usuario de Windows (`HKCU\Environment`) y en la sesión actual.

> **Seguridad:** La clave administrativa original es privada. Nunca la compartas por canales públicos ni la escribas en `.env`, `.properties`, scripts o archivos versionados. Tampoco publiques hashes reales en el repositorio.

---

## E. Iniciar el backend

Para el arranque diario del servidor Spring Boot, utiliza siempre **`-Mode Normal`** (nunca `-Mode Migration`):

```powershell
cd "C:\Users\galea\Documents\SteamBeta"
.\backend\scripts\start-local.ps1 -Mode Normal
```

*(Si clonaste el proyecto en otra carpeta, reemplaza la ruta de `cd` por la carpeta donde extrajiste o clonaste `SteamBeta`).*

### Verificar que Spring Boot arrancó correctamente

1. En la consola verás el arranque en el puerto `8080` sobre `steam_academico` con el perfil `local` y `replica_runtime`.
2. En otra ventana de PowerShell, comprueba que responde el endpoint CSRF:
   ```powershell
   Invoke-WebRequest -Uri "http://127.0.0.1:8080/api/auth/csrf" -UseBasicParsing
   ```
   Una respuesta `StatusCode : 200` con un JSON que contiene `token` y `headerName` confirma que el backend está listo.

Para detener el backend, pulsa `Ctrl + C` en su terminal.

---

## F. Iniciar el cliente JavaFX

### Opción 1: Desde IntelliJ IDEA (recomendado)

1. Abre **IntelliJ IDEA** → **File → Open** y selecciona la carpeta raíz `SteamBeta` (donde está `pom.xml`).
2. Permite que IntelliJ cargue el proyecto **Maven** (si no lo hace automáticamente, abre la pestaña **Maven** y pulsa **Reload All Maven Projects**).
3. Verifica en **File → Project Structure → Project** que el **SDK** sea **Java 21**.
4. Crea o selecciona la configuración de ejecución **`SteamBeta Client (JavaFX)`**:
   - Tipo: **Application**
   - Main class: `com.example.steambeta.Launcher` (también es válida `com.example.steambeta.HelloApplication` vía `javafx:run`)
   - Module: `SteamBeta`
   - Working directory: `$PROJECT_DIR$`
5. Con el backend ejecutándose en `http://127.0.0.1:8080`, pulsa **Run** sobre **`SteamBeta Client (JavaFX)`**.

### Opción 2: Desde PowerShell (sin IDE)

Con el backend activo en otra terminal:

```powershell
.\backend\scripts\start-client.ps1
```

O directamente con Maven Wrapper:

```powershell
.\mvnw.cmd -B javafx:run
```

---

## G. Solución de problemas

| Problema | Diagnóstico y solución concreta |
| :--- | :--- |
| **Java 21 no encontrado** | Comprueba `java -version` y `$env:JAVA_HOME`. Instala **JDK 21 LTS** (Eclipse Temurin 21) y apunta `JAVA_HOME` a su carpeta raíz (p. ej. `C:\Program Files\Eclipse Adoptium\jdk-21...`). |
| **PostgreSQL no disponible** | Verifica el servicio con `Get-Service postgresql*`. Si está detenido, inícialo en PowerShell como administrador: `Start-Service postgresql-x64-18`. |
| **Variables de entorno o DPAPI faltantes** | Si `start-local.ps1` avisa falta de DPAPI, ejecuta `.\backend\scripts\setup-local-credentials.ps1`. Si avisa falta de variables de STEAMBETA ADMIN, ejecuta `.\backend\scripts\setup-admin-environment.ps1 -TechnicalActorId "<UUID>"`. |
| **Puerto 8080 ocupado** | Consulta el proceso con `Get-NetTCPConnection -LocalPort 8080 -State Listen \| Select-Object LocalAddress, LocalPort, OwningProcess`, inspecciona con `Get-Process -Id <PID>` y detén instancias previas con `Stop-Process -Id <PID>`. |
| **Error de conexión a `steam_academico`** | Ejecuta `.\backend\scripts\verify-postgres.ps1`. Confirma que existen la base `steam_academico`, los roles `replica_runtime` y `replica_migrator`, y que las contraseñas en DPAPI coinciden con PostgreSQL. |
| **Errores de dependencias Maven** | Asegúrate de tener conexión a Internet en la primera compilación y ejecuta `.\mvnw.cmd -B clean test` (cliente) y `.\mvnw.cmd -B -f .\backend\pom.xml test-compile` (backend). No necesitas Maven global; usa siempre `.\mvnw.cmd`. |
| **Problemas de JavaFX / FXML en IntelliJ** | Verifica que el SDK del proyecto sea **Java 21**, haz **Maven → Reload All Maven Projects** y **Build → Rebuild Project**. Si falla al arrancar directo, prueba `.\mvnw.cmd -B javafx:run`. |
| **Funciones manuales de PostgreSQL no aprovisionadas** | Si en STEAMBETA ADMIN aparece bloqueo de permisos o falta la función de borrado físico / límite de 200, ejecuta en orden los 4 scripts de la sección **C.5** con el rol `replica_migrator`. |

---

## H. Estructura del proyecto

```text
SteamBeta/
├── pom.xml                                       # Proyecto Maven del cliente JavaFX 21
├── mvnw.cmd / mvnw / .mvn/                       # Maven Wrapper incluido
├── README.md                                     # Presentación general y acceso rápido
├── DESCARGAR-Y-EJECUTAR.md                       # Esta guía paso a paso de descarga e instalación
├── STEAMBETA-*.md                                # Documentación de arquitectura, despliegue, UI, moderación y cambios
├── scripts/
│   └── package-client.ps1                        # Empaquetado del cliente
├── src/                                          # Cliente de escritorio JavaFX (Java 21)
│   ├── main/java/com/example/steambeta/
│   │   ├── HelloApplication.java / Launcher.java # Puntos de entrada JavaFX
│   │   └── client/                               # ApiClient, AcademicWindow, EnvironmentConfig y controladores ui/
│   ├── main/resources/com/example/steambeta/client/
│   │   ├── academic.css                          # Estilos JavaFX
│   │   └── fxml/                                 # Vistas FXML (incluye AdminLayout y ConfirmStudentRemovalDialog)
│   └── test/java/com/example/steambeta/client/   # Pruebas del cliente y verificación FXML
└── backend/                                      # Backend Spring Boot 4.1.2 (Java 21 + PostgreSQL 18 + JasperReports 7.1.1)
    ├── pom.xml                                   # Proyecto Maven del backend
    ├── .env.example                              # Plantilla de variables de entorno (sin secretos)
    ├── scripts/                                  # Scripts PowerShell y SQL de configuración, mantenimiento y arranque
    │   ├── setup-local-credentials.ps1           # Configuración DPAPI para PostgreSQL
    │   ├── setup-admin-environment.ps1           # Configuración segura de STEAMBETA ADMIN
    │   ├── start-local.ps1                       # Arranque del backend (-Mode Normal / Migration / Demo)
    │   ├── start-client.ps1                      # Arranque del cliente JavaFX
    │   ├── verify-postgres.ps1                   # Diagnóstico de PostgreSQL
    │   ├── check-local-api.ps1                   # Comprobación rápida de endpoints locales
    │   ├── provision-steambeta-admin-actor.ps1   # Mantenimiento 1: actor técnico ADMIN
    │   ├── provision-anonymous-content-actor.ps1 # Mantenimiento 2: actor técnico anónimo
    │   ├── patch-control-center-search-limit.sql # Mantenimiento 3: límite de 200 usuarios
    │   └── provision-admin-physical-delete.sql   # Mantenimiento 4: borrado físico con conservación de aportes
    └── src/
        ├── main/java/com/example/steambeta/backend/
        │   ├── admin/                            # Consola STEAMBETA ADMIN, reportes y borrado físico
        │   ├── auth/                             # Autenticación, CSRF, sesiones y perfil
        │   ├── community/                        # Comunidades, Q&A, recursos y almacenamiento comunitario
        │   ├── controlcenter/                    # Consultas y moderación global
        │   ├── exchange/                         # Intercambio universitario y adjuntos
        │   ├── library/                          # Biblioteca institucional y materias manuales
        │   ├── opportunity/                      # Oportunidades universitarias
        │   ├── report/                           # Reportes JasperReports (PDF y XLSX)
        │   ├── study/                            # Equipos de Estudio
        │   └── task/                             # Planificación académica personal
        ├── main/resources/
        │   ├── application*.properties           # Perfiles Spring Boot (sin credenciales reales)
        │   ├── db/migration/                     # Migraciones Flyway V1 a V17
        │   └── reports/                          # Plantillas JRXML personales y administrativas
        └── test/java/com/example/steambeta/backend/ # Pruebas automatizadas del backend
```
