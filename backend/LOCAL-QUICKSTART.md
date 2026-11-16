# Prueba local de registro, acceso, perfil y salida

Esta guía corresponde a la entrega de cuentas independientes en la rama
codex/community-foundation. Primero se prueba el backend mediante HTTP. La
entrega posterior incorpora registro visual y comunidad. Para ese recorrido
utiliza [PILOT-QUICKSTART.md](PILOT-QUICKSTART.md).

## Qué abrir

Abre SteamBeta en IntelliJ. En la parte inferior abre Terminal y selecciona
Windows PowerShell. También puedes abrir Windows PowerShell desde el menú Inicio.
Necesitarás dos pestañas de terminal. La primera mantiene el servidor encendido;
la segunda ejecuta la comprobación y vuelve a mostrar el indicador PS.

En ambas terminales, este comando selecciona la carpeta que contiene mvnw.cmd.
Termina inmediatamente.

```powershell
cd 'C:\Users\Woo\Downloads\ProyectoSteam\SteamBeta'
```

## Requisitos comprobados en esta máquina

El 16 de noviembre se encontraron JDK Temurin 21.0.13 en .replica-work,
Maven Wrapper 3.8.5, PostgreSQL 18.6 y credenciales de aplicación cifradas con
DPAPI para el usuario de Windows actual. Los scripts de arranque seleccionan
el JDK local existente sin cambiar las variables globales de Windows.

PostgreSQL debe estar disponible en localhost:5432. El backend se conecta con
replica_runtime y las migraciones utilizan replica_migrator. Las credenciales
DPAPI pertenecen al usuario actual y se conservan fuera del repositorio.

La comprobación real encontró V1, V2 y V3 aplicadas correctamente. Por tanto,
para repetir esta prueba en esta máquina no hace falta ejecutar Migration,
Demo ni volver a preparar contraseñas.

## Terminal 1 · Servidor

Si Replica Backend (local) ya está ejecutándose en IntelliJ y muestra
Started BackendApplication, utiliza ese servidor y pasa a la terminal 2.

En caso contrario, este comando inicia el backend con las credenciales locales
y el perfil local. Debe permanecer ejecutándose.

```powershell
.\backend\scripts\start-local.ps1 -Mode Normal
```

El arranque correcto muestra ambas líneas, con variaciones en los tiempos:

```text
Tomcat started on port 8080 (http)
Started BackendApplication
```

La terminal no vuelve a PS mientras el servidor funciona. No es un bloqueo.
La ventana JavaFX no se abre mediante este comando.

## Terminal 2 · Recorrido API

Este comando actúa como un cliente. Envia peticiones al backend local,
mantiene las cookies en memoria, obtiene el token CSRF y lo renueva tras
autenticarse. No hace falta copiar cookies o tokens manualmente.

```powershell
.\backend\scripts\check-local-api.ps1
```

Debe terminar. La primera ejecución registra local.check.member; las siguientes
reutilizan esa cuenta. La contraseña aleatoria se guarda cifrada con DPAPI en
el perfil local de Windows. No se imprime ni se copia al portapapeles.
El script conserva la cuenta y no borra datos existentes.

El resultado correcto muestra:

| Paso | Resultado esperado |
| --- | --- |
| Perfil sin iniciar sesión | HTTP 401 |
| Obtener CSRF y cookie | HTTP 200 |
| Registro inicial | HTTP 201 |
| Registro repetido | HTTP 409, seguido de acceso correcto |
| Login | HTTP 200 |
| Perfil propio | HTTP 200 |
| Biblioteca sin matrícula | HTTP 403, esperado para esta cuenta independiente |
| Logout | HTTP 204 |
| Perfil después de salir | HTTP 401 |

El mensaje final es RECORRIDO API LOCAL COMPLETADO. El servidor de la terminal 1
continúa funcionando. El script solo muestra estados y el identificador público
de prueba. Comprueba que el perfil incluye únicamente los cinco campos acordados
y que la identidad institucional se marca como no verificada.

Si local.check.member ya pertenece a otra contraseña, el script no la modifica.
Puedes elegir otra cuenta de prueba con este comando, que también termina:

```powershell
.\backend\scripts\check-local-api.ps1 -Login local.check.segundo
```

Cada identificador distinto crea una cuenta conservada. Mantén el mismo para
repetir la prueba sin acumular cuentas. El script es específico de Windows/DPAPI.

## Cómo reconocer errores

| Mensaje o situación | Significado y siguiente comprobación |
| --- | --- |
| No se recibió respuesta HTTP | Backend detenido, URL equivocada o conexión local bloqueada |
| Port 8080 was already in use | Ya hay un proceso escuchando; usa el backend existente o identifica el proceso antes de detenerlo |
| Connection refused a localhost:5432 | PostgreSQL no está disponible; revisar su servicio |
| Password authentication failed | Credenciales JDBC no válidas para esa base |
| Missing local identity o error DPAPI | Se está usando otra cuenta de Windows o falta su configuración local |
| release version 21 not supported | Maven utiliza un JDK anterior; revisar JAVA_HOME y mvnw.cmd -v |
| HTTP 401 durante login | El identificador y la contraseña no coinciden, o la cuenta está deshabilitada |
| HTTP 403 durante login/logout | Revisar cookie, token CSRF y perfil local; no equivale a contraseña incorrecta |
| HTTP 500 | Revisar consola del backend y estado de las migraciones |
| Flyway checksum mismatch | Detenerse y revisar las migraciones; no editar historial ni usar repair automáticamente |

401 y 403 son resultados correctos en los pasos negativos indicados en la tabla
del recorrido. Un fallo real interrumpe el script y muestra el paso afectado.
Al compartir un error, conserva el código HTTP y el mensaje de consola, ocultando
credenciales, cookies, tokens y cuerpos que contengan contraseñas.

## Detener y volver a arrancar

En la terminal que mantiene el backend, pulsa Ctrl+C y espera el cierre.
Si lo iniciaste desde una configuración de IntelliJ, utiliza su botón Stop.
Las sesiones en memoria dejan de ser válidas al reiniciar.

Para volver a arrancar, ejecuta en la raíz el mismo comando de modo Normal.
Espera las dos líneas de arranque y repite el comprobador en la segunda terminal.
No hace falta repetir la migración ni el registro demo.

## Migraciones y recuperación

Antes de esta comprobación, V3 ya estaba aplicada. No se ejecutó ninguna
migración nueva durante el recorrido. Se guardó un respaldo previo a crear la
cuenta de prueba en .replica-work/backups/before-local-api-20261116-110544.dump.
El directorio está limitado al usuario actual y SYSTEM. El archivo contiene
datos y hashes de contraseñas y no debe subirse a Git ni compartirse como código.
Su archivo .sha256 permite comprobar integridad y .toc.txt contiene el índice.

La copia se restauró en un clúster PostgreSQL temporal, separado del servidor
real. Las instrucciones y el resultado están en LOCAL-VERIFICATION.md.
Una recuperación futura debe restaurar primero en una base nueva con los roles
requeridos, verificarla y decidir después qué conexión usar. Evita restaurar
sobre steam_academico o ejecutar clean/drop sobre la base existente.

Si en otro momento faltara una migración nueva, primero revisar historial,
datos y respaldo. El comando de modo Migration termina; después se inicia
modo Normal. Para el estado comprobado aquí, esa operación es innecesaria.

## Qué está y qué no está verificado

El comprobador realiza registro, login, perfil y logout contra la base local
real a través del backend. Verifica la identidad independiente de una matrícula.
No es una sustitución de la prueba automatizada en PostgreSQL desechable.

La cuenta independiente de esta comprobación recibe 403 en sus rutas académicas.
JavaFX ahora entra a Mis comunidades y conserva el acceso académico opcional.
El comprobador API no abre la ventana ni constituye aceptación visual.
