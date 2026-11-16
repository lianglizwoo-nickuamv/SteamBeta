# Piloto de comunidad en JavaFX

## Estado de esta entrega

En la rama codex/community-foundation están implementados registro visual,
acceso, comunidades propias, estado vacío, lectura paginada, publicación y salida.
La biblioteca académica conserva sus rutas y una entrada desde la cabecera para
cuentas con vínculo académico. Las cuentas independientes entran a Mis comunidades.

La comprobación manual del usuario confirmó acceso, estado vacío, entrada al
piloto, publicación desde una cuenta y lectura desde otra cuenta miembro.
También confirmó que una cuenta sin membresía ve el estado vacío después de
cambiar de sesión. La paginación, los errores de red, la sesión vencida y las
respuestas retrasadas siguen pendientes de comprobación visual. Las pruebas
HTTP y del cliente ApiClient se registran aparte.

Solo se utilizarán cuentas de prueba. Comentarios, reportes y moderación se
completarán antes de introducir usuarios reales.

## 1. Arrancar el backend

Abre dos terminales PowerShell en IntelliJ. En ambas sitúate en la raíz:

```powershell
cd 'C:\Users\Woo\Downloads\ProyectoSteam\SteamBeta'
```

Si había un backend arrancado antes de actualizar el código, detén esa ejecución
con Stop o Ctrl+C. Inicia una sola instancia del backend nuevo.

La base local de desarrollo ya tiene V4 aplicada y un respaldo anterior privado
en .replica-work/backups/before-community-v4-20261116-113527.dump.
En otra copia o máquina primero revisa el historial y ejecuta Migration,
que termina antes de iniciar el modo Normal:

```powershell
.\backend\scripts\start-local.ps1 -Mode Migration
```

En esta máquina, el comando habitual en la terminal 1 es:

```powershell
.\backend\scripts\start-local.ps1 -Mode Normal
```

Debe permanecer ejecutándose. El arranque correcto muestra Tomcat started on
port 8080 y Started BackendApplication. Port 8080 was already in use indica
que hay otra instancia; identifica la ejecución existente antes de detenerla.

Si se usó una URL de QA, revisa las variables REPLICA_HTTP_PORT y REPLICA_API_URL
en tu terminal. Para el procedimiento normal se utilizan servidor 8080 y
cliente http://localhost:8080. La verificación de esta entrega usó 18080
temporalmente y ese servidor de QA se detuvo al terminar.

## 2. Abrir JavaFX y crear tres cuentas

En la terminal 2:

```powershell
.\backend\scripts\start-client.ps1
```

Debe abrir Campus. Esta terminal permanece ocupada mientras JavaFX está abierto.

En la ventana:

1. Pulsa Crear una cuenta.
2. Introduce alias Ana Piloto e identificador piloto.ana.
3. Elige y confirma una contraseña propia de al menos 12 caracteres. El máximo
   es 72 bytes UTF-8. La universidad y carrera son opcionales y declaradas.
4. Pulsa Crear cuenta. Debes volver al acceso con la cuenta creada.
5. Entra como piloto.ana. Debe aparecer Mis comunidades con el estado vacío.
6. Cierra sesión. Crea piloto.luis con alias Luis Piloto y piloto.sin-grupo con
   alias Visitante Piloto, utilizando contraseñas locales propias.

Si esos identificadores ya existen, utiliza las cuentas conocidas o elige otros
y cambia los nombres del siguiente comando. Un 409 no cambia contraseñas.

## 3. Incorporar dos miembros al piloto

Abre una tercera terminal PowerShell en la raíz. Este es un procedimiento del
equipo administrador, no una operación disponible desde la cuenta de estudiante.

```powershell
.\backend\scripts\add-pilot-members.ps1 -Login @('piloto.ana','piloto.luis')
```

El comando termina. Crea el espacio sistemas-piloto si no existe y activa las
membresías MEMBER indicadas. Conserva cualquier rol comunitario superior
preexistente. No incorpora piloto.sin-grupo.

Las cuentas deben existir y estar habilitadas. Si alguna no existe, toda la
transacción se revierte. Repetir el comando no duplica la comunidad ni membresías.
Un miembro conectado puede pulsar Actualizar comunidades sin volver a entrar.

Aquí se usan las credenciales DPAPI de replica_migrator. En otra máquina se
configuran REPLICA_MIGRATION_USER y REPLICA_MIGRATION_PASSWORD de forma local.
El script apunta por defecto a localhost:5432/steam_academico. Para otra base,
indica explícitamente -DatabaseHost, -Port y -Database. Mantén esas credenciales
administrativas fuera del proceso HTTP y del cliente JavaFX.

## 4. Publicar y leer desde otra cuenta

1. Entra como piloto.ana y pulsa Actualizar comunidades.
2. Abre Piloto de Sistemas.
3. Escribe un título de 3 a 160 caracteres y contenido de 1 a 10 000.
4. Pulsa Publicar duda. Mientras se procesa, los campos y el botón se bloquean.
5. Debe aparecer el título, el alias Ana Piloto, la fecha y el contenido.
6. Cierra sesión y entra como piloto.luis.
7. Abre el piloto y comprueba que puedes leer la publicación de Ana.
8. Cierra sesión y entra como piloto.sin-grupo. Debe aparecer el estado vacío,
   sin comunidad, formulario de publicación ni identidad de las cuentas anteriores.

Las publicaciones compartidas siguen disponibles para miembros del espacio.
Al cambiar de cuenta se descartan respuestas pendientes y se limpia la navegación
de la sesión anterior. La nueva cuenta vuelve a obtener sus permisos del servidor.

## 5. Comprobaciones manuales adicionales

- Intenta un título de un carácter o contenido vacío. Debe mostrarse una
  indicación de longitud sin enviar una publicación válida.
- Pulsa Ver más recientes para recargar. Más antiguas aparece cuando quedan
  publicaciones; el cliente carga hasta diez por página.
- Detén el backend y pulsa Actualizar comunidades o Ver más recientes.
  Debe aparecer un error de conexión y la interfaz debe seguir respondiendo.
- Reinicia el backend. La sesión anterior ya no existe y una consulta protegida
  debe llevar al acceso indicando sesión vencida.
- Con una petición de lectura retrasada, salir y entrar con otra cuenta debe
  conservar la pantalla de la nueva cuenta. El guard de sesión tiene pruebas
  automáticas; este ensayo dentro de la ventana sigue pendiente.
- Para cuentas demo con vínculo, Biblioteca académica abre el módulo conservado.
  Mis comunidades permite regresar usando la misma sesión.

Una publicación con respuesta incierta conserva su clave de envío mientras
no cambies el texto. Reintentar devuelve la misma publicación. Cambiar el texto
representa una nueva intención de publicación.

## Contrato HTTP y permisos implementados

| Ruta | Acceso y resultado |
| --- | --- |
| GET /api/me/communities | Sesión válida; devuelve solo membresías activas en espacios activos |
| GET /api/communities/{id}/posts | Miembro activo; página cronológica descendente |
| POST /api/communities/{id}/posts | Miembro activo y CSRF; título, body y requestKey |

La lista admite limit de 1 a 50 y un cursor compuesto por beforeTime y beforeId.
La respuesta devuelve posts, nextBeforeTime y nextBeforeId. El orden usa
created_at DESC e id DESC, incluyendo desempate y nuevas publicaciones entre páginas.

El autor se deriva del principal. Campos de autor o rol enviados por JSON
no asignan identidad ni privilegios. Cada lectura y creación vuelve a comprobar
cuenta habilitada, comunidad activa y membresía activa. Una cuenta ajena recibe
404 aunque conozca el ID; una sesión ausente recibe 401.

El runtime solo puede ejecutar funciones SQL limitadas para estas operaciones.
No tiene SELECT/INSERT directo en las tres tablas nuevas. El equipo administra
el piloto con la identidad migradora separada.

## Pruebas ejecutadas

- Cliente compila con Java 21; 2 pruebas del guard de sesión aprobadas.
- Backend, 3 pruebas unitarias y 16 de integración PostgreSQL aprobadas.
- Dos miembros publican/leen; una cuenta sin membresía y un ADMIN de otro
  espacio reciben rechazo por las rutas directas.
- Autoría del principal, privacidad, CSRF, estado vacío, cambios de cuenta,
  retirada de membresía y deshabilitación de cuenta.
- Paginación con fechas iguales y publicación nueva entre páginas.
- Reintento concurrente con una sola publicación y conflictos de clave.
- Contratos académicos y de registro anteriores conservados.

Además se probó el ApiClient real de JavaFX contra el backend local nuevo en
18080. Se verificaron registro, acceso, estado vacío, publicación entre dos
miembros, rechazo de otra cuenta y logout/cambio de identidad. Esa prueba
utiliza HTTP y DTOs, no acredita el recorrido visual de la ventana.

Los datos locales conservados de QA son local.check.member y local.check.reader
como miembros, local.check.outsider sin membresía y local.check.javaclient como
cuenta independiente recién registrada. Sus contraseñas están cifradas fuera
del repositorio. No necesitas esas contraseñas para seguir el recorrido manual
con tus tres cuentas piloto.

Si el equipo restableció las contraseñas de tus cuentas piloto y las guardó
localmente, pilot-credentials.ps1 permite copiar la contraseña de un identificador
de acceso al portapapeles. Utiliza el identificador con el que registraste la
cuenta, que puede ser distinto del alias público. El archivo DPAPI permanece
fuera del repositorio y solo se puede utilizar desde el usuario Windows que lo creó.

V1–V3 siguen intactas. V4 añade comunidad, membresía y publicación. Quedan fuera
edición/borrado, comentarios, reportes, acciones de moderación, etiquetas,
adjuntos, mensajería, votos, puntos, logros e integraciones.
