# Campus: acceso y biblioteca académica

Cliente JavaFX modular y backend Maven independiente. Java 21, Spring Boot 4.1.2,
Spring MVC/Security, JPA/Hibernate, PostgreSQL y Flyway. El parent/BOM de Boot
gestiona las dependencias del backend. El cliente usa HttpClient y Gson.
No contiene JDBC ni credenciales de base de datos.

## Arranque local

Desde la raíz SteamBeta, en dos terminales:

```powershell
# Servidor persistente en 127.0.0.1:8080; perfil HTTP local explícito.
.\backend\scripts\start-local.ps1 -Mode Normal
# Cliente; cerrar su ventana no apaga el servidor.
.\backend\scripts\start-client.ps1
```

El servidor normal permanece atendiendo HTTP hasta Ctrl+C o Stop en IntelliJ.
Una configuración local **Replica Backend (local)** puede llamar al primer
script con `-Mode Normal` y mantiene Store as project file desmarcado. La ejecución desde IntelliJ se verificó en el entorno de desarrollo. Para el cliente, crear otro Shell Script local:

| Campo | Valor |
| --- | --- |
| Script file | `$PROJECT_DIR$/backend/scripts/start-client.ps1` |
| Script options | vacío |
| Working directory | `$PROJECT_DIR$` |
| Interpreter | `C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe` |
| Interpreter options | `-NoProfile -File` |
| Execute in the terminal / Store as project file | ambos desmarcados |

Crear visualmente esta segunda configuración y completar la prueba manual siguen
pendientes: el servicio de pantalla no está conectado. No se declaran verificados.

Los scripts prefieren `.replica-work/jdk21/jdk-21.0.13+9`; si no existe, usan
JAVA_HOME. No cambian variables globales. Los comandos directos siguen disponibles:

```powershell
$env:JAVA_HOME = (Resolve-Path '.replica-work/jdk21/jdk-21.0.13+9').Path
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:MAVEN_USER_HOME = Join-Path $PWD '.replica-work/m2'
java -version
.\mvnw.cmd -v
.\mvnw.cmd javafx:run
# Requiere las tres REPLICA_DB_* configuradas en ese proceso:
.\mvnw.cmd -f .\backend\pom.xml spring-boot:run '-Dspring-boot.run.profiles=local'
```

JavaFX usa http://localhost:8080. Para otra URL, configurar REPLICA_API_URL o
la propiedad `replica.api.url` de la JVM del cliente. Cada sesión tiene su propio
HttpClient/CookieManager y token CSRF en memoria. Las llamadas están fuera del
hilo JavaFX y la interfaz se actualiza con Platform.runLater. Cambiar sesión o
selección invalida callbacks anteriores, incluidos errores 401 tardíos.

## Configuración y otra máquina

Instalar JDK 21, PostgreSQL 18, configurar JAVA_HOME e importar ambos POM por
separado. Backend en classpath; cliente con module-info.java y mainClass modular.
Las versiones de backend siguen gestionadas por el BOM de Boot 4.1.2.

| Variable | Uso |
| --- | --- |
| REPLICA_DB_URL | JDBC local: jdbc:postgresql://localhost:5432/steam_academico |
| REPLICA_DB_USER / REPLICA_DB_PASSWORD | Login backend limitado y contraseña local |
| REPLICA_MIGRATION_USER / REPLICA_MIGRATION_PASSWORD | Propietario separado, solo Migration/Demo |
| REPLICA_HTTP_ADDRESS / REPLICA_HTTP_PORT | Servidor: predeterminados 127.0.0.1 / 8080 |
| REPLICA_API_URL | Cliente: predeterminado http://localhost:8080 |

Normal elimina las variables de migración heredadas. Las cookies son HttpOnly,
SameSite=Strict y Secure=true. Solo el perfil local permite Secure=false para HTTP
en loopback. Fuera de local, usar HTTPS. Sesiones de servidor en memoria, timeout
de inactividad 30 minutos; reiniciar el backend exige volver a entrar.

El lanzador Windows admite un almacén local de contraseñas cifrado con DPAPI en
`%LOCALAPPDATA%/ReplicaSteam/persistence/credentials.dpapi.json`, con ACL para el usuario propietario y SYSTEM.
No copiar DPAPI a otra cuenta o máquina. Para una instalación nueva configurar variables
propias en ejecuciones locales no compartidas; no escribir contraseñas en el
repositorio ni en comandos que queden en historial. El lanzador usa esas variables
cuando no existe almacén DPAPI. .env.example no se carga automáticamente.

Prerrequisitos que aprovisiona un administrador en una base propia vacía:

```sql
CREATE ROLE replica_app NOLOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE NOREPLICATION;
CREATE ROLE replica_academic NOLOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE NOREPLICATION;
CREATE ROLE replica_migrator LOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE NOREPLICATION;
GRANT CONNECT, CREATE ON DATABASE steam_academico TO replica_migrator;
CREATE ROLE replica_runtime LOGIN INHERIT NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE NOREPLICATION IN ROLE replica_app;
CREATE ROLE replica_academic_login LOGIN INHERIT NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE NOREPLICATION IN ROLE replica_academic;
GRANT CONNECT ON DATABASE steam_academico TO replica_runtime, replica_academic_login;
```

Asignar contraseñas con el gestor local o `\password` de psql. Runtime no debe
ser propietario ni miembro del migrador/replica_academic; el guard lo verifica.
Mantener el proceso académico separado. Se conserva ddl-auto=validate, SQL init
y open-in-view desactivados y sin cachés compartidas.

## Migraciones y demostración

Ejecutar scripts/preflight.sql y revisar el historial antes de migrar. Si replica
existe sin historial, preservar datos y detenerse. No clean, drop, baseline
automático, Hibernate update ni cambios a pg_hba.conf.

```powershell
.\backend\scripts\start-local.ps1 -Mode Migration
.\backend\scripts\demo-credentials.ps1 -Prepare
.\backend\scripts\start-local.ps1 -Mode Demo
```

Migration y Demo son procesos administrativos no web que terminan al completar.
En otra máquina, con variables propias, usar Maven con
`'-Dspring-boot.run.profiles=migration'` o `'-Dspring-boot.run.profiles=demo'`.

V1 permanece intacta: SHA-256
`35B3520FBDF7AC14CF6BC32391E0ABD5231C97860FF6C9200A6CAB6317DFA0AE`.
Flyway conserva su historial en replica_meta. V2 agrega access_account y la
función lookup_access_account(text), SECURITY DEFINER, search_path seguro y
máximo una cuenta por identificador normalizado. REPLICA_DB_USER proporciona el
placeholder backendLogin (identificador simple validado): solo ese login recibe
EXECUTE, no replica_app. La tabla no tiene SELECT runtime y usa RLS sin políticas
públicas. Su FK compuesta fija inequívocamente estudiante/universidad.

Las cuentas no se exponen como entidades REST ni repositorios generales. El hash
se usa solo en autenticación mediante PasswordEncoder y se borra del principal
antes de almacenarlo en sesión. Los hashes demo usan el formato delegado bcrypt.

Las migraciones no incluyen datos demo. Prepare genera contraseñas aleatorias una
sola vez en `demo.credentials.dpapi.json`, en el mismo directorio DPAPI privado.
Para introducirlas en JavaFX sin imprimirlas:

```powershell
.\backend\scripts\demo-credentials.ps1 -CopyPasswordFor demo.alba
# Pegar en el campo Contraseña; después:
Set-Clipboard -Value ''
```

| Cuenta | Universidad ficticia | Biblioteca |
| --- | --- | --- |
| demo.alba | Aurora | Programación 50 %, Matemática 100 %, Seminario sin requisitos |
| demo.bruno | Aurora | Redes 0 %, sin matrícula de Alba |
| demo.celia | Horizonte | Diseño de sistemas 100 % |

Repetir CopyPasswordFor con demo.bruno o demo.celia. No pegar contraseñas en el chat.
En otra máquina usar Prepare bajo su usuario o configurar localmente
REPLICA_DEMO_ALBA_PASSWORD, REPLICA_DEMO_BRUNO_PASSWORD y REPLICA_DEMO_CELIA_PASSWORD
(mínimo 12 caracteres) antes del perfil demo.

La carga usa UUID estables con prefijo lógico replica-demo-library-v1 y códigos
DEMO-*. Solo inserta; compara datos existentes y revierte todo ante un conflicto,
incluida una contraseña distinta. Crea/verifica las actividades obligatorias antes
de open_enrollment y nunca intenta modificar un plan congelado. Se ejecutó dos
veces localmente sin duplicados.

## Contrato REST

No enviar studentId/universityId en endpoints personales. ActorProvider deriva
ambos del principal autenticado; ActorTransactions fija los GUC transaccionales
con parámetros en la misma conexión JPA que usa la consulta.

| Método y ruta | Contrato |
| --- | --- |
| GET /api/auth/csrf | Público; JSON `{headerName,token}`; conservar su cookie |
| POST /api/auth/login | application/x-www-form-urlencoded con `login` y `password`, cabecera CSRF; 200 identidad mínima o 401 genérico |
| POST /api/auth/logout | Cabecera CSRF; invalida sesión/cookie; 204 |
| GET /api/me | `{studentId,alias,universityName}`; 401 JSON sin sesión |
| GET /api/me/library | Array de `{offeringId,code,name,termCode,sectionCode,requiredCount,completedCount,progressPercent}` |
| GET /api/me/library/{offeringId} | `{summary,activities}`; actividad: `{activityId,code,name,required,completed}`; 404 si no existe o no tiene matrícula |

Antes de login, GET csrf y enviar su token en la cabecera indicada. Login cambia
el identificador de sesión y retira el token anterior; obtener otro antes de
logout. Tras salir, descartar cookies/token. Falta/token incorrecto: 403 JSON.
GET privado sin sesión: 401 JSON, sin redirección HTML. No registrar contraseñas,
cookies, tokens ni hashes.

El progreso lo calcula PostgreSQL en student_progress. Cero requisitos produce
null y JavaFX muestra «Sin requisitos obligatorios». La biblioteca usa una consulta
de joins sin N+1. El detalle verifica matrícula antes de consultar actividades.
Perfil completo, tienda, canje UI, registro público, recuperación y LMS real
quedan fuera. Se preservan las funciones SQL anteriores y las políticas RLS.

## Pruebas y aceptación real

```powershell
.\mvnw.cmd test
# Con Docker, PostgreSQL 18.6 mediante Testcontainers:
.\mvnw.cmd -f .\backend\pom.xml verify
# Windows sin Docker: clúster PostgreSQL NUEVO desechable en puerto libre:
.\backend\scripts\verify-postgres.ps1
```

La suite exige base vacía replica_test_* y nunca usa steam_academico. No omite
los IT silenciosamente si falta Docker. El script nativo genera credenciales
de fixture, usa SCRAM y detiene su clúster en finally.

Ejecutado: 2 pruebas cliente, 3 unitarias backend y 10 IT PostgreSQL, todos
aprobados sin omisiones. Cubren los contratos previos y HTTP con cookies reales,
CSRF, fijación/invalidation de sesión, acceso correcto/incorrecto/deshabilitado,
aislamiento de cuentas/universidades, 404, progreso parcial/completo/null, permisos
de credenciales y reutilización de un pool de una conexión. El callback tardío
del cliente se probó con una simulación automática del guard de sesión.

Localmente: V2 y carga repetible aplicadas; ApiClient real de JavaFX verificado
con las tres cuentas, detalles y logout=401. Esto verifica HTTP/DTO, no renderizado
ni interacciones visuales. El proceso javafx:run se lanzó sin error de arranque,
pero la prueba manual completa está pendiente: el servicio nativo de pantalla
devuelve «native pipe is unavailable (os error 2)», incluso tras reconexiones.

Prueba manual pendiente: entrar como Alba, revisar las tres materias/actividades,
cerrar sesión, entrar como Bruno y comprobar que solo ve Redes. Verificar error
de credenciales, red y sesión vencida. Registrar resultados sin secretos.

Ensayo de respuesta tardía preparado (solo QA, Node.js; todavía no verificado
visualmente): ejecutar manual-latency-proxy.cjs, configurar el cliente con
REPLICA_API_URL=http://localhost:8687 y REPLICA_HTTP_TIMEOUT_SECONDS=300. Tras entrar
como Alba, crear `.replica-work/hold-next-detail`, seleccionar otra materia y
esperar el aviso no sensible del proxy. Salir y entrar como Bruno, crear
`.replica-work/release-detail` y comprobar que la respuesta anterior no repuebla
la pantalla. El proxy escucha solo loopback y no registra cuerpos/cookies.
Detenerlo y quitar ambas variables al acabar; timeout normal del cliente: 12 s.
