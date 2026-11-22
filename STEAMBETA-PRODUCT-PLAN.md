# SteamBeta Campus · Plan de Producto y Visión Integral

## 1. Identidad y Misión del Producto
**SteamBeta Campus** es una plataforma universitaria integral diseñada por y para estudiantes, que combina la fluidez e interacción de una red social universitaria con herramientas prácticas de aprendizaje colaborativo, gestión de tareas y recursos académicos.

No es un sistema administrativo ni un LMS rígido institucional. Es el espacio digital donde los estudiantes organizan su vida universitaria, resuelven dudas complejas, comparten apuntes y construyen redes de colaboración.

---

## 2. Pilares Fundamentales del Ecosistema

### A. Mi Campus (Hub Central)
- **Bienvenida y Resumen:** Panel personalizado con estado de conexión centralizada.
- **Actividad Reciente:** Resumen de dudas, comentarios y eventos de sus comunidades.
- **Próximas Fechas Clave:** Exámenes y entregas consolidadas en una sola pantalla.

### B. Comunidades Universitarias
- **Espacios Temáticos y de Carrera:** Creación y exploración abierta de comunidades.
- **Códigos de Invitación Seguros:** Adhesión mediante códigos alfanuméricos (`CAMPUS...`) con límite de usos y expiración configurable (siempre asigna rol `MEMBER`).
- **Autonomía Operativa:** Los administradores comunitarios gestionan miembros y moderadores directamente desde la aplicación sin intervención técnica ni scripts.

### C. Centro de Aprendizaje (Q&A Académico)
- **Preguntas Formales:** Clasificación por materia, título y etiquetas temáticas.
- **Solución Aceptada:** El autor de la duda tiene la potestad exclusiva de seleccionar la respuesta más útil como solución, la cual queda destacada visualmente.
- **Filtros Inteligentes:** Búsqueda y filtrado por estado (`Todas`, `Resueltas`, `Pendientes`).

### D. Organizador Académico (Mi Planificación)
- **Tablero de Avance:** Gestión ágil de tareas en 4 estados: `Pendiente`, `En Proceso`, `En Revisión` y `Completada`.
- **Proyectos y Prioridades:** Clasificación por urgencia (`LOW`, `MEDIUM`, `HIGH`, `URGENT`) y fechas de vencimiento.

### E. Biblioteca Universitaria Colaborativa
- **Materiales de Estudio:** Apuntes, resúmenes, guías de laboratorio, presentaciones y enlaces a recursos educativos.
- **Seguridad Rigurosa:** Validación estricta de extensiones y tipos MIME, rechazo absoluto de archivos ejecutables y almacenamiento seguro fuera del repositorio.

### F. Equipos de Estudio y Afinidades
- **Ficha de Estudio Voluntaria:** Materias cursadas, áreas de fortaleza (dónde puedo ayudar), áreas donde se busca apoyo y disponibilidad horaria.
- **Conexión de Pares:** Recomendación de compañeros con afinidades académicas compartidas.

### G. Oportunidades Campus
- **Cartelera Vigente:** Becas, pasantías profesionales, concursos, voluntariados y eventos con fecha de cierre y enlace a la fuente oficial.

### H. Intercambio Universitario de Materiales
- **Trueque y Préstamo Solidario:** Donación, préstamo o intercambio de libros de texto, calculadoras y equipo de laboratorio entre alumnos (estrictamente sin transacciones financieras).

---

## 3. Matriz de Roles y Permisos

| Capacidad / Módulo | Estudiante (`USER`) | Moderador Comunitario (`MODERATOR`) | Admin Comunitario (`ADMIN`) | Moderador General (`GLOBAL_MODERATOR`) |
| :--- | :---: | :---: | :---: | :---: |
| Registrarse e Iniciar Sesión | Sí | Sí | Sí | Sí |
| Explorar y Crear Comunidades | Sí | Sí | Sí | Sí |
| Generar Invitaciones a su Comunidad | No | Sí | Sí | Sí |
| Moderar Reportes de su Comunidad | No | Sí | Sí | Sí |
| Asignar Moderadores en su Comunidad | No | No | Sí | Sí |
| Publicar Dudas y Marcar Solución | Sí | Sí | Sí | Sí |
| Tablero Personal de Tareas | Sí | Sí | Sí | Sí |
| Acceso a SteamBeta Control Center | **No** | **No** | **No** | **Sí** |
| Aplicar Sanciones y Suspensiones Globales | **No** | **No** | **No** | **Sí** |
| Ver Bitácora Inmutable de Auditoría | **No** | **No** | **No** | **Sí** |
