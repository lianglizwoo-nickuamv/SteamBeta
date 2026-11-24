# SteamBeta Campus

**SteamBeta Campus** es un ecosistema digital universitario de escritorio desarrollado con **Java 21**, **JavaFX**, **FXML**, **CSS**, **Spring Boot** y **PostgreSQL 18**. Está diseñado para que los estudiantes organicen su vida académica, colaboren en comunidades universitarias, encuentren compañeros de estudio, intercambien materiales sin fines monetarios y consulten oportunidades académicas desde una aplicación nativa de escritorio respaldada por políticas de seguridad a nivel de fila (RLS) en PostgreSQL.

---

## 1. Funcionalidades disponibles

- **Registro e inicio de sesión:** creación de cuentas estudiantiles, inicio de sesión mediante identificador o correo electrónico, protección CSRF y cookies `HttpOnly`.
- **Perfil académico:** visualización y edición de alias, universidad declarada, carrera, semestre, intereses académicos, biografía y control explícito de visibilidad del correo electrónico (privado por defecto).
- **Comunidades universitarias y catálogo global:** separación entre *Mis Comunidades* y *Explorar Comunidades*, búsqueda por categoría, niveles de privacidad (`PUBLIC`, `INVITE_ONLY`, `PRIVATE`), códigos de invitación y capacidad máxima configurable (2 a 50 integrantes).
- **Conversaciones y preguntas (Q&A) dentro de comunidades:** publicaciones, respuestas, marcado de soluciones y recursos compartidos por comunidad.
- **Calendario y fechas académicas:** vistas mensual, semanal y agenda combinando eventos comunitarios con tareas personales con vencimiento.
- **Planificación personal de tareas (Kanban):** tablero privado por estudiante (*Pendiente*, *En proceso*, *En revisión*, *Completada*) con prioridades, fechas de entrega y asociación opcional a materias registradas.
- **Equipos de Estudio:** ficha personal de estudio, búsqueda parcial insensible a mayúsculas por nombre, alias, materias, temas o apoyo, consulta de fichas públicas y contacto autorizado mediante correo electrónico público (`mailto:` y copiado al portapapeles). *(SteamBeta no incluye mensajería privada 1-a-1).*
- **Intercambio universitario de materiales y archivos:** publicación sin pagos de libros, apuntes y herramientas con hasta 5 archivos adjuntos (máx. 25 MB cada uno) almacenados en disco fuera del repositorio, validación de contenido y eliminación transaccional `afterCommit`.
- **Oportunidades universitarias:** cartelera de becas, pasantías, concursos y eventos con enlace oficial, fecha de cierre y administración por propietario.
- **Notificaciones:** bandeja de avisos con contador de no leídas.
- **Biblioteca Universitaria y registro manual de materias:** consulta de materias institucionales (cuando exista vinculación oficial) junto con el registro manual privado de materias por estudiante (año académico, período/semestre, código, grupo, docente, créditos, horario y notas), con distinción clara entre origen *Institucional* y *Registrada manualmente*.
- **Centro de Ayuda contextual:** guía en español adaptada automáticamente al módulo activo.
- **Configuración y diagnóstico de conexión:** estado de conectividad en tiempo real contra el backend local.

---

## 2. Tecnologías y requisitos

Versiones verificadas en [pom.xml](pom.xml) y [backend/pom.xml](backend/pom.xml):

| Componente | Versión verificada |
| :--- | :--- |
| **Java (JDK)** | Java 21 LTS (`<release>21</release>`) |
| **JavaFX** | 21.0.6 (`javafx-controls`, `javafx-fxml`) |
| **JavaFX Maven Plugin** | 0.0.8 |
| **Spring Boot** | 4.1.2 (`spring-boot-starter-parent`) |
| **PostgreSQL** | 18 (probado con PostgreSQL 18.4) |
| **Flyway** | Gestionado por `spring-boot-starter-flyway` (`flyway-database-postgresql`) |
| **Gson** | 2.13.2 |
| **JUnit Jupiter** | 5.12.1 |
| **Maven Wrapper** | Incluido (`mvnw.cmd` / `mvnw`) |
| **IDE recomendado** | IntelliJ IDEA |

### Requisitos mínimos en Windows
- Windows 10 u 11 (x64) con PowerShell 5.1 o superior.
- **JDK 21** instalado y disponible en `JAVA_HOME`.
- **PostgreSQL 18** instalado y en ejecución local en el puerto `5432`.
- **Git** para clonar el repositorio.

---

## 3. Estructura del proyecto

```text
SteamBeta/
├── pom.xml                                       # Proyecto Maven del cliente JavaFX
├── mvnw.cmd / mvnw / .mvn/                       # Maven Wrapper (no requiere Maven global)
├── README.md                                     # Guía de instalación y ejecución
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   ├── module-info.java                  # Módulo Java 21 (JavaFX + Gson + java.net.http)
│   │   │   └── com/example/steambeta/
│   │   │       ├── HelloApplication.java         # Punto de entrada JavaFX
│   │   │       └── client/
│   │   │           ├── AcademicWindow.java       # Ventana principal y carga de estilos
│   │   │           ├── ApiClient.java            # Cliente HTTP con cookies y CSRF
│   │   │           ├── EnvironmentConfig.java    # Configuración de entorno (por defecto http://127.0.0.1:8080)
│   │   │           └── ui/                       # Controladores FXML
│   │   └── resources/com/example/steambeta/client/
│   │       ├── academic.css                      # Hoja de estilos mínima sobre JavaFX Modena
│   │       └── fxml/                             # 57 vistas y diálogos FXML
│   └── test/java/com/example/steambeta/client/   # Pruebas unitarias y verificación agrupada FXML
└── backend/
    ├── pom.xml                                   # Proyecto Maven del backend Spring Boot 4.1.2
    ├── scripts/
    │   ├── setup-local-credentials.ps1           # Asistente DPAPI para contraseñas locales
    │   ├── start-local.ps1                       # Arranque del backend (-Mode Normal / Migration / Demo)
    │   ├── start-client.ps1                      # Arranque del cliente JavaFX vía Maven Wrapper
    │   ├── verify-postgres.ps1                   # Comprobación de PostgreSQL
    │   └── check-local-api.ps1                   # Comprobación rápida de endpoints locales
    └── src/
        ├── main/
        │   ├── java/com/example/steambeta/backend/
        │   │   ├── auth/                         # Autenticación, cuentas y perfil
        │   │   ├── community/                    # Comunidades, Q&A, recursos, eventos y errores
        │   │   ├── exchange/                     # Intercambio y ExchangeStorageService
        │   │   ├── library/                      # Biblioteca institucional y materias manuales (V16)
        │   │   ├── opportunity/                  # Oportunidades universitarias
        │   │   ├── study/                        # Equipos de Estudio y contacto autorizado
        │   │   └── task/                         # Mi Planificación (tareas privadas)
        │   └── resources/
        │       ├── application.properties
        │       ├── application-local.properties
        │       ├── application-migration.properties
        │       └── db/migration/                 # Migraciones Flyway V1 a V17
        └── test/java/com/example/steambeta/backend/
```

---

## 4. Instalación desde cero en Windows

### Paso 1. Instalar Java 21 y PostgreSQL 18
1. Instala **JDK 21 LTS** (por ejemplo, Eclipse Temurin 21) y verifica en PowerShell:
   ```powershell
   java -version
   ```
2. Instala **PostgreSQL 18** y comprueba que el servicio esté activo en `127.0.0.1:5432`.

### Paso 2. Clonar el repositorio
```powershell
git clone https://github.com/lianglizwoo-nickuamv/SteamBeta.git
cd SteamBeta
```

### Paso 3. Crear la base de datos y roles en PostgreSQL 18
Conéctate como superusuario `postgres` (usando `psql` o pgAdmin) y crea los roles con privilegios mínimos (`replica_migrator` para migraciones y `replica_runtime` para la ejecución del backend sin `SUPERUSER` ni `BYPASSRLS`) y la base `steam_academico`:

```sql
CREATE ROLE replica_migrator LOGIN PASSWORD 'TU_CONTRASENA_SEGURA_MIGRADOR' NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
CREATE ROLE replica_runtime  LOGIN PASSWORD 'TU_CONTRASENA_SEGURA_RUNTIME'  NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;

CREATE DATABASE steam_academico OWNER replica_migrator ENCODING 'UTF8';
GRANT CONNECT ON DATABASE steam_academico TO replica_runtime;
```

> **Importante:** sustituye las contraseñas de ejemplo por contraseñas propias seguras. Nunca guardes contraseñas reales en archivos del repositorio.

### Paso 4. Configurar credenciales locales cifradas con DPAPI
SteamBeta guarda las contraseñas cifradas por usuario de Windows mediante DPAPI en `%LOCALAPPDATA%\ReplicaSteam\persistence\credentials.dpapi.json` (fuera del repositorio). Ejecuta una sola vez:

```powershell
powershell -ExecutionPolicy Bypass -File .\backend\scripts\setup-local-credentials.ps1
```

El asistente solicitará de forma segura la contraseña de `replica_runtime` y la de `replica_migrator`.

### Paso 5. Comprobar conectividad con PostgreSQL (opcional)
```powershell
powershell -ExecutionPolicy Bypass -File .\backend\scripts\verify-postgres.ps1
```

### Paso 6. Aplicar las migraciones iniciales de Flyway (solo base nueva o actualización)
Para inicializar una base de datos `steam_academico` recién creada y aplicar las migraciones **V1–V17**, ejecuta:

```powershell
.\backend\scripts\start-local.ps1 -Mode Migration
```

#### Diferencia entre `-Mode Migration` y `-Mode Normal` en `start-local.ps1`
- **`-Mode Migration`**: ejecuta Spring Boot en el perfil `migration` (`spring.main.web-application-type=none`, `spring.flyway.enabled=true`) usando el rol `replica_migrator` para validar y aplicar migraciones pendientes de Flyway en `replica_meta.flyway_schema_history`, y termina automáticamente al finalizar. **Úsalo únicamente al inicializar una base nueva o cuando exista una nueva migración autorizada.** No es necesario ni recomendable ejecutarlo en cada arranque diario.
- **`-Mode Normal`** *(valor predeterminado)*: inicia el servidor web Spring Boot en el perfil `local` (`spring.flyway.enabled=false`) usando exclusivamente el rol restringido `replica_runtime` en `http://127.0.0.1:8080`. **Úsalo para el arranque cotidiano del proyecto.**

### Paso 7. Arrancar el backend Spring Boot
```powershell
.\backend\scripts\start-local.ps1 -Mode Normal
```

Comprueba que el servidor responde en `http://127.0.0.1:8080`:

```powershell
Invoke-WebRequest -Uri "http://127.0.0.1:8080/api/auth/csrf" -UseBasicParsing
```

Para detener el backend correctamente, pulsa `Ctrl + C` en la consola donde está ejecutándose (o detén la configuración de ejecución desde IntelliJ IDEA).

### Paso 8. Ejecutar el cliente JavaFX

#### Opción A: desde IntelliJ IDEA (recomendado)
1. Abre la carpeta raíz `SteamBeta` como proyecto Maven en **IntelliJ IDEA**.
2. Verifica que el SDK del proyecto sea **Java 21**.
3. Ejecuta la configuración **SteamBeta Client (JavaFX)** (clase principal `com.example.steambeta.HelloApplication`) o la tarea Maven `javafx:run`.

#### Opción B: desde PowerShell con Maven Wrapper
Con el backend ya activo en `http://127.0.0.1:8080`:

```powershell
.\mvnw.cmd -B javafx:run
```

También existe el script auxiliar `.\backend\scripts\start-client.ps1` (que configura JDK 21 y ejecuta `.\mvnw.cmd -B javafx:run`).

---

## 5. Almacenamiento de archivos (Intercambio Universitario)

Los adjuntos publicados en **Intercambio Universitario** son gestionados por `ExchangeStorageService` fuera del repositorio y de PostgreSQL:

- **Directorio por defecto:** `${user.home}/.steambeta/exchange-storage` (en Windows: `C:\Users\<usuario>\.steambeta\exchange-storage`).
- **Variable de entorno opcional para cambiar la ruta:** `STEAMBETA_EXCHANGE_STORAGE_DIR`.
- **Límites vigentes:**
  - Máximo **25 MB** por archivo (`spring.servlet.multipart.max-file-size=25MB`, solicitud máx. `30MB`).
  - Máximo **5 archivos** por publicación.
- **Formatos admitidos:**
  - Documentos y presentaciones: `pdf`, `doc`, `docx`, `ppt`, `pptx` (con validación real de firmas/encabezados y revisión en memoria de contenedores OOXML bloqueando macros `vbaProject.bin` y ZIP bombs).
  - Imágenes: `jpg`, `jpeg`, `png`, `gif`, `webp`, `bmp`, `tif`, `tiff`.
- **Respaldo independiente:** los archivos binarios residen en el sistema de archivos local, mientras que PostgreSQL almacena únicamente sus metadatos y claves seguras. Al respaldar el sistema, respalda tanto `steam_academico` como la carpeta configurada de `exchange-storage`.

---

## 6. Estado actual de Flyway (V1–V17)

El esquema de base de datos se encuentra en la **versión V17** y todas las migraciones se versionan en:

```text
backend/src/main/resources/db/migration/
```

- `V1`–`V13`: esquema base académico, cuentas independientes, comunidades piloto, Q&A, recursos, calendario, notificaciones, moderación global, perfil extendido y autenticación por correo.
- `V14`: acceso global a comunidades y capacidad máxima configurable.
- `V15`: persistencia RLS de Mi Planificación, Equipos de Estudio, Intercambio con adjuntos y Oportunidades.
- `V16`: registro manual privado de materias por estudiante en Biblioteca Universitaria (`replica.student_manual_subject` con RLS forzada).
- `V17`: corrección de `replica.update_account_profile_v2` en el guardado de Mi Perfil.

---

## 7. Pruebas automatizadas

- **Pruebas del cliente JavaFX (incluye verificación agrupada de los 57 FXML):**
  ```powershell
  .\mvnw.cmd -B test
  ```
- **Pruebas unitarias del backend:**
  ```powershell
  .\mvnw.cmd -B -f .\backend\pom.xml test
  ```

---

## 8. Solución de problemas (PowerShell)

- **Puerto 8080 ocupado:**
  ```powershell
  Get-NetTCPConnection -LocalPort 8080 -State Listen | Select-Object LocalAddress, LocalPort, OwningProcess
  ```
  Identifica el proceso con `Get-Process -Id <PID>` y detén la instancia anterior de SteamBeta si corresponde (`Stop-Process -Id <PID>`).
- **PostgreSQL detenido:**
  ```powershell
  Get-Service postgresql*
  ```
  Si aparece `Stopped`, inícialo desde *Servicios* de Windows o con `Start-Service postgresql-x64-18` (en PowerShell como administrador).
- **Credenciales locales no configuradas:**
  Ejecuta `powershell -ExecutionPolicy Bypass -File .\backend\scripts\setup-local-credentials.ps1` y verifica que exista `%LOCALAPPDATA%\ReplicaSteam\persistence\credentials.dpapi.json`.
- **Error de conexión / el cliente no encuentra el backend:**
  Comprueba que el backend esté activo en `http://127.0.0.1:8080` (`Invoke-WebRequest -Uri "http://127.0.0.1:8080/api/auth/csrf" -UseBasicParsing`). Si definiste `STEAMBETA_API_URL`, verifica que apunte a `http://127.0.0.1:8080`.
- **Error de carga FXML o `Unresolved compilation problems` en IntelliJ IDEA:**
  Ejecuta una compilación limpia con `.\mvnw.cmd -B clean test` y en IntelliJ usa **Build → Rebuild Project** para descartar archivos `.class` antiguos.
- **Java incorrecto:**
  Verifica `java -version` y `$env:JAVA_HOME`. El proyecto requiere **JDK 21**.
- **Maven no disponible en el sistema:**
  No es necesario instalar Maven globalmente; utiliza siempre `.\mvnw.cmd` desde la raíz del repositorio.
- **Problemas con almacenamiento de archivos en Intercambio:**
  Verifica permisos de escritura en `%USERPROFILE%\.steambeta\exchange-storage` (o en la ruta definida por `STEAMBETA_EXCHANGE_STORAGE_DIR`) y que el archivo no supere 25 MB ni contenga macros.
