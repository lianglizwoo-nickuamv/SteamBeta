# Prueba local del piloto de comunidad

Esta guía valida el recorrido en JavaFX con cuentas de prueba. La cuenta, alias y
perfil son independientes de cualquier matrícula institucional. No usar cuentas
reales: la moderación y los reportes son herramientas iniciales de piloto.

## 1. Migrar y arrancar

En IntelliJ, abre dos terminales PowerShell en la raíz del repositorio:

```powershell
cd 'C:\Users\Woo\Downloads\ProyectoSteam\SteamBeta'
```

En esta instalación ya se confirmó V1–V4, se hizo un respaldo privado validado
y se aplicó V5. No necesitas volver a migrar para esta prueba. En otra base,
antes de migrar, en la pestaña Database ejecuta las consultas de solo lectura de
`backend/scripts/preflight.sql`. Comprueba que la conexión sea
`steam_academico`, el historial tenga V1–V4 aplicadas y que las tablas de cuenta,
comunidad y publicaciones contengan tus datos locales. Haz un respaldo desde
pgAdmin o con `pg_dump -Fc` antes de migrar. No uses `clean`, `drop` ni restaures
una copia sobre la base activa.

Detén el backend que ya estuviera ejecutándose. En una instalación que aún tenga
V4, en terminal 1 ejecuta la migración V5; el comando termina al acabar:

```powershell
.\backend\scripts\start-local.ps1 -Mode Migration
```

Si termina correctamente, inicia el backend normal desde terminal 1:

```powershell
.\backend\scripts\start-local.ps1 -Mode Normal
```

Debe permanecer abierto y mostrar `Tomcat started on port 8080` y
`Started BackendApplication`. No inicies otra instancia en el puerto 8080.
En terminal 2 ejecuta el cliente, que permanece activo mientras la ventana esté
abierta:

```powershell
.\backend\scripts\start-client.ps1
```

## 2. Preparar dos miembros y un moderador

Usa tres cuentas de prueba ya registradas y sus **identificadores de acceso**;
el alias visible no necesariamente coincide con el identificador. No registres
de nuevo una cuenta que ya existe. El comando administrativo activo las agrega
al piloto sin duplicar membresías y asigna el rol solicitado:

```powershell
.\backend\scripts\add-pilot-members.ps1 `
  -Login @('login-miembro-1','login-miembro-2') `
  -ModeratorLogin @('login-moderador')
```

El comando termina. Cualquier cuenta inexistente o deshabilitada revierte el
conjunto completo. Miembros y moderadores deben ser personas distintas. Para
recuperar una contraseña que ya se guardó localmente mediante DPAPI, usa
`backend/scripts/pilot-credentials.ps1 -CopyPasswordFor <identificador>`; si la
cuenta no tiene contraseña en ese archivo, restablécela con el proceso local que
utiliza el administrador. El identificador sirve para iniciar sesión; el alias
solo se muestra en la comunidad.

El moderador queda limitado a esta comunidad: `MODERATOR` dentro del piloto no
otorga privilegios globales. Los roles solo se cambian con el script local del
equipo, nunca desde solicitudes HTTP.

## 3. Probar publicaciones y paginación

1. Entra con el primer miembro, abre **Mis comunidades**, pulsa **Actualizar
   comunidades** y luego **Entrar a la comunidad**.
2. Publica una pregunta de prueba. Debe aparecer en la parte superior del feed.
3. Cierra sesión e inicia con el segundo miembro. Abre el mismo piloto y verifica
   que la pregunta persiste y puede leerse.
4. Publica suficientes preguntas para que haya más de diez. **Más antiguas** debe
   cargar la página siguiente sin repetir elementos. **Ver más recientes** vuelve
   al inicio; una publicación nueva debe aparecer arriba.
5. Prueba la paginación también desde **Más antiguas** después de que otra cuenta
   agregue publicaciones. El orden es fecha descendente, con UUID como desempate.

La lectura usa páginas cursoradas de diez publicaciones en JavaFX. PostgreSQL
limita y ordena la página en servidor; no se cargan todas las publicaciones para
paginar en memoria.

## 4. Probar comentarios, autoría y borrado

1. El segundo miembro abre una publicación del primero y agrega un comentario.
2. El autor del comentario puede editarlo y eliminarlo. En la interfaz, usa los
   botones **Editar comentario** y **Eliminar comentario**.
3. El autor de la publicación puede usar **Editar** o **Eliminar**. Otro miembro
   no verá esos botones; además, el servidor rechaza la operación aunque se
   invoque la ruta HTTP directamente.
4. Para comprobar la conversación tras borrado, agrega primero un comentario y
   luego elimina la publicación original. El título queda como “Publicación
   eliminada”, el texto original se borra y los comentarios existentes siguen
   visibles. No se aceptan comentarios nuevos en esa publicación.

Comentarios son de un nivel y no se admiten respuestas a comentarios. Borrar un
comentario también borra su texto y deja un marcador en la conversación.

## 5. Probar reportes y moderación

1. Como segundo miembro, pulsa **Reportar** en una publicación ajena o
   **Reportar comentario** y escribe un motivo concreto.
2. Cambia a la cuenta moderadora. Abre el piloto y pulsa **Reportes y moderación**.
3. Revisa el motivo y contenido. **Ocultar y resolver** oculta la publicación o
   comentario para los miembros, escribe el evento de auditoría y cierra el
   reporte como resuelto. **Descartar reporte** conserva el reporte en el
   historial y registra el motivo de resolución.
4. Vuelve al miembro y actualiza la comunidad. Lo oculto ya no debe aparecer en
   el feed o la conversación. La edición del autor no puede levantar la
   ocultación; la operación de edición queda rechazada por el servidor.
5. Prueba acceder a la cola de reportes como miembro y como moderador de otro
   espacio: ambas peticiones deben ser rechazadas.

Solo hay una denuncia abierta por persona y contenido. Al resolverse, puede
presentarse una nueva. La cola muestra reportes abiertos; el historial completo
y los eventos quedan en PostgreSQL. No hay interfaz de apelación ni restauración
de contenido oculto en esta entrega.

## 6. Errores de conexión y sesión vencida

**Conexión:** mientras JavaFX siga abierto, detén el backend con Ctrl+C en
terminal 1 y pulsa **Ver más recientes** o vuelve a cargar comunidades. Debe
aparecer un mensaje de conexión y la ventana debe seguir respondiendo. Reinicia
el backend con `start-local.ps1 -Mode Normal` y vuelve a intentar.

**Sesión vencida:** reiniciar el backend elimina las sesiones guardadas en
memoria. Sin cerrar JavaFX, pulsa **Ver más recientes**. El backend responderá
401 y el cliente debe limpiar la sesión local, abandonar los datos anteriores y
mostrar el acceso con el aviso de sesión vencida. Inicia sesión de nuevo.

**Respuesta tardía:** las pruebas automatizadas retienen una respuesta anterior
y confirman que, al cerrar sesión y entrar con otra cuenta, no puede reemplazar
la pantalla de la nueva cuenta. La prueba visual de temporización manual no es
necesaria para aceptar esa protección del guard; la UI sigue mostrando el estado
actual de la sesión.

## 7. Detener y volver a iniciar

- Para detener el backend, vuelve a terminal 1 y pulsa Ctrl+C.
- Para cerrar JavaFX, pulsa **Cerrar sesión** y cierra la ventana; terminal 2
  termina cuando se cierra la aplicación.
- Para volver a iniciar, arranca una instancia del backend normal y luego el
  cliente. No vuelvas a ejecutar Migration salvo que una nueva versión agregue
  otra migración.

## Qué se verificó automáticamente

- Migración incremental V1–V5 sobre PostgreSQL 18.6 temporal; no se alteró
  `steam_academico`.
- 17 pruebas de integración backend, incluidas dos miembros y un moderador,
  permisos por comunidad, autoría, edición/borrado, reportes, ocultación,
  registros de auditoría y tombstones con comentarios preservados.
- Paginación con cursor, fechas iguales, entradas nuevas entre páginas y límites.
- Respuesta 401 cuando la sesión ya no existe; protección ante resultados
  retrasados de otra sesión/selección; error de conexión rechazada mostrado como
  fallo capturable fuera del hilo JavaFX.
- Cliente compilado con Java 21; las comprobaciones automáticas JavaFX pasaron.

La aceptación visual de comentarios, formularios de reporte y cola del
moderador debe hacerse en la ventana local siguiendo los pasos anteriores. Las
pruebas HTTP no sustituyen esa revisión visual.
