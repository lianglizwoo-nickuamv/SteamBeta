# Preparación del piloto — registro de resultados

Fecha: 2026-11-16. Rama: codex/community-foundation.
La revisión visual se realiza con el usuario, un paso por turno. No se declara
aceptada hasta registrar resultados reales de la ventana.

## 1. Recorridos comprobados

- Histórico aportado por el usuario: iniciar como miembro, entrar en comunidad,
  publicar, cerrar sesión, leer desde otro miembro y ver estado vacío sin grupo.
- Esta preparación: backend local arrancó en 8080; dos cuentas QA nuevas
  completaron registro 201, login 200, perfil 200, biblioteca 403 y logout 204,
  seguido de perfil 401. Esto fue por API, no por JavaFX.
- SQL confirmó dos comunidades QA: A con dos miembros y una moderadora; B con
  dos miembros y sin moderador. A contiene 12 publicaciones, B contiene una.
  La nueva cuenta sin grupo tiene cero membresías.

## 2. Casos visuales pendientes

| Caso | Resultado real |
| --- | --- |
| Abrir JavaFX actualizado | Esperando confirmación del usuario |
| A: diez publicaciones y dos al pulsar Más antiguas | Pendiente |
| Comentario, edición y eliminación propia | Pendiente |
| Reportar y ocultar con moderador A | Pendiente |
| B: misma persona solo es miembro, sin moderación | Pendiente |
| Cuenta QA sin grupo: estado vacío sin datos anteriores | Pendiente |
| Conexión rechazada y recuperación | Pendiente |
| Sesión vencida tras reiniciar servidor | Pendiente |
| Respuesta retrasada no reemplaza nueva sesión | Pendiente visual; guard unitario previo aprobado |

## 3. Arrancar y detener

En PowerShell, raíz del repositorio: `backend/scripts/start-local.ps1 -Mode Normal`
mantiene el backend ejecutándose; Ctrl+C lo detiene. Una sola instancia por puerto.
En otra terminal: `backend/scripts/start-client.ps1` abre JavaFX y permanece
ocupada. Cerrar sesión y cerrar la ventana termina el cliente.
Esta preparación inició el backend desde el agente; al reiniciar el equipo,
arráncalo desde tu propia terminal para disponer de Ctrl+C.

## 4. Cuentas, espacios y administración

Los identificadores numéricos de cuentas anteriores se mantienen fuera de este
documento compartido. Usa las cuentas locales Ana y Luis como miembros y la
cuenta anterior sin grupo como moderadora en A, miembro normal en B.
Las contraseñas anteriores se copian con `pilot-credentials.ps1`.

| Cuenta QA nueva | Membresía | Copiar contraseña local |
| --- | --- | --- |
| qa.pilot.outsider | Ninguna | `qa-credentials.ps1 -CopyPasswordFor qa.pilot.outsider` |
| qa.pilot.second.member | Solo B | `qa-credentials.ps1 -CopyPasswordFor qa.pilot.second.member` |

Los scripts están en backend/scripts y se ejecutan desde la raíz con `.\backend\scripts\`.
Las credenciales DPAPI solo sirven al usuario Windows que las generó.
No publicarlas ni copiarlas a los compañeros. Cada compañero crea su cuenta.

`add-pilot-members.ps1 -Login @('miembro1','miembro2') -ModeratorLogin @('moderador')`
es un procedimiento del administrador para sistemas-piloto. No administra QA A/B.
Para QA A/B, `pilot-qa-fixtures.sql` es un fixture explícito, no una migración:
solo lo ejecuta el migrador, con las cuentas de prueba existentes. Es aditivo y
repetible, sin modificar contenido previo. Antes de usarlo en otra instalación,
adapta los identificadores de prueba a sus cuentas; no lo ejecutes en producción.
Los títulos y nombres usan [QA PILOTO]; no se insertaron datos en el espacio
original ni se borraron publicaciones del usuario.

## 5. Limitaciones y problemas por resolver

- Revisión visual aún en curso. Las pruebas HTTP no acreditan ventanas ni mensajes.
- Comentarios y cola de reportes se cargan completos; falta comprobar comportamiento
  con volumen. Sesiones en memoria: reiniciar servidor obliga a entrar de nuevo.
- No hay recuperación autónoma de contraseña ni interfaz de administración de roles.
- El proxy manual se amplió para retener también una lectura de publicaciones;
  su ensayo visual guiado queda pendiente. No imprime cabeceras ni cuerpos.
- Se corrigió el despachador de callbacks: antes de aplicar un resultado o error
  comprueba que siga siendo la generación de sesión/pantalla que inició la tarea.
  La comprobación visual de respuestas tardías permanece pendiente.
- El backend solo escucha loopback. Otra computadora no puede alcanzarlo todavía.
- Pendiente validar checksums Flyway locales: V5 recibió un ajuste de contador
  después de aplicarse en la sesión anterior. No usar repair ni reescribir V5;
  resolver la discrepancia mediante una migración posterior revisada si se confirma.

No se modificaron migraciones, firewall, HTTPS ni acceso externo en esta preparación.
