package com.example.steambeta.client.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.stage.Stage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HelpCenterDialogController {

        private record HelpTopic(
                        String key,
                        String displayName,
                        String purpose,
                        String capabilities,
                        String steps,
                        String security,
                        String troubleshooting) {
        }

        @FXML
        private Label moduleBadge;
        @FXML
        private ListView<String> moduleList;
        @FXML
        private Label topicTitle;
        @FXML
        private Label topicPurpose;
        @FXML
        private Label topicCapabilities;
        @FXML
        private Label topicSteps;
        @FXML
        private Label topicSecurity;
        @FXML
        private Label topicTroubleshooting;

        private final Map<String, HelpTopic> topicsByName = new LinkedHashMap<>();
        private final Map<String, String> keyToDisplayName = new LinkedHashMap<>();

        @FXML
        public void initialize() {
                List<HelpTopic> topics = List.of(
                                new HelpTopic("HOME", "Inicio",
                                                "Panel principal de bienvenida con accesos rápidos a tus comunidades, próximos eventos y tareas académicas.",
                                                "• Ver un resumen rápido de tu actividad.\n• Acceder con un clic a tus comunidades, calendario o planificación.",
                                                "• Desde Inicio puedes navegar hacia cualquier módulo usando los accesos rápidos o la barra lateral izquierda.",
                                                "Requiere sesión iniciada. Toda la información mostrada corresponde exclusivamente a tu cuenta autenticada en PostgreSQL.",
                                                "• Si el indicador superior marca desconexión, verifica que el servidor local esté ejecutándose en http://localhost:8080 y pulsa Actualizar."),
                                new HelpTopic("COMMUNITIES", "Comunidades",
                                                "Espacio global para descubrir, crear, gestionar y colaborar en comunidades académicas, de estudio, proyectos, carrera o interés.",
                                                "• Mis Comunidades: entrar a tus espacios y consultar su Presentación (descripción, categoría, acceso, ocupación, reglas, rol y actividad real).\n• Conversaciones (debates y publicaciones): anuncios, debates, experiencias e ideas generales de la comunidad.\n• Preguntas y respuestas (Q&A): dudas académicas concretas, respuestas de integrantes y soluciones aceptadas.\n• Explorar Comunidades: catálogo con filtros combinables de categoría, acceso (Públicas / Requieren invitación), disponibilidad (Con espacios / Llenas), ordenamiento y 'Limpiar filtros'.\n• Salir de la comunidad: abandonar cualquier comunidad desde su vista de detalle respetando las reglas de administración.",
                                                "• Crear: pulsa '+ Crear Comunidad', define privacidad (Pública, Solo con invitación o Privada) y capacidad máxima (2 a 50).\n• Filtrar el catálogo: entra en 'Explorar Comunidades', combina categoría, acceso, disponibilidad y ordenamiento; pulsa 'Limpiar filtros' para volver al catálogo completo.\n• Unirse: en comunidades públicas pulsa 'Unirse'; en comunidades con invitación o privadas usa 'Ingresar código' o 'Unirse con Código'.\n• Salir: dentro de la comunidad pulsa 'Salir de la comunidad'. En comunidades públicas sales directamente con aviso breve; en comunidades solo con invitación o privadas se solicita confirmación previa.",
                                                "El creador adquiere rol de Administrador Local (ADMIN). Las comunidades privadas jamás aparecen en búsquedas públicas. Al salir de una comunidad pierdes acceso a sus conversaciones, preguntas, recursos, calendario e integrantes; el último administrador debe transferir la administración antes de salir.",
                                                "• 'No puedes salir porque eres el único administrador': asigna el rol ADMIN a otro integrante antes de abandonar la comunidad.\n• 'Comunidad llena': se alcanzó la capacidad máxima de integrantes.\n• 'El código no corresponde': verifica que el código pertenezca a la comunidad seleccionada y no haya expirado."),
                                new HelpTopic("LIBRARY", "Biblioteca Universitaria",
                                                "Reúne tus materias institucionales (cuando existe vinculación oficial) junto con las materias que registres manualmente para organizar tu cursada.",
                                                "• Consultar materias institucionales con su avance oficial y actividades obligatorias.\n• Registrar, editar y eliminar tus propias materias manuales (nombre, código, año, período, grupo, docente, créditos, horario y notas).\n• Filtrar tus materias por año académico y período o semestre.\n• Usar tus materias registradas como selección rápida al crear tareas en Mi Planificación.",
                                                "• Registrar: pulsa '+ Agregar materia', completa al menos nombre, año académico y período, y guarda.\n• Editar: selecciona una materia marcada como 'Registrada manualmente' y pulsa 'Editar'.\n• Eliminar: selecciona tu materia manual y pulsa 'Eliminar' (requiere confirmación).\n• Identificar origen: cada materia muestra la etiqueta 'Institucional' (matrícula oficial) o 'Registrada manualmente' (registro personal).\n• En Mi Planificación: al crear o editar una tarea, elige tu materia desde el desplegable de asignaturas registradas.",
                                                "Las materias manuales son estrictamente privadas por cuenta (protegidas mediante RLS forzada en PostgreSQL). Ningún otro estudiante puede consultarlas ni modificarlas. Nunca se presentan como matrículas verificadas por la universidad.",
                                                "• 'Materia duplicada': no puedes registrar dos veces la misma materia en el mismo año y período académico.\n• Si no aparecen materias institucionales, puedes usar '+ Agregar materia' para llevar tu organización personal sin depender de integración universitaria."),
                                new HelpTopic("CALENDAR", "Calendario",
                                                "Agenda visual en vista mensual, semanal y lista que combina los eventos de tus comunidades con tus tareas personales.",
                                                "• Alternar entre vista Mensual, Semanal y Agenda.\n• Filtrar por Exámenes, Entregas o Reuniones.\n• Pulsar cualquier evento o tarea para ver su detalle completo.\n• Programar nuevos eventos comunitarios con '+ Crear Evento'.",
                                                "• Crear evento: pulsa '+ Crear Evento', selecciona una comunidad a la que pertenezcas, tipo, fecha y hora (zona America/Managua).\n• Las tareas personales con fecha de vencimiento aparecen automáticamente aquí sin convertirse en eventos comunitarios.",
                                                "Los eventos comunitarios requieren membresía activa en la comunidad. Tus tareas personales siguen siendo estrictamente privadas.",
                                                "• Si no puedes crear un evento, verifica que perteneces al menos a una comunidad universitaria."),
                                new HelpTopic("TASKS", "Mi Planificación",
                                                "Tablero Kanban personal y privado para organizar entregas, tareas y proyectos académicos.",
                                                "• Crear tareas con título, descripción, asignatura/proyecto, fecha de vencimiento y prioridad (Baja, Media, Alta, Urgente).\n• Mover tareas entre Pendiente, En proceso, En revisión y Completada.\n• Editar o eliminar tus tareas en cualquier momento.",
                                                "• Crear: pulsa '+ Nueva Tarea', completa los campos y guarda.\n• Editar: pulsa 'Editar' en la tarjeta.\n• Cambiar estado: usa 'En proceso', 'En revisión' o '✓ Completar'.\n• Eliminar: pulsa '✕' en la tarjeta.",
                                                "Tus tareas son 100 % privadas; ningún otro estudiante ni comunidad puede verlas ni modificarlas. Se validan mediante la sesión autenticada en PostgreSQL.",
                                                "• Si aparece un error de permisos RLS, asegúrate de que la migración V15 esté aplicada en el servidor."),
                                new HelpTopic("STUDY_TEAMS", "Equipos de Estudio",
                                                "Permite encontrar compañeros con materias o intereses afines, consultar sus fichas públicas completas y contactarlos mediante correo electrónico autorizado.",
                                                "• Completar y actualizar tu ficha de estudio (materias, temas de interés, apoyo que ofreces, apoyo que necesitas, disponibilidad y visibilidad).\n• Buscar compañeros por nombre, alias, materias, temas de interés o áreas de apoyo (búsqueda parcial sin distinción de mayúsculas).\n• Consultar la ficha pública en una ventana completa organizada en Identidad, Información académica y Contacto.\n• Contactar mediante 'Enviar correo' (abre tu cliente de correo con mailto:) o 'Copiar correo' únicamente cuando el estudiante habilitó su correo como público.",
                                                "• Completar ficha: pulsa 'Mi Ficha de Estudio', completa tus datos y marca la casilla de visibilidad para aparecer en búsquedas.\n• Buscar compañeros: escribe un nombre, alias o materia en el buscador y pulsa 'Buscar'; usa 'Limpiar' para ver nuevamente a todos los compañeros visibles.\n• Consultar y contactar: pulsa 'Ver ficha' o 'Contactar' en la tarjeta del compañero. SteamBeta no incluye mensajería privada uno a uno; el contacto se realiza por correo electrónico público autorizado.",
                                                "Solo las fichas marcadas como visibles aparecen en 'Compañeros Disponibles' y pueden abrirse por otros estudiantes (las fichas privadas devuelven no disponible). El correo electrónico permanece privado de forma predeterminada y jamás se expone salvo que el propietario active expresamente su visibilidad en Mi Perfil.",
                                                "• Si aparece 'Este estudiante no ha habilitado el contacto por correo electrónico', el compañero mantiene su correo privado en Mi Perfil.\n• Si una ficha devuelve que no está disponible o es privada, significa que el estudiante la ocultó desde su Ficha de Estudio."),
                                new HelpTopic("EXCHANGE", "Intercambio Universitario",
                                                "Catálogo colaborativo solidario sin dinero ni transacciones comerciales para intercambiar, regalar o prestar libros, apuntes, herramientas y materiales académicos.",
                                                "• Tres modalidades solidarias: Intercambio (trueque académico), Regalo / Donación (entrega gratuita) y Préstamo (cesión temporal con devolución acordada).\n• Filtros combinables por categoría (Libros, Herramientas, Apuntes), modalidad, búsqueda por texto (título, descripción o autor), 'Mis publicaciones' y botón 'Limpiar filtros'.\n• Adjuntar hasta 5 archivos por publicación (máx. 25 MB por archivo): PDF, DOC, DOCX, PPT, PPTX e imágenes (JPG, JPEG, PNG, GIF, WEBP, BMP, TIFF).\n• Previsualizar imágenes compatibles y descargar los archivos originales en el detalle de la publicación.",
                                                "• Publicar: pulsa '+ Publicar Material', elige categoría y modalidad (sin precios), describe el estado físico y el medio de contacto, y opcionalmente adjunta archivos.\n• Filtrar: combina categoría, modalidad y búsqueda; pulsa 'Limpiar filtros' para volver al catálogo completo.\n• Detalle y descarga: pulsa 'Ver detalle / Adjuntos' para revisar la descripción, previsualizar imágenes o descargar documentos.\n• Editar o eliminar: disponible únicamente en tus propias publicaciones desde la tarjeta o en 'Mis Publicaciones'.",
                                                "Los archivos se almacenan fuera del repositorio con identificadores seguros; el servidor valida extensión, tamaño (25 MB), firma binaria (magic bytes) y estructura OOXML antes de guardarlos. Solo el propietario puede editar o eliminar su publicación y sus adjuntos.",
                                                "• 'Formato no admitido' o 'contenido inválido': verifica que el archivo sea un PDF, documento Office o imagen auténtica y que no supere 25 MB.\n• Máximo 5 adjuntos por publicación (incluyendo los ya almacenados al editar).\n• Los archivos PDF, DOC/DOCX y PPT/PPTX no muestran vista previa de imagen; usa 'Descargar archivo' para abrirlos en tu equipo."),
                                new HelpTopic("OPPORTUNITIES", "Oportunidades Campus",
                                                "Cartelera universitaria de becas, pasantías, concursos, cursos, eventos y convocatorias.",
                                                "• Explorar oportunidades activas filtrando por categoría (Becas, Pasantías, Concursos, Eventos).\n• Abrir o copiar el enlace oficial de cada convocatoria.\n• Publicar, editar y eliminar tus propias convocatorias.",
                                                "• Publicar: pulsa '+ Publicar Oportunidad', indica título, categoría, entidad convocante, descripción, enlace oficial (http/https) y fecha de cierre (AAAA-MM-DD).\n• Editar/Eliminar: usa los botones en tus publicaciones.",
                                                "Solo el creador de la convocatoria (o un moderador global) puede editarla o eliminarla. Las convocatorias vencidas dejan de mostrarse automáticamente.",
                                                "• Si el enlace es rechazado, verifica que comience por https:// o http:// y no contenga espacios."),
                                new HelpTopic("NOTIFICATIONS", "Notificaciones",
                                                "Bandeja centralizada de avisos académicos de tu cuenta en SteamBeta Campus.",
                                                "• Consultar notificaciones organizadas por estado (No leídas / Leídas) y recencia (Recientes en las últimas 24 h / Anteriores).\n• Filtrar entre 'Todas' y 'No leídas'.\n• Ver el tipo de aviso en español, origen, fecha real y marcar avisos como leídos sincronizando el contador superior de Avisos.",
                                                "• Usa las pestañas 'Todas' o 'No leídas' para filtrar tu bandeja.\n• Pulsa 'Marcar como leída' en cualquier aviso pendiente para actualizar su estado y el contador de la barra superior.\n• Pulsa 'Actualizar Avisos' para consultar el estado actual en el servidor.",
                                                "Cada estudiante únicamente accede a sus propias notificaciones mediante funciones seguras y RLS en PostgreSQL (V10). Nunca se muestran avisos de otras cuentas ni notificaciones simuladas.",
                                                "• Si tu bandeja aparece vacía, significa que tu cuenta no tiene notificaciones registradas en el servidor.\n• Nota técnica: actualmente el servidor consulta y marca avisos existentes; la generación automática de nuevas notificaciones por respuestas, invitaciones o recordatorios de calendario requiere una actualización futura del esquema del servidor."),
                                new HelpTopic("PROFILE", "Mi Perfil",
                                                "Gestiona tu identidad estudiantil en SteamBeta Campus: fotografía de perfil, datos académicos, intereses, biografía y privacidad de correo.",
                                                "• Registro en dos pasos: tras crear tu cuenta de acceso puedes completar inmediatamente tu presentación académica (universidad, carrera, semestre, intereses, biografía y fotografía) o pulsar 'Completar más tarde' y hacerlo desde Mi Perfil.\n• Fotografía de perfil: sube una imagen JPG, JPEG, PNG o WEBP (máximo 5 MB) con vista previa, cámbiala o elimínala en cualquier momento; sin foto se muestran tus iniciales.\n• Trayectoria e intereses: actualiza alias, universidad, carrera, semestre, intereses académicos y biografía.\n• Privacidad de correo: decide si tu correo electrónico es confidencial o visible para otros estudiantes.",
                                                "• Cambiar fotografía: en Mi Perfil o dentro de 'Editar Mi Información', pulsa 'Cambiar fotografía' y selecciona una imagen válida (JPG, PNG o WEBP hasta 5 MB).\n• Eliminar fotografía: pulsa 'Eliminar fotografía' y confirma para regresar a tus iniciales.\n• Editar perfil: pulsa 'Editar Mi Información', actualiza tus campos académicos o la casilla de visibilidad de correo y guarda los cambios.",
                                                "Tu cuenta autenticada solo puede modificar su propio perfil y su propia fotografía mediante funciones seguras en PostgreSQL (V17) y almacenamiento privado en el servidor. Otros estudiantes solo ven tu información pública (alias, fotografía, universidad, carrera, intereses y biografía donde corresponda); tu correo permanece privado salvo que habilites expresamente su visibilidad.",
                                                "• 'Formato no permitido' o 'archivo no decodificable': verifica que la fotografía sea una imagen real JPG, PNG o WEBP y no supere 5 MB ni 4096×4096 px.\n• El alias debe tener entre 2 y 60 caracteres y el semestre entre 1 y 20."),
                                new HelpTopic("SETTINGS", "Configuración",
                                                "Consulta el estado de conexión con el servidor, la URL del entorno y ajustes de sesión.",
                                                "• Verificar conectividad con el backend de SteamBeta Campus.\n• Consultar parámetros del entorno activo.",
                                                "• Las variables de entorno (como URL del backend) se configuran al iniciar el cliente sin almacenar contraseñas en texto plano.",
                                                "Las sesiones utilizan cookies HttpOnly y protección CSRF en cada operación de escritura.",
                                                "• Si falla la conexión, comprueba que el backend esté escuchando en el puerto configurado."));

                for (HelpTopic t : topics) {
                        topicsByName.put(t.displayName(), t);
                        keyToDisplayName.put(t.key(), t.displayName());
                        moduleList.getItems().add(t.displayName());
                }

                moduleList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
                        if (newVal != null && topicsByName.containsKey(newVal)) {
                                renderTopic(topicsByName.get(newVal));
                        }
                });

                selectModule("HOME");
        }

        public void selectModule(String viewId) {
                String key = viewId != null ? viewId.toUpperCase() : "HOME";
                String display = keyToDisplayName.getOrDefault(key, "Inicio");
                moduleList.getSelectionModel().select(display);
                moduleList.scrollTo(display);
                HelpTopic topic = topicsByName.get(display);
                if (topic != null) {
                        renderTopic(topic);
                }
        }

        private void renderTopic(HelpTopic t) {
                moduleBadge.setText(t.displayName().toUpperCase());
                topicTitle.setText(t.displayName());
                topicPurpose.setText(t.purpose());
                topicCapabilities.setText(t.capabilities());
                topicSteps.setText(t.steps());
                topicSecurity.setText(t.security());
                topicTroubleshooting.setText(t.troubleshooting());
        }

        @FXML
        private void onClose() {
                if (moduleList.getScene() != null && moduleList.getScene().getWindow() instanceof Stage s) {
                        s.close();
                }
        }
}
