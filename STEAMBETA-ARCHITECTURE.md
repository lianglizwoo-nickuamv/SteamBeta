# Arquitectura Técnica y Persistencia — SteamBeta Campus

**Versión del Documento:** 2.1.0  
**Estado:** *Implementado & Diseñado* (Persistencia oficial en PostgreSQL `steam_academico`, desacoplamiento JDBC del cliente y preparación multientorno)  
**Fecha:** Noviembre 2026  
**Autores:** Equipo de Arquitectura de Software e Infraestructura SteamBeta Campus  

---

## 1. Principio Fundamental: Preservación de PostgreSQL y `steam_academico`

### 1.1 Aclaración Arquitectónica Indispensable
**La base de datos `steam_academico` (PostgreSQL 18) es y sigue siendo la base de datos oficial, central e indispensable de SteamBeta.**

El desacoplamiento del cliente **NUNCA** significa eliminar, sustituir o desconectar PostgreSQL:
* **El Servidor Backend Spring Boot:** Depende obligatoriamente de PostgreSQL mediante JDBC y el pool de conexiones HikariCP. Administra el esquema relacional (`public` y `replica`), ejecuta las consultas SQL transaccionales y almacena todas las cuentas, contraseñas con hash BCrypt, perfiles, comunidades, tareas y bitácoras de auditoría.
* **El Cliente de Escritorio JavaFX:** No realiza conexiones directas JDBC a la base de datos ni almacena credenciales de PostgreSQL. Consume de forma limpia y segura la API REST del backend mediante HTTPS, utilizando cookies de sesión protegidas (`HttpOnly`, `SameSite=Lax`, `Secure`) y tokens CSRF.
* **Cuentas y Datos Existentes:** Se preservan intactas las cuentas de prueba institucionales (`equipo.ana`, `equipo.luis`, `equipo.mod`) y todas las migraciones anteriores (V1 a V11). No se utiliza almacenamiento volátil en memoria, ni SQLite, ni archivos JSON como sustitutos de base de datos.

```
+---------------------------------------------------------------------------------------------------------+
|                                    FLUJO DE ARQUITECTURA REAL Y OFICIAL                                 |
|                                                                                                         |
|   [ Cliente JavaFX 21 ]                                                                                 |
|   (Desktop Estudiante)                                                                                  |
|             │                                                                                           |
|             │   HTTPS REST / JSON                                                                       |
|             │   Cookies HttpOnly + X-CSRF-TOKEN                                                         |
|             ▼                                                                                           |
|   [ Backend Central Spring Boot 4.1.2 ]                                                                 |
|   (Seguridad, Control de Sesiones, BCrypt, Servicios de Dominio)                                        |
|             │                                                                                           |
|             │   HikariCP / JDBC con Pool Concurrente                                                    |
|             │   Esquemas: public & replica                                                              |
|             ▼                                                                                           |
|   [ PostgreSQL 18: steam_academico ] ─── (Base de datos oficial indispensable)                          |
+---------------------------------------------------------------------------------------------------------+
```

---

## 2. Gestión de Entornos y Despliegue sin Dominios Ficticios

El sistema opera con configuraciones separadas y transparentes para cada fase del ciclo de vida del software:

| Característica / Parámetro | Entorno LOCAL (Desarrollo Activo) | Entorno STAGING (Pruebas Piloto) | Entorno PRODUCCIÓN (Servidor Permanente) |
| :--- | :--- | :--- | :--- |
| **Objetivo** | Desarrollo y pruebas manuales del propietario | Validación de aceptación previa | Servicio ininterrumpido del campus |
| **Perfil Spring** | `local` / `default` | `staging` | `production` |
| **Host Backend** | `http://localhost:8080` | URL asignada al servidor de staging | URL pública segura del servidor central |
| **Instancia PostgreSQL** | **`localhost:5432/steam_academico`** | Instancia remota de pruebas | Instancia central alojada permanente |
| **Traslado a Producción** | Base de partida con datos de desarrollo | Migraciones Flyway V1 a V12 | Copia y migración controlada de esquema |
| **Experiencia de Usuario** | Requiere backend local para desarrollo | Cliente apunta a URL de staging | **El estudiante solo abre SteamBeta Desktop** |

> [!IMPORTANT]
> No se asume que el servidor central de producción ya existe ni se configuran dominios ficticios. En el entorno de desarrollo actual, el backend continúa ejecutándose localmente conectado a la base `steam_academico`.

---

## 3. Modelo de Autenticación, Registro y Verificación de Correo

### 3.1 Nuevo Formulario de Registro Universitario
El registro permite que cualquier estudiante cree su cuenta de manera autónoma sin intervención administrativa previa, solicitando **exactamente cinco datos principales**:

1. **Nombre o Alias Público (`alias`):** Nombre visible para otros compañeros en comunidades y foros (2 a 60 caracteres). Totalmente separado del identificador de acceso.
2. **Identificador de Acceso Único (`login`):** Identificador utilizado para iniciar sesión y ser localizado (3 a 80 caracteres en minúsculas, caracteres `[a-z0-9._@-]`).
3. **Correo Electrónico Obligatorio (`email`):** Correo electrónico institucional o personal, normalizado a minúsculas y validado con expresión regular. Se mantiene privado de forma predeterminada.
4. **Contraseña (`password`):** Mínimo 12 caracteres y máximo 72 bytes UTF-8, protegida con hashing unidireccional BCrypt.
5. **Confirmación de Contraseña (`confirmPassword`):** Verificación de coincidencia en cliente y servidor.

*Nota de Diseño:* Durante el registro **no** se solicita universidad, carrera ni semestre. Estos datos se completan posteriormente desde el perfil del estudiante.

### 3.2 Inicio de Sesión Bivalente (Login o Correo)
El endpoint de autenticación y la función de base de datos `replica.lookup_access_account` han sido actualizados para permitir que el estudiante inicie sesión indistintamente con:
- Su **identificador de acceso único** (ej. `equipo.ana` o `sofia.m`).
- Su **correo electrónico registrado y verificado** (ej. `ana@campus.steambeta.edu`).

### 3.3 Arquitectura del Sistema de Verificación por Correo
- **Generación Criptográficamente Segura:** Emplea `java.security.SecureRandom` para generar tokens de 32 bytes en formato hexadecimal.
- **Almacenamiento Protegido (Sin Texto Plano):** El backend calcula el hash SHA-256 del token antes de insertarlo en la tabla `replica.email_verification_token` junto con su fecha de expiración (24 horas). En la base de datos nunca se guarda el token en texto plano.
- **Configuración Externa Vía Variables de Entorno:**
  - `STEAMBETA_MAIL_ENABLED` (`true` / `false`)
  - `STEAMBETA_MAIL_HOST` (Servidor SMTP del campus)
  - `STEAMBETA_MAIL_PORT` (Puerto SMTP, ej. 587 o 465)
  - `STEAMBETA_MAIL_USERNAME` / `STEAMBETA_MAIL_PASSWORD`
  - `STEAMBETA_MAIL_FROM`
- **Comportamiento sin Proveedor Configurado:** Si las variables `STEAMBETA_MAIL_*` no están definidas, el sistema **no** simula envíos falsos ni engaña al usuario. La cuenta queda creada y activa en PostgreSQL `steam_academico` para acceso por identificador, y la verificación por correo queda en espera formal de aprovisionamiento SMTP.

---

## 4. Estructura de la Base de Datos (`steam_academico`) y Migración V12

El archivo `V12__email_verification_and_extended_profile.sql` introduce las siguientes mejoras sobre el esquema:
1. `replica.access_account`: Añade columnas `email text UNIQUE` y `email_verified boolean NOT NULL DEFAULT false`, con backfill automático para las cuentas existentes (`equipo.ana`, `equipo.luis`, `equipo.mod`).
2. `replica.account_profile`: Añade `declared_semester integer` (1 a 20) y `email_public boolean NOT NULL DEFAULT false`.
3. `replica.email_verification_token`: Tabla protegida con Row Level Security para control de tokens de verificación con expiración.
4. `replica.register_account_v2`: Función PL/pgSQL transaccional para registro autónomo de estudiantes.
5. `replica.update_account_profile_v2`: Función de actualización del perfil incluyendo semestre y privacidad de correo.
