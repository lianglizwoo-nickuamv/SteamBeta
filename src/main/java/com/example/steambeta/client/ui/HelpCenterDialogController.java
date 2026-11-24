package com.example.steambeta.client.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.stage.Stage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Controlador del Centro de Ayuda Contextual (FXML) — SteamBeta Campus.
 * Muestra documentación real en español adaptada automáticamente al módulo abierto.
 */
public final class HelpCenterDialogController {

    private record HelpTopic(
            String key,
            String displayName,
            String purpose,
            String capabilities,
            String steps,
            String security,
            String troubleshooting
    ) {}

    @FXML private Label moduleBadge;
    @FXML private ListView<String> moduleList;
    @FXML private Label topicTitle;
    @FXML private Label topicPurpose;
    @FXML private Label topicCapabilities;
    @FXML private Label topicSteps;
    @FXML private Label topicSecurity;
    @FXML private Label topicTroubleshooting;

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
                        "Espacio global para descubrir, crear y colaborar en comunidades académicas, de estudio, proyectos o carrera.",
                        "• Mis Comunidades: entrar a tus espacios, publicar conversaciones, formular dudas Q&A, compartir recursos y programar eventos.\n• Explorar Comunidades: buscar comunidades públicas y por invitación de otros estudiantes.\n• Ver ocupación real y espacios disponibles (límite configurable de 2 a 50 integrantes).",
                        "• Crear: pulsa '+ Crear Comunidad', define privacidad (Pública, Solo con invitación o Privada) y capacidad máxima.\n• Unirse: en comunidades públicas pulsa 'Unirse'; en comunidades con invitación o privadas usa 'Ingresar código' o 'Unirse con Código'.\n• Eliminar publicaciones: solo el autor de cada publicación puede eliminarla.",
                        "El creador adquiere rol de Administrador Local (ADMIN). Las comunidades privadas jamás aparecen en búsquedas públicas. Los límites de capacidad y códigos se validan atómicamente en PostgreSQL.",
                        "• 'Comunidad llena': se alcanzó la capacidad máxima de integrantes.\n• 'El código no corresponde': verifica que el código pertenezca a la comunidad seleccionada y no haya expirado."),
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
                        "Permite encontrar compañeros con materias o intereses afines, consultar sus fichas públicas y contactarlos mediante correo electrónico cuando lo hayan autorizado.",
                        "• Completar y actualizar tu ficha de estudio (materias, temas de interés, apoyo que ofreces, apoyo que necesitas, disponibilidad y visibilidad).\n• Buscar compañeros por nombre, alias, materias, temas de interés o áreas de apoyo (búsqueda parcial sin distinción de mayúsculas).\n• Consultar la ficha pública completa de cualquier compañero con 'Ver ficha'.\n• Contactar mediante 'Contactar' -> 'Enviar correo' (abre tu cliente de correo con mailto:) o 'Copiar correo' cuando el estudiante habilitó su correo como público.",
                        "• Completar ficha: pulsa 'Mi Ficha de Estudio', completa tus datos y marca la casilla de visibilidad para aparecer en búsquedas.\n• Buscar compañeros: escribe un nombre, alias o materia en el buscador y pulsa 'Buscar'; usa 'Limpiar' para ver nuevamente a todos los compañeros visibles.\n• Consultar y contactar: en la tarjeta de un compañero pulsa 'Ver ficha' o 'Contactar'. Actualmente SteamBeta no cuenta con mensajería privada directa; el contacto se realiza por correo electrónico público.",
                        "Solo las fichas marcadas como visibles aparecen en 'Compañeros Disponibles' (nunca te verás a ti mismo en los resultados). El correo electrónico permanece privado de forma predeterminada y jamás se expone a otros estudiantes salvo que el propietario active expresamente 'Hacer visible mi correo electrónico' en Mi Perfil.",
                        "• Si un compañero muestra 'Este estudiante no ha habilitado el contacto por correo electrónico', significa que mantiene su correo privado en Mi Perfil.\n• Para mantener tu correo privado, deja desmarcada la opción de visibilidad de correo en Mi Perfil → Editar Perfil."),
                new HelpTopic("EXCHANGE", "Intercambio Universitario",
                        "Catálogo colaborativo sin transacciones monetarias para donar, prestar o intercambiar libros, apuntes, herramientas y documentos.",
                        "• Publicar materiales en categorías (Libros, Apuntes, Herramientas, Calculadoras, Equipo de laboratorio, Otros).\n• Adjuntar hasta 5 archivos (máx. 25 MB c/u): PDF, DOC, DOCX, PPT, PPTX e imágenes (JPG, PNG, GIF, WEBP, BMP, TIFF).\n• Previsualizar imágenes compatibles y descargar archivos originales.\n• Filtrar por categoría o ver 'Mis publicaciones'.",
                        "• Publicar: pulsa '+ Publicar Material', completa los datos y opcionalmente usa '+ Adjuntar archivos'.\n• Detalle/Descarga: pulsa 'Ver detalle / Adjuntos' en cualquier tarjeta.\n• Editar/Eliminar: disponible únicamente en tus propias publicaciones.",
                        "Los archivos se almacenan fuera del repositorio bajo identificadores seguros; el servidor valida extensión, tamaño y contenido real antes de guardarlos. Solo el propietario puede editar o eliminar su publicación.",
                        "• 'Formato no admitido': verifica que el archivo sea un documento, presentación o imagen válida y no supere 25 MB.\n• No se permiten archivos ejecutables ni scripts."),
                new HelpTopic("OPPORTUNITIES", "Oportunidades Campus",
                        "Cartelera universitaria de becas, pasantías, concursos, cursos, eventos y convocatorias.",
                        "• Explorar oportunidades activas filtrando por categoría (Becas, Pasantías, Concursos, Eventos).\n• Abrir o copiar el enlace oficial de cada convocatoria.\n• Publicar, editar y eliminar tus propias convocatorias.",
                        "• Publicar: pulsa '+ Publicar Oportunidad', indica título, categoría, entidad convocante, descripción, enlace oficial (http/https) y fecha de cierre (AAAA-MM-DD).\n• Editar/Eliminar: usa los botones en tus publicaciones.",
                        "Solo el creador de la convocatoria (o un moderador global) puede editarla o eliminarla. Las convocatorias vencidas dejan de mostrarse automáticamente.",
                        "• Si el enlace es rechazado, verifica que comience por https:// o http:// y no contenga espacios."),
                new HelpTopic("NOTIFICATIONS", "Notificaciones",
                        "Bandeja centralizada de avisos generados por tu actividad en comunidades y moderación.",
                        "• Consultar notificaciones recientes.\n• Marcar avisos como leídos para actualizar el contador de la barra superior.",
                        "• Haz clic en una notificación o usa las opciones de lectura para actualizar su estado.",
                        "Cada estudiante únicamente accede a sus propias notificaciones mediante la sesión autenticada.",
                        "• Si el contador no cambia de inmediato, pulsa el botón Actualizar en la vista de notificaciones."),
                new HelpTopic("PROFILE", "Mi Perfil",
                        "Gestiona tu información pública de estudiante dentro de SteamBeta Campus.",
                        "• Consultar tu alias, universidad declarada, carrera, semestre, biografía e intereses.\n• Decidir si tu correo es visible públicamente.\n• Revisar el estado de sanciones si aplica.",
                        "• Editar: pulsa 'Editar Perfil', actualiza los campos deseados y confirma para persistir en PostgreSQL.",
                        "Tu cuenta autenticada solo puede modificar su propio perfil mediante funciones seguras RLS.",
                        "• El alias debe tener entre 2 y 60 caracteres y el semestre entre 1 y 20."),
                new HelpTopic("SETTINGS", "Configuración",
                        "Consulta el estado de conexión con el servidor, la URL del entorno y ajustes de sesión.",
                        "• Verificar conectividad con el backend de SteamBeta Campus.\n• Consultar parámetros del entorno activo.",
                        "• Las variables de entorno (como URL del backend) se configuran al iniciar el cliente sin almacenar contraseñas en texto plano.",
                        "Las sesiones utilizan cookies HttpOnly y protección CSRF en cada operación de escritura.",
                        "• Si falla la conexión, comprueba que el backend esté escuchando en el puerto configurado.")
        );

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
