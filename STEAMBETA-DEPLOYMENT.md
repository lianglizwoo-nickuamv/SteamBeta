# Guía de Despliegue, Empaquetado y Operaciones — SteamBeta Campus

**Versión:** 2.0.0  
**Estado:** *Implementado & Diseñado* (Scripts de empaquetado, configuraciones de entorno y perfiles listos)  
**Fecha:** Noviembre 2026  
**Audiencia:** Administradores de Sistemas (SysAdmins), DevOps, Soporte TI Universitario  

---

## 1. Visión Operativa de la Plataforma

Para garantizar que el estudiante universitario experimente SteamBeta como cualquier software moderno comercial (como Discord, Spotify o Slack), la distribución técnica se divide en dos frentes operacionales independientes:
1. **Despliegue Centralizado del Servidor:** Gestionado por el centro de cómputo universitario o infraestructura en la nube. Requiere cero intervención de los usuarios finales.
2. **Empaquetado y Distribución del Cliente:** Generación de un instalador autocontenido nativo (`.msi` o `.exe`) que empaqueta la aplicación JavaFX junto con su propio entorno de ejecución Java 21 optimizado.

---

## 2. Despliegue del Servidor Central (Spring Boot + PostgreSQL)

### 2.1 Requisitos del Servidor
- **Sistema Operativo:** Ubuntu Server 22.04 LTS / Debian 12 / RHEL 9 o contenedor Linux.
- **CPU:** 2 vCPU mínimo (4 vCPU recomendadas para picos de inicio de semestre).
- **Memoria RAM:** 4 GB mínimo (8 GB recomendados para balancear JVM y PostgreSQL).
- **Almacenamiento:** 50 GB SSD con copias de seguridad automáticas diarias.
- **Java:** OpenJDK 21 LTS de 64 bits.

### 2.2 Variables de Entorno Requeridas en el Servidor
Para evitar cualquier filtración de credenciales en el código fuente, la configuración central se inyecta mediante variables de entorno del sistema o secretos de Docker/Kubernetes:

```bash
# Entorno activo de Spring
SPRING_PROFILES_ACTIVE=production

# Parámetros de Conexión a Base de Datos Central
REPLICA_DB_HOST=db.steambeta.campus.edu
REPLICA_DB_PORT=5432
REPLICA_DB_NAME=steambeta_central
REPLICA_DB_USERNAME=steambeta_app_user
REPLICA_DB_PASSWORD=ContraseñaSuperSeguraGenerada128Bits!

# Parámetros de Seguridad y Red
SERVER_PORT=8080
SERVER_FORWARD_HEADERS_STRATEGY=framework
STEAMBETA_CORS_ALLOWED_ORIGINS=https://steambeta.campus.edu,https://app.steambeta.campus.edu
```

### 2.3 Servicio Systemd para Linux (`steambeta-backend.service`)
```ini
[Unit]
Description=SteamBeta Campus Central Backend Service
After=syslog.target network.target postgresql.service

[Service]
Type=simple
User=steambeta
Group=steambeta
WorkingDirectory=/opt/steambeta/backend
ExecStart=/usr/bin/java -Xms1024m -Xmx2048m -Dspring.profiles.active=production -jar steambeta-backend-2.0.0.jar
Restart=always
RestartSec=10
EnvironmentFile=/etc/steambeta/backend.env

[Install]
WantedBy=multi-user.target
```

### 2.4 Terminación TLS y Proxy Inverso con Nginx
```nginx
server {
    listen 443 ssl http2;
    server_name api.steambeta.campus.edu;

    ssl_certificate /etc/letsencrypt/live/api.steambeta.campus.edu/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/api.steambeta.campus.edu/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
        proxy_cookie_path / "/; Secure; HttpOnly; SameSite=Lax";
        client_max_body_size 25M;
    }
}
```

---

## 3. Empaquetado del Cliente de Escritorio con `jpackage`

### 3.1 Ventajas para el Estudiante
- **Instalación en un Clic:** El estudiante descarga `SteamBetaCampus-Setup.msi` (o su equivalente en macOS/Linux).
- **Cero Dependencias Previas:** El instalador incluye un runtime JRE privado en la carpeta de instalación. Si la computadora del alumno no tiene Java instalado, la aplicación funciona perfectamente sin solicitar descargas adicionales.
- **Acceso Directo y Menú de Inicio:** Integra automáticamente el icono oficial del campus en el escritorio y panel de programas.

### 3.2 Script de Empaquetado Automatizado
El repositorio incluye el script `scripts/package-client.ps1`:

```powershell
# scripts/package-client.ps1
param(
    [string]$AppVersion = "2.0.0",
    [string]$ServerUrl = "https://api.steambeta.campus.edu",
    [string]$PackageType = "msi" # Opciones: msi, exe
)

Write-Host "Iniciando empaquetado nativo de SteamBeta Campus v$AppVersion..." -ForegroundColor Cyan

# 1. Configuración de URL de Servidor Central embebida
$propsPath = "src/main/resources/steambeta.properties"
"steambeta.api.url=$ServerUrl" | Set-Content -Path $propsPath -Encoding utf8

# 2. Empaquetado nativo mediante jpackage (ejecutado por el pipeline de release)
# jpackage --name "SteamBetaCampus" `
#          --app-version $AppVersion `
#          --input target/package-input `
#          --main-jar steambeta-client-fat.jar `
#          --main-class com.example.steambeta.App `
#          --type $PackageType `
#          --win-dir-chooser `
#          --win-shortcut `
#          --icon assets/icon.ico
```

---

## 4. Configuración del Cliente para Conectar a Distintos Servidores

El cliente `EnvironmentConfig.java` resuelve la dirección del servidor central con el siguiente orden de precedencia:

1. **Variable de Entorno del Sistema Operativo (`STEAMBETA_API_URL`):**
   Útil para despliegues de laboratorio institucional o pruebas locales de ingeniería:
   ```powershell
   [System.Environment]::SetEnvironmentVariable('STEAMBETA_API_URL', 'https://staging-api.steambeta.campus.edu', 'User')
   ```
2. **Archivo de Propiedades Embebido (`steambeta.properties`):**
   Inyectado durante la compilación de la versión oficial apuntando a `https://api.steambeta.campus.edu`.
3. **Fallback Seguro por Defecto:**
   Si no se define ninguna propiedad externa, asume el host central oficial de la universidad.

---

## 5. Pautas para el Despliegue de la Futura Versión Web

La arquitectura centralizada sentó las bases para el lanzamiento transparente de un portal web institucional:
1. **Consumo de la Misma API REST:** Los endpoints `/api/**` devuelven JSON estándar y manejan autorización por cabeceras y cookies compatibles con navegadores modernos.
2. **CORS Centralizado:** Los orígenes permitidos se administran en `application-production.properties` vía `app.cors.allowed-origins`.
3. **Despliegue del Frontend Web:** Puede alojarse en infraestructura CDN estática (Vercel, Cloudflare Pages o AWS CloudFront / S3) consumiendo el mismo clúster Spring Boot central.
