# Verificación local de cuentas independientes

Comprobación ejecutada el 16 de noviembre de 2026 sobre la entrega existente.
Se pausó el desarrollo de funcionalidades nuevas. Los cambios de este lote son
el comprobador local y las instrucciones de ejecución.

## Requisitos y estado inicial observados

- JDK Temurin 21.0.13 local y JDK Microsoft 21 en PATH.
- Maven Wrapper 3.8.5 ejecutado con Temurin 21.
- Herramientas PostgreSQL 18.6 instaladas en Program Files/PostgreSQL/18/bin.
- steam_academico accesible en localhost:5432 con la identidad migradora limitada.
- V1, V2 y V3 con success=true. account_profile ya existía.
- Tres cuentas, tres perfiles, tres estudiantes, tres saldos y cinco matrículas.
- Servidor real ya en ejecución en 127.0.0.1:8080, PID observado 28012.

No fue necesario ejecutar migraciones. No se cambiaron roles, contraseñas de
cuentas existentes, pg_hba.conf ni datos académicos.

## Respaldo y ensayo de recuperación

Se generó una copia custom completa de la base antes de registrar la cuenta local:

```text
.replica-work/backups/before-local-api-20261116-110544.dump
```

Tiene 82.067 bytes. Se guardaron su SHA-256 y el índice en archivos adyacentes.
El directorio de respaldo solo concede acceso al usuario Windows actual y SYSTEM.
El respaldo queda ignorado por Git. Contiene datos y hashes de contraseña y debe
tratarse como información privada.

Se restauró con pg_restore --exit-on-error en replica_recovery_check dentro de
un clúster PostgreSQL 18.6 nuevo, en otro puerto y únicamente en loopback.
Los roles requeridos se crearon como NOLOGIN en ese clúster de ensayo.
La restauración conservó las tres cuentas, tres perfiles, tres estudiantes,
cinco matrículas, V1/V2/V3 exitosas y replica_migrator como propietario de
access_account. El clúster temporal se detuvo al terminar.

Los logs de recuperación están en el directorio local ignorado
.replica-work/recovery-7f8471455dda41dd8269245f8a2d3e97/.
No se restauró sobre steam_academico ni se eliminó ningún dato original.

El respaldo representa el estado anterior a local.check.member. Para una
recuperación futura, crear primero una base vacía de recuperación con los roles
requeridos y usar el archivo dump. Validar la copia y decidir después si se
cambia la URL del backend. La restauración requiere credenciales locales propias;
no es un procedimiento para sobrescribir automáticamente la base en uso.

## Recorrido real por HTTP

Se ejecutó backend/scripts/check-local-api.ps1 con Windows PowerShell 5.1.
El script utilizó cookies en memoria y tokens CSRF obtenidos del servidor,
renovando el token después del login. No se imprimieron secretos.

| Operación | Primera ejecución | Segunda ejecución |
| --- | --- | --- |
| GET /api/account sin sesión | 401 | 401 |
| GET /api/auth/csrf | 200 | 200 |
| POST /api/auth/register | 201 | 409, cuenta ya existente |
| POST /api/auth/login | 200 | 200 |
| Renovar CSRF | 200 | 200 |
| GET /api/account autenticado | 200 | 200 |
| GET /api/me/library sin vínculo | 403 | 403 |
| POST /api/auth/logout | 204 | 204 |
| GET /api/account después de salir | 401 | 401 |

El perfil tenía únicamente accountId, alias, declaredUniversity, declaredCareer
e institutionalIdentityVerified=false. La respuesta de acceso tenía studentId=null.
No se crearon registros académicos para la cuenta.

La cuenta conservada es local.check.member. Su contraseña aleatoria está cifrada
con DPAPI fuera del repositorio, en el directorio ReplicaSteam/persistence del
LOCALAPPDATA del usuario. El comprobador la reutiliza al repetir la prueba.
Si el identificador ya corresponde a otra contraseña, se informa del fallo y
se puede elegir otro mediante -Login. No se cambia una contraseña existente.

## Estado final observado

- Cuatro cuentas y cuatro perfiles, una cuenta independiente.
- Las tres cuentas demo siguen habilitadas.
- Tres estudiantes, tres saldos y cinco matrículas.
- V1, V2 y V3 permanecen exitosas.
- El backend real continúa en el mismo puerto y proceso.
- Comprobador sin errores de sintaxis y git diff --check correcto.

Estos son resultados de la API local real, distintos de la evidencia anterior
de pruebas automáticas en una base desechable. No se abrieron nuevas pantallas
JavaFX. El registro visual y la interfaz comunitaria aún no están implementados.
La aceptación del usuario no se da por supuesta.

Las instrucciones para repetir el recorrido están en LOCAL-QUICKSTART.md.
