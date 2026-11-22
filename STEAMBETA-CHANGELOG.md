# Registro Detallado de Cambios (Changelog) — SteamBeta Campus

**Versión:** 2.1.0-CAMPUS-CONSOLIDATION  
**Rama:** `codex/community-foundation`  
**Base de Datos Oficial:** `steam_academico` (PostgreSQL 18)  
**Fecha:** Noviembre 2026  

---

## 1. Resumen de la Consolidación Arquitectónica

En esta iteración se ha consolidado formalmente la arquitectura de SteamBeta Campus respondiendo a la directiva fundamental de **preservar PostgreSQL y la base de datos `steam_academico`**:
1. **Flujo de Persistencia Obligatorio:** JavaFX (Cliente Desktop) → API REST Spring Boot → PostgreSQL `steam_academico`.
2. **Desacoplamiento JDBC:** Se mantiene que el cliente no accede por JDBC directo ni almacena contraseñas de PostgreSQL, pero el backend Spring Boot continúa utilizando PostgreSQL para el 100% de la persistencia de usuarios, perfiles, comunidades y auditoría.
3. **Preservación Total de Datos Existentes:** Se conservan intactas las cuentas de prueba (`equipo.ana`, `equipo.luis`, `equipo.mod`), el esquema `replica` y las migraciones V1 a V11.

---

## 2. Registro de Nuevos Componentes y Modificaciones

### 2.1 Base de Datos (PostgreSQL 18 / `steam_academico`)

| Componente | Estado | Descripción del Cambio |
| :--- | :--- | :--- |
| `V12__email_verification_and_extended_profile.sql` | `[IMPLEMENTADO]` | Migración incremental: añade `email` único y `email_verified` en `replica.access_account`; `declared_semester` y `email_public` en `replica.account_profile`; tabla inmutable de tokens `replica.email_verification_token`; función `replica.register_account_v2` (alias, login, email, hash); y actualización de `replica.lookup_access_account` para autenticar por login o correo. |
| `V13__student_registration_and_email_authentication.sql` | `[IMPLEMENTADO]` | **Unificación Definitiva de Registro:** Migración incremental que actualiza la firma oficial de `replica.register_account(alias, login, email, password_hash)` al nuevo contrato unificado de 4 argumentos; añade índice único normalizado sobre `lower(btrim(email))` en `replica.access_account`; y actualiza `replica.lookup_access_account` para permitir acceso mediante identificador o correo electrónico verificado. |

### 2.2 Backend Central (Spring Boot 4.1.2)

| Clase | Estado | Descripción del Cambio |
| :--- | :--- | :--- |
| `EmailVerificationService.java` | `[IMPLEMENTADO]` | Servicio de verificación de correo mediante tokens `SecureRandom` con hash SHA-256 (sin texto plano) y expiración a 24 horas. Detección automática de variables `STEAMBETA_MAIL_*`; si no están configuradas, documenta la espera sin fingir envíos falsos. |
| `AccountLookup.java` | `[IMPLEMENTADO]` | Soporte de autenticación dual (por identificador único o por correo electrónico registrado), con consultas defensivas y fallback a esquemas previos. |
| `AccountService.java` | `[IMPLEMENTADO]` | Nuevo modelo de registro (alias, login, correo, contraseña, confirmación) con normalización estricta; diferenciación de errores 409 (`login_unavailable` vs `email_unavailable`); ampliación de `FullProfile` y `ProfileUpdate` con semestre, privacidad de correo y conteo de comunidades/publicaciones. |
| `AccountController.java` | `[IMPLEMENTADO]` | Nuevos endpoints: `/api/auth/register`, `/api/auth/verify-email?token=...`, `/api/auth/email-status`. |
| `SecurityConfiguration.java` | `[IMPLEMENTADO]` | Actualización de `requestMatchers` para permitir acceso público a las rutas de verificación de correo y consulta de estado SMTP. |

### 2.3 Cliente de Escritorio (JavaFX 21)

| Clase / Componente | Estado | Descripción del Cambio |
| :--- | :--- | :--- |
| `ApiClient.java` | `[IMPLEMENTADO]` | Actualización de `Profile` con campos extendidos; método `register(alias, login, email, password, confirmPassword)` y sobrecargas de compatibilidad; método `updateProfile` con semestre y privacidad de correo. |
| `AuthComponent.java` | `[IMPLEMENTADO]` | **Rediseño completo de Login:** soporte para login o correo, toggle ver/ocultar contraseña, spinner de carga, prevención de doble envío y mensajes de error estrictamente diferenciados (401, 403, 500, conexión).<br/>**Rediseño completo de Registro:** solicita exactamente los 5 datos principales (alias, login, correo, contraseña, confirmación), sin pedir universidad/carrera/semestre; validadores visuales reactivos en vivo. |
| `ProfileComponent.java` | `[IMPLEMENTADO]` | **Rediseño de Perfil:** cabecera con avatar e iniciales, etiqueta `@login`, tarjeta de correo con indicador de privacidad (`🔒 Privado`), formación académica declarada con disclaimer explícito, semestre, biografía, intereses y diálogo de edición. |

### 2.4 Documentación de Arquitectura y Guías

| Documento | Estado | Descripción del Cambio |
| :--- | :--- | :--- |
| `STEAMBETA-ARCHITECTURE.md` | `[IMPLEMENTADO]` | Enfatiza la preservación de PostgreSQL y `steam_academico`, flujo de persistencia, entornos sin dominios ficticios y arquitectura de verificación de correo. |
| `STEAMBETA-UI-GUIDE.md` | `[IMPLEMENTADO]` | Especificación detallada de interfaces para Login, Registro y Perfil. |
| `STEAMBETA-CHANGELOG.md` | `[IMPLEMENTADO]` | Registro cronológico actualizado con clasificación transparente. |

---

## 3. Matriz de Estados de Entrega

* **`[IMPLEMENTADO]`**: Todos los archivos de código Java, esquemas SQL y documentación están completamente escritos y estructurados en el repositorio.
* **`[PENDIENTE DE PRUEBAS]`**: Las pruebas unitarias, manuales y de integración quedan a cargo del propietario del proyecto de acuerdo con la directiva expresa de no ejecutar herramientas que consuman recursos en esta sesión.
* **`[PENDIENTE DE DESPLIEGUE]`**: Aplicación manual de la migración V12 en la base de datos `steam_academico` por parte del propietario y configuración de variables SMTP institucionales.
