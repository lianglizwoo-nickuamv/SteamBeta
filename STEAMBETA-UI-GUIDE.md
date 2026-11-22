# Guía de Diseño Visual y Experiencia de Usuario (UI/UX) — SteamBeta Campus

**Versión:** 2.1.0  
**Estado:** *Implementado & Diseñado* (Rediseño integral de Login, Registro y Perfil Estudiantil)  
**Fecha:** Noviembre 2026  

---

## 1. Lenguaje Visual y Paleta Cromática

SteamBeta Campus utiliza una interfaz oscura moderna inspirada en herramientas de ingeniería y colaboración académica de vanguardia:

* **Dark Navy (`#0a0f1d`):** Fondo general de la aplicación (`CampusShell`), pantalla de acceso y contenedores principales.
* **Slate Deep Card (`#0f172a`):** Fondo de tarjetas de contenido, formularios y modales.
* **Surface Border / Hover (`#1e293b`):** Bordes sutiles, divisores de campos y estados de interacción.
* **Electric Blue (`#0284c7`):** Acciones primarias, botones de acceso, enlaces destacados y llamadas a la acción.
* **Cyan (`#38bdf8`):** Identificadores de usuario (`@login`), halos de foco accesible, badges de especialidad y acentos interactivos.
* **Academic Success (`#34d399` / `#10b981`):** Indicadores positivos de requisitos cumplidos en tiempo real.
* **Error & Alert (`#f87171` / `#ef4444`):** Mensajes de error contextuales y advertencias disciplinarias.

---

## 2. Rediseño del Inicio de Sesión (`AuthComponent.showLogin`)

La interfaz de inicio de sesión ha sido optimizada para ofrecer claridad inmediata, robustez ante fallos y navegación sin fricción:

* **Campo Bivalente:** Permite ingresar indistintamente el identificador de acceso único (`login`) o el correo electrónico registrado.
* **Alternancia de Contraseña:** Botón interactivo "Ver" / "Ocultar" para verificar la contraseña antes de enviar.
* **Protección Anti-Reintentos y Estado de Carga:** Al pulsar "Iniciar sesión", los campos se deshabilitan temporalmente y se activa un indicador circular (`ProgressIndicator`), previniendo envíos dobles.
* **Manejo Estricto de Errores Diferenciados:**
  * **HTTP 401:** *"Identificador, correo o contraseña incorrectos."*
  * **HTTP 403:** *"Acceso denegado: tu cuenta se encuentra suspendida o inactiva."*
  * **HTTP 500:** *"Error interno en el servidor central. Por favor intenta más tarde."*
  * **Fallo de Red / Conexión:** *"No se pudo conectar con el servidor backend. Verifica tu conexión de red o que el servidor esté activo."*
* **Acceso Directo al Registro:** Botón destacado para estudiantes que aún no poseen cuenta.

---

## 3. Rediseño del Formulario de Registro (`AuthComponent.showRegister`)

El formulario de registro universitario se enfoca exclusivamente en los datos fundamentales, evitando barreras iniciales innecesarias:

### 3.1 Los 5 Campos Principales Requeridos
1. **Nombre o Alias Público:** Campo de texto con validación de 2 a 60 caracteres.
2. **Identificador de Acceso Único:** Solo caracteres alfanuméricos, punto, guion o `@` (3 a 80 caracteres).
3. **Correo Electrónico Obligatorio:** Validación sintáctica reactiva en tiempo real.
4. **Contraseña Segura:** Campo con botón para mostrar u ocultar texto.
5. **Confirmar Contraseña:** Campo sincronizado para garantizar exactitud.

### 3.2 Indicadores Visuales de Requisitos en Tiempo Real
Debajo de los campos de contraseña se despliega una lista dinámica que cambia de color y texto a medida que el estudiante teclea:
* `✓ Mínimo 12 caracteres cumplido (N)` / `• Mínimo 12 caracteres (llevas N)`
* `✓ Las contraseñas coinciden` / `✗ Las contraseñas no coinciden`
* `✓ Longitud máxima en bytes correcta` / `✗ Excede el límite de 72 bytes`
* `✓ Correo electrónico con formato válido` / `• Correo con formato válido (@)`

---

## 4. Rediseño del Perfil Estudiantil (`ProfileComponent`)

El perfil universitario permite al alumno gestionar su identidad y trayectoria académica de manera autónoma:

```
+----------------------------------------------------------------------------------------------------+
|  [Avatar Circle (Iniciales)]   Sofía Martínez   @sofia.m   [ROL: USER]                             |
|                                [ Botón: Editar información de mi perfil ]                          |
+----------------------------------------------------------------------------------------------------+
|  IDENTIDAD DE ACCESO Y CONTACTO                                                                    |
|  Correo electrónico: sofia@universidad.edu  [🔒 Correo privado (Solo tú lo ves)]                   |
|  (Tu correo se mantiene confidencial. Puedes cambiar su visibilidad al editar tu perfil).         |
+----------------------------------------------------------------------------------------------------+
|  TRAYECTORIA UNIVERSITARIA DECLARADA                                                               |
|  Universidad: Universidad Central de Tecnología                                                    |
|  Carrera:     Ingeniería de Software                                                               |
|  Semestre:    Semestre 6                                                                           |
|  * Información académica declarada por el estudiante · Sin verificación institucional.             |
+----------------------------------------------------------------------------------------------------+
|  BIOGRAFÍA ACADÉMICA                                                                               |
|  Estudiante apasionada por la arquitectura de sistemas distribuidos y bases de datos relacionales.|
+----------------------------------------------------------------------------------------------------+
|  ÁREAS DE INTERÉS Y ESPECIALIDADES                                                                 |
|  [Java 21]  [PostgreSQL]  [Spring Boot]  [Estructuras de Datos]  [Sistemas Operativos]             |
+----------------------------------------------------------------------------------------------------+
|  PARTICIPACIÓN EN EL CAMPUS                                                                        |
|  [ 4 ] Comunidades inscritas                       [ 18 ] Publicaciones realizadas                 |
+----------------------------------------------------------------------------------------------------+
```

### 4.1 Diálogo de Edición de Perfil
Permite modificar los datos autorizados:
* Alias público.
* Biografía académica.
* Enlace de avatar (URL).
* Universidad declarada y Carrera.
* Selector de Semestre (Spinner numérico entre 1 y 20).
* Intereses académicos (etiquetas separadas por comas).
* Checkbox de Privacidad: *"Permitir que otros estudiantes vean mi correo en mi perfil"*.
* **Seguridad:** El estudiante **no** puede alterar su identificador único de acceso ni su rol global (`USER`, `GLOBAL_MODERATOR`).
