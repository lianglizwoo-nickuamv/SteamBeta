# REGISTRO REPLICA v5.0 — conversación y moderación de comunidad

La etapa anterior de cuentas independientes mediante API fue aceptada
expresamente por el usuario antes de esta entrega.

Implementados registro JavaFX, portada Mis comunidades, estado sin membresías,
lectura cronológica paginada, publicaciones, comentarios de un nivel, reportes,
edición y borrado propios, y cola de moderación por comunidad. Se conservan
sesiones, CSRF, llamadas fuera del hilo FX y guard de generación de sesión.
La biblioteca académica es una entrada opcional para cuentas vinculadas.

V4 agrega comunidad, membresías y publicaciones. V5 agrega comentarios, reportes,
estado de ocultación y bitácora de moderación. Las operaciones de contenido son
funciones limitadas de PostgreSQL; el servidor obtiene el autor de la sesión y
comprueba membresía y rol por cada operación. Los borrados son lógicos, eliminan
el texto y dejan marcadores. Las respuestas previas a una publicación eliminada
se conservan; una edición no restaura contenido oculto. El script
add-pilot-members.ps1 agrega miembros y moderadores con una transacción.

## Evidencia de la entrega

- Cliente compilado con Java 21; 3 pruebas aprobadas.
- Backend con 3 unitarias y 17 IT aprobados; sin fallos ni omisiones.
- En steam_academico: preflight confirmó V1–V4, 10 cuentas, 3 estudiantes,
  1 comunidad, 4 membresías y 2 publicaciones. Se creó y verificó un respaldo
  privado antes de V5; la migración V5 terminó correctamente. Las tablas nuevas
  se agregaron sin borrar o reescribir datos existentes.
- Las cuentas locales existentes quedaron configuradas para una práctica de
  dos miembros y una moderadora. El backend normal arrancó contra V5 en puerto
  alterno; perfil y publicaciones protegidos respondieron 401 sin sesión. La
  instancia temporal se detuvo al concluir.
- Miembro publica y otro lee; cuenta ajena y administrador de otro espacio
  rechazados. Permisos, CSRF, privacidad, paginación estable, validación,
  reintentos concurrentes, retiro de membresía y cuenta deshabilitada verificados.
- Pruebas académicas, registro y actualización de esquema existentes conservadas.
- Copia privada previa a V4 en .replica-work/backups/before-community-v4-20261116-113527.dump,
índice legible; V4 fue aplicada con migrador en steam_academico. V1–V4 son
inmutables. V5 se validó en PostgreSQL temporal y fue aplicada localmente tras
preflight y un respaldo nuevo, privado y verificado.
- Comprobación local con ApiClient real contra 18080, usando dos miembros y una
  cuenta ajena. Registro, lectura, publicación, reintento y cambio de cuenta
  completados. Este servidor de QA se detuvo al terminar.
- Base local al cerrar el QA inicial, 7 cuentas de prueba, 3 estudiantes conservados, 1 comunidad,
  2 membresías y 1 publicación sintética.
- Inspección visual directa de acceso y formulario de registro. Recorrido
  visual completo con credenciales y respuesta retrasada en pantalla pendiente.

Después del QA inicial, el usuario completó el recorrido manual y aportó
capturas. Se observaron acceso y estado vacío, incorporación al piloto,
publicación desde una cuenta, lectura desde otra cuenta miembro y estado vacío
al entrar con una cuenta sin membresía. En la presente entrega se verificaron
automáticamente cursor/paginación, conexión rechazada, 401 por sesión sin estado,
y aislamiento de respuestas tardías. La revisión visual de pantallas nuevas de
comentarios, reportes y moderación se entrega al usuario con pasos reproducibles.
Los identificadores y contraseñas de sus cuentas no se incorporan a este registro.

La guía de uso del equipo restringe automatizar diálogos de autenticación.
No se completó por UI el ingreso de credenciales; la evidencia de ApiClient
se distingue de una interacción visual.

Un primer intento de iniciar el servidor QA con Windows PowerShell 5 falló
por carga del módulo Security heredado del entorno de herramientas. Se corrigió
el lanzador de QA usando PowerShell 7, sin modificar las configuraciones globales.
No se atribuye ese fallo de preparación a las pruebas funcionales del backend.

Evidencia privada en .replica-work/community-ui-client-test.log,
community-ui-backend-verify.log, community-ui-local-migration.log y
community-ui-local-probe.log. Los logs de cierre del servidor QA pueden mostrar
terminación del proceso de prueba. No se publican credenciales ni tokens.

Sigue PILOT-QUICKSTART.md para reiniciar el backend actualizado, registrar tres
cuentas de prueba desde JavaFX, incorporar dos miembros y verificar el recorrido.
Comentarios, reportes y moderación precederán al uso con personas reales.
La evidencia visual básica del usuario queda registrada arriba. No se declara
aceptación global de la entrega ni verificación visual de los casos pendientes.

## Entrega anterior

# REGISTRO REPLICA v3.1 — prueba local reproducible

Se pausaron funcionalidades nuevas y se comprobó la entrega de cuentas
independientes contra el backend y PostgreSQL locales. Al empezar se encontraron
V1/V2/V3 ya aplicadas, por lo que no se ejecutó otra migración.

Se creó un respaldo privado y se ensayó su restauración en un clúster temporal.
El comprobador check-local-api.ps1 completó registro, login, perfil y logout
dos veces, reutilizando la cuenta local.check.member en la segunda ejecución.
Solo se añadió esa cuenta y su perfil; se conservaron las tres cuentas demo
y sus registros académicos. El backend real sigue disponible en el puerto 8080.

LOCAL-QUICKSTART.md explica las dos terminales, arranque, resultados, errores,
parada y reinicio. LOCAL-VERIFICATION.md registra la evidencia y el respaldo.
Este resultado verifica el recorrido API local; no declara aceptación visual
ni aceptación del usuario.

## Entrega anterior

# REGISTRO REPLICA v3.0 — fundamento de comunidad

Fecha de revisión y cambios, 16 de noviembre de 2026. Rama
codex/community-foundation, creada desde ecd62fe con árbol limpio.

## Nueva dirección y primera entrega

La prioridad es una comunidad utilizable sin matrícula ni integración
universitaria. Se conserva el módulo académico. El modelo y la matriz de
permisos propuestos están en COMMUNITY-PLAN.md.

Implementado el registro JSON con CSRF, un perfil propio por cuenta, accountId
en el principal y ROLE_USER. V3 permite ambos identificadores académicos nulos
juntos, conserva las FK existentes y crea perfiles para cuentas previas. Login
y /api/me mantienen su respuesta anterior; /api/account es la identidad propia
para comunidad y declara institutionalIdentityVerified=false.

La universidad y carrera declaradas son opcionales. El servidor no acepta
asignaciones de matrícula, verificación ni roles desde el registro.
La admisión al piloto se propone por membresía gestionada por el equipo.

La pantalla JavaFX sigue siendo la biblioteca académica existente. El registro
de esta entrega se verifica por API. Una cuenta independiente puede iniciar y
cerrar sesión, consultar su perfil y recibe 403 al pedir datos académicos.
Comunidad, publicaciones, comentarios y moderación todavía no están implementados.

## Verificación de esta entrega

- Consulta local de solo lectura antes de editar persistencia. V1/V2 exitosas,
  tres cuentas y tres estudiantes, todas las cuentas vinculadas.
- V3 preparada y aplicada únicamente en PostgreSQL desechable.
- Actualización de V2 poblada a V3 verificada con una cuenta previa, preservando
  IDs, hash y vínculo académico y rellenando su alias.
- Cliente Maven test, 2 pruebas aprobadas.
- Backend verify con PostgreSQL temporal 18.6, 3 unitarias y 13 de integración
  aprobadas, sin fallos ni omisiones.
- Registro sin crear estudiantes, matrículas ni saldos; perfil privado;
  login/salida; CSRF; conflicto concurrente de login y rollback de cuenta/perfil.
- JSON con studentId, universityId, role=ADMIN y verificación=true no asigna
  esos atributos. Contratos SQL y pruebas académicas anteriores preservados.
- V1/V2 sin modificaciones. steam_academico conserva V1/V2 en este trabajo.
- Evidencia local ignorada en .replica-work/community-foundation-verify.log.

Pendientes de próximas entregas, UI de registro y comunidad piloto; publicaciones
y comentarios con autoría; permisos por comunidad; reportes y conversaciones
ocultas/eliminadas. Sus pruebas todavía no corresponden a funciones existentes.
No se ejecutó una aceptación visual nueva.

## Estado anterior conservado como histórico

# REGISTRO REPLICA v2.1 — estado compartible

## Alcance

Java 21, cliente JavaFX modular, backend Spring Boot 4.1.2 independiente, JPA,
PostgreSQL 18 y Flyway. Implementados acceso por sesión, biblioteca matriculada,
detalle con actividades/progreso calculado por PostgreSQL y salida. Perfil
completo, tienda, registro público, recuperación y LMS real siguen fuera del lote.

V1 conserva las 13 tablas originales y RLS. V2 agrega cuentas vinculadas por FK
compuesta a universidad/estudiante y una función de autenticación con permisos
limitados al login backend. El runtime no puede leer la tabla de credenciales.
Sesiones en memoria, CSRF, protección frente a fijación de sesión y cookie
HttpOnly/SameSite; Secure salvo perfil local HTTP explícito en loopback.

## Evidencia ejecutada en el entorno de desarrollo

- Compilación modular del cliente con release 21.
- 2 pruebas cliente, 3 unitarias backend y 10 IT PostgreSQL aprobados, sin omisiones.
- IT en clúster PostgreSQL desechable: V1/V2, RLS, pool, concurrencia SQL, HTTP con
  cookies reales, CSRF, fijación/invalidación, aislamiento, 404 y progreso null.
- Migración V2 y carga demo opt-in repetible verificadas en una base local.
- ApiClient real del cliente verificado con las tres cuentas demo y salida=401.
- V1 inmutable: SHA-256
  `35B3520FBDF7AC14CF6BC32391E0ABD5231C97860FF6C9200A6CAB6317DFA0AE`.

No se atribuyen estos resultados a la máquina de un nuevo colaborador. Debe
aprovisionar sus roles, credenciales y datos, y volver a ejecutar las comprobaciones.
Las credenciales y registros detallados de ejecuciones locales no se distribuyen.

## Pendientes de aceptación

La prueba visual completa JavaFX con dos cuentas, errores de red/sesión y
respuesta tardía está pendiente. Falló el transporte de la herramienta de pantalla.
La simulación automática del guard de sesión y las pruebas HTTP no equivalen a
esa prueba manual. No se declara aceptación visual completa ni paridad con Steam.

## Preparación para GitHub

Se excluyen configuraciones IDE, secretos, DPAPI, logs, JDKs, bases temporales y
compilación. El registro detallado anterior se conserva como
REPLICA-STATE.local.md, archivo local ignorado; este es el registro compartible.
Revisar README raíz, backend/README.md y SHARING-REVIEW.md antes de publicar.
No se ha creado commit ni publicado un repositorio como parte de esta preparación.

Validación de distribución ejecutada: se exportó únicamente el índice de Git a
un directorio nuevo, sin .idea, secretos, JDK ni salidas de compilación. Se usó un
JDK 21 externo mediante JAVA_HOME. Esa copia aprobó 2 pruebas cliente, 3 unitarias
backend y 10 IT sobre un clúster PostgreSQL desechable nuevo, detenido al terminar.
V1 mantuvo el SHA-256 anterior también dentro de la exportación. Las 15 sondas de
gitignore pasaron; ningún archivo ignorado permanece en el índice. Maven Wrapper
y su JAR están incluidos; mvnw tiene modo ejecutable en Git.
