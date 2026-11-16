# SteamBeta · Etapa de comunidad

La entrega posterior ya implementa registro JavaFX, comunidades, membresías y
publicaciones mediante V4. Su estado y verificación están en
[PILOT-QUICKSTART.md](PILOT-QUICKSTART.md). El diagnóstico inicial y las propuestas
de comentarios/moderación se conservan debajo como contexto de diseño.

## Estado observado el 16 de noviembre de 2026

La revisión encontró un cliente JavaFX y una API Spring Boot con acceso/salida,
biblioteca, actividades y progreso. La identidad del login dependía de un
estudiante. V1/V2 existen en la base local con tres cuentas y tres estudiantes.
Los informes anteriores registraban 15 pruebas aprobadas. Las capturas del usuario
mostraron la vista de acceso, pero no establecen la aceptación visual de biblioteca.

No se encontraron registro, comunidades, publicaciones, comentarios o moderación.
Los méritos, puntos y canjes tienen contratos SQL; sus pantallas y recorrido
académico real siguen pendientes. La integración universitaria y los retos
autoevaluados fueron propuestas y no son implementaciones.

Se conservan Java 21, Maven Wrapper, JavaFX/HTTP, Spring Security, sesiones, CSRF,
PasswordEncoder, Flyway, PostgreSQL y pruebas con instancias desechables.
La biblioteca y el resto del modelo académico se posponen manteniendo sus tablas,
funciones y rutas. No son un requisito para acceder a la futura comunidad.

## Entrega 1 · Identidad independiente

La rama codex/community-foundation agrega V3, sin reescribir V1/V2. Permite cuentas
sin vínculo académico y conserva la FK y los vínculos de las cuentas existentes.
Cuenta y perfil se crean en una misma transacción. El perfil contiene alias,
universidad declarada y carrera declarada. Esas declaraciones no verifican una
identidad institucional. No se requiere email para este piloto.

El principal conserva accountId y usa ROLE_USER. Los roles de comunidad serán
membresías por comunidad; ROLE_USER no concede moderación global.

API disponible en esta entrega:

| Operación | Contrato |
| --- | --- |
| GET /api/auth/csrf | Sesión y token CSRF |
| POST /api/auth/register | JSON con login, password, alias y dos campos declarados opcionales; 201 y accountId |
| POST /api/auth/login | Formulario login/password; conserva contrato JavaFX anterior |
| GET /api/account | Perfil propio con accountId, alias, declaredUniversity, declaredCareer e institutionalIdentityVerified=false |
| POST /api/auth/logout | Invalida la sesión; exige CSRF vigente |

Registro devuelve 400 para datos inválidos y 409 para un identificador ocupado.
Login se normaliza a minúsculas. Alias tiene 2–60 caracteres y las declaraciones
opcionales hasta 120. La contraseña tiene un mínimo de 12 caracteres y un máximo
de 72 bytes UTF-8 por compatibilidad con bcrypt. Todos los valores se parametrizan.
El registro requiere CSRF y no inicia sesión automáticamente.

El login y /api/me conservan los tres campos de identidad del cliente anterior.
Para una cuenta independiente studentId es null. /api/account es el nuevo contrato
de identidad que usará la comunidad. Las rutas académicas devuelven 403 sin vínculo.
La pantalla de registro y la nueva portada JavaFX quedan para la próxima entrega.

V3 también crea perfiles al añadir cuentas desde el cargador demo existente.
Las credenciales y el perfil no se exponen por SELECT directo al runtime. Las
funciones estrechas reciben los parámetros del servidor; el ID del perfil propio
se deriva exclusivamente del principal autenticado.

## Modelo mínimo propuesto para las entregas siguientes

Solo cuenta y perfil tienen cambios SQL en esta entrega. El resto está propuesto.

| Entidad | Relación y restricción principal |
| --- | --- |
| Cuenta | Identidad de acceso; vínculo académico opcional |
| Perfil | Uno por cuenta; alias público; declaraciones opcionales |
| Comunidad | Creada por el equipo; nombre, slug único, descripción y reglas |
| Membresía | Clave única comunidad/cuenta; MEMBER, MODERATOR o ADMIN |
| Publicación | Comunidad y autor; título, cuerpo, fechas y estado |
| Comentario | Publicación y autor; un nivel, fechas y estado |
| Etiqueta | Pertenece a una comunidad; nombre normalizado único dentro de ella |
| Publicación/etiqueta | Relación compuesta que rechaza etiquetas de otra comunidad |
| Reporte | Autor, motivo y exactamente un objetivo; publicación o comentario |
| Acción de moderación | Actor, objetivo, motivo y fecha; historial separado del reporte |

El registro será libre. La admisión al piloto será por invitación del equipo,
mediante alta de membresías administrada inicialmente por el equipo. Solo miembros
activos consultan contenido. Todavía no se implementan tokens de invitación,
creación libre de espacios o administración global desde la aplicación.

No se usa university_id para aislar conversaciones. La pertenencia a la comunidad
controla el acceso, con independencia de la universidad declarada o matrícula.

## Matriz de permisos propuesta

Todas las operaciones de contenido requieren membresía activa en la comunidad.
Los permisos se comprueban en el servidor y también en accesos por ID directo.

| Operación | Anónimo | Cuenta sin membresía | Miembro | Moderador | Administrador comunitario |
| --- | --- | --- | --- | --- | --- |
| Registro/acceso | Sí | Sí | Sí | Sí | Sí |
| Perfil propio | No | Sí | Sí | Sí | Sí |
| Leer conversación | No | No | Sí | Sí | Sí |
| Publicar/comentar/reportar | No | No | Sí | Sí | Sí |
| Editar/eliminar contenido propio visible | No | No | Sí | Sí | Sí |
| Editar texto ajeno | No | No | No | No | No |
| Ocultar contenido con motivo | No | No | No | Su comunidad | Su comunidad |
| Gestionar miembros y moderadores | No | No | No | No | Su comunidad |

No se crean roles globales adicionales sin una operación que los necesite.
El equipo aprovisionará espacios y el primer administrador mediante una operación
administrativa. El registro nunca podrá conceder esos permisos.

## Coherencia de las conversaciones

Se propone borrado lógico. Una publicación eliminada u oculta retira también su
conversación de las consultas de miembros, conservando comentarios para auditoría.
Un comentario eliminado presenta un marcador sin su texto. Las consultas,
búsqueda y detalle aplican los mismos estados. Editar no levanta una ocultación.
Se conservarán versiones de concurrencia para evitar sobrescribir ediciones.
El registro de moderación preserva el motivo y actor, sin permitir su edición
desde los endpoints de contenido.

## Secuencia de construcción

1. Cuenta independiente y registro API, entregados en esta rama.
2. Una comunidad piloto, membresías, portada JavaFX y registro desde JavaFX.
3. Publicaciones propias, paginación cronológica, etiquetas y búsqueda sencilla.
4. Comentarios, borrado coherente, reportes y moderación por comunidad.

El recorrido completo solicitado queda pendiente hasta completar los cuatro lotes.
Autoría ajena, permisos de moderación y conversaciones borradas se prueban al
añadir sus operaciones. No se presentan como verificados en la entrega 1.

## Ejecución y verificación

Desde la raíz con JDK 21:

```powershell
.\mvnw.cmd test
.\backend\scripts\verify-postgres.ps1
```

El segundo comando crea PostgreSQL desechable y ejecuta verify del backend.
La prueba de actualización empieza en V2 con una cuenta existente y aplica V3.
Verifica preservación de datos, registro sin filas académicas, CSRF, privacidad,
login/salida, conflicto concurrente de identificador y rollback cuenta/perfil.

V3 no se aplica automáticamente a steam_academico durante este trabajo. Para probar
localmente después de revisar los cambios, detener el backend anterior y ejecutar:

```powershell
.\backend\scripts\start-local.ps1 -Mode Migration
.\backend\scripts\start-local.ps1 -Mode Normal
```

En una segunda terminal se puede registrar una cuenta con PowerShell sin imprimir
la contraseña, utilizando una sesión HTTP y el token CSRF:

```powershell
$communitySession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$communityCsrf = Invoke-RestMethod http://localhost:8080/api/auth/csrf -WebSession $communitySession
$communityHeaders = @{}
$communityHeaders[$communityCsrf.headerName] = $communityCsrf.token
$communityPassword = Read-Host 'Contraseña nueva (mínimo 12 caracteres)' -AsSecureString
try {
    $communityBody = @{
        login = 'piloto.miembro'
        password = [Net.NetworkCredential]::new('', $communityPassword).Password
        alias = 'Miembro piloto'
        declaredUniversity = 'Universidad declarada'
        declaredCareer = 'Ingeniería en Sistemas'
    } | ConvertTo-Json
    Invoke-RestMethod http://localhost:8080/api/auth/register -Method Post -WebSession $communitySession -Headers $communityHeaders -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($communityBody))
    $communityLogin = @{
        login = 'piloto.miembro'
        password = [Net.NetworkCredential]::new('', $communityPassword).Password
    }
    Invoke-RestMethod http://localhost:8080/api/auth/login -Method Post -WebSession $communitySession -Headers $communityHeaders -Body $communityLogin
    $communityCsrf = Invoke-RestMethod http://localhost:8080/api/auth/csrf -WebSession $communitySession
    $communityHeaders[$communityCsrf.headerName] = $communityCsrf.token
    Invoke-RestMethod http://localhost:8080/api/account -WebSession $communitySession
    Invoke-RestMethod http://localhost:8080/api/auth/logout -Method Post -WebSession $communitySession -Headers $communityHeaders
} finally {
    $communityBody = $null
    $communityLogin = $null
    $communityPassword.Dispose()
}
```

El ejemplo registra piloto.miembro, inicia sesión, renueva el token CSRF, consulta
su perfil y sale. Para repetir el registro se debe elegir otro identificador o
acceder con la cuenta ya creada. Las pruebas HTTP automatizadas ejecutan ese
recorrido completo sin modificar la base local.
