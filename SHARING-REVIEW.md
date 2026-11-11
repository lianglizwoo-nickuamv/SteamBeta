# Revisión previa al primer commit

Preparación del árbol de trabajo y del índice; no se ha creado commit ni remoto
ni se ha publicado el repositorio mediante este trabajo.

## Incluido

Código JavaFX y backend, recursos, dos POM, V1/V2, pruebas, scripts sin secretos,
plantilla de variables, documentación y Maven Wrapper (incluido su JAR).
El listado exacto está en [SHARE-MANIFEST.md](SHARE-MANIFEST.md).

El índice inicial contenía tres archivos .idea y versiones antiguas de algunos
fuentes/POM. Se retiraron los archivos IDE del índice conservándolos localmente
y se prepararon las versiones actuales del proyecto. No se usó reset ni se
borraron configuraciones personales.

## Excluido

.idea, .run, .vscode, archivos de entorno reales, contraseñas/cifrados DPAPI,
keystores/claves, logs, .replica-work, JDKs, clústeres y volcados de bases,
target/build/out y el historial detallado REPLICA-STATE.local.md.
Los secretos de BD/demo residen fuera del repositorio y no son requisitos del clon.

Se revisaron tanto los archivos candidatos como los blobs ya staged, incluyendo
los iniciales. Se buscaron patrones de claves privadas, tokens de proveedor,
JWT, verificadores de contraseñas, URLs con credenciales, literales sospechosos y
rutas personales. Además se compararon con las contraseñas locales DPAPI en memoria,
sin imprimirlas. No se detectaron secretos en los archivos destinados a compartir.
Esta revisión reduce el riesgo, pero no sustituye la revisión humana del diff.

## Pendientes antes de publicar

- Aceptación visual JavaFX con dos cuentas y respuesta tardía: sigue pendiente.
  Las pruebas API/automáticas no equivalen a esa aceptación.
- Elegir propietario/nombre/visibilidad del repositorio y, si se desea permitir
  reutilización, la licencia del proyecto. No se añadió una licencia propia sin
  decisión del responsable; los avisos del Maven Wrapper se conservan.
- Un colaborador debe aprovisionar su base, roles y credenciales según README.
  No se distribuyen la base local, las contraseñas demo ni configuraciones IDE.
- Revisar el índice final antes de autorizar el primer commit. El permiso para
  preparar archivos no se ha tratado como autorización para commit/push.

La copia exportada del índice aprobó 2 pruebas cliente, 3 unitarias backend y
10 IT en PostgreSQL desechable. No dependió de configuraciones personales ni del
JDK descargado dentro del proyecto: JAVA_HOME apuntó a un JDK externo a esa copia.
V1 conservó su SHA-256 original. Pasaron 15 sondas de exclusión; no queda ningún
archivo ignorado en el índice. Detalle en backend/REPLICA-STATE.md.
