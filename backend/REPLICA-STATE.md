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
