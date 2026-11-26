package com.example.steambeta.backend.report;

import static org.junit.jupiter.api.Assertions.*;

import com.example.steambeta.backend.report.PersonalReportDtos.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperPrintManager;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class PersonalReportEngineTest {

    private final JasperReportEngine engine = new JasperReportEngine();

    @Test
    void allFourTemplatesExportValidPdfAndXlsxWithDataAndEmpty() throws Exception {
        String previewDirProp = System.getProperty("steambeta.report.preview.dir");
        Path previewDir = (previewDirProp != null && !previewDirProp.isBlank()) ? Path.of(previewDirProp) : null;
        if (previewDir != null) {
            Files.createDirectories(previewDir);
        }

        verifyTemplate("personal_academic_activity", "Actividad", "Mi actividad académica",
                "Resumen detallado · Publicaciones: 2 · Preguntas: 1 · Respuestas Q&A: 2 · Comentarios: 1 · Soluciones aceptadas: 1",
                new String[]{"PUBLICACIONES", "2", "PREGUNTAS", "1", "RESPUESTAS Q&A", "2", "SOLUCIONES ACEPT.", "1"},
                List.of(
                        new AcademicActivityRow("Publicación", "Programación Java", "Guía de colecciones y Streams en Java 21", "Activa", "24/11/2026 18:10"),
                        new AcademicActivityRow("Pregunta", "Estructuras de Datos", "¿Complejidad de PriorityQueue frente a TreeSet en Dijkstra?", "Resuelta", "24/11/2026 18:24"),
                        new AcademicActivityRow("Respuesta Q&A", "Bases de Datos", "Normalización 3FN y diseño de claves foráneas con RLS", "Solución aceptada", "23/11/2026 15:40"),
                        new AcademicActivityRow("Comentario", "Programación Java", "En «Práctica de laboratorio»: Adjunto pruebas unitarias JUnit 5", "Activa", "22/11/2026 11:05")
                ), previewDir, "01-simple-blue-actividad.png");

        verifyTemplate("personal_communities", "Comunidades", "Mis comunidades",
                "Agrupado por categoría · Comunidades activas: 4 · Administrador: 1 · Moderador: 1 · Miembro: 2",
                new String[]{"COMUNIDADES ACTIVAS", "4", "ADMINISTRADOR", "1", "MODERADOR", "1", "MIEMBRO", "2"},
                List.of(
                        new CommunityMembershipRow("Programación Java y Estructuras", "Académica", "Pública", "Administrador", "Activa", "15/08/2026", "28 / 50"),
                        new CommunityMembershipRow("Bases de Datos PostgreSQL", "Académica", "Solo invitación", "Miembro", "Activa", "02/09/2026", "19 / 30"),
                        new CommunityMembershipRow("Grupo de Cálculo Multivariable", "Estudio", "Pública", "Moderador", "Activa", "10/09/2026", "14 / 25"),
                        new CommunityMembershipRow("Semillero de Ingeniería de Software", "Proyectos", "Privada", "Miembro", "Activa", "01/10/2026", "9 / 15")
                ), previewDir, "02-coffee-comunidades.png");

        verifyTemplate("personal_exchange_publications", "Intercambio", "Mis publicaciones de intercambio",
                "Modalidades solidarias · Total: 3 · Intercambio: 1 · Donación: 1 · Préstamo: 1",
                new String[]{"PUBLICACIONES", "3", "INTERCAMBIO", "1", "DONACIÓN", "1", "PRÉSTAMO", "1"},
                List.of(
                        new ExchangePublicationRow("Cálculo de una Variable — Stewart 8.ª ed.", "Libros", "Intercambio", "Excelente estado, busco Álgebra Lineal de Grossman.", "Disponible", "2 adj. (PDF, PNG)", "24/11/2026"),
                        new ExchangePublicationRow("Guía resuelta de Estructuras de Datos", "Apuntes", "Donación", "Árboles AVL, grafos y tablas hash del semestre.", "Disponible", "2 adj. (PDF, PPTX)", "22/11/2026"),
                        new ExchangePublicationRow("Calculadora científica Casio fx-991EX", "Herramientas", "Préstamo", "Préstamo por dos semanas para parciales.", "Disponible", "1 adj. (PNG)", "20/11/2026")
                ), previewDir, "03-leaf-green-intercambio.png");

        verifyTemplate("personal_academic_planning", "Planificacion", "Mi planificación académica",
                "Detalle por estado · Pendientes: 1 · En proceso: 1 · En revisión: 0 · Completadas: 1 · Vencidas: 1",
                new String[]{"TOTAL TAREAS", "3", "EN CURSO / PEND.", "2", "COMPLETADAS", "1", "VENCIDAS REALES", "1"},
                List.of(
                        new PlanningTaskRow("Entrega Laboratorio 3 — Árbol AVL", "Implementar rotaciones simples y dobles con pruebas", "Estructuras", "En proceso", "Alta", "28/11/2026", "20/11/2026"),
                        new PlanningTaskRow("Informe de Normalización 3FN", "Entregar esquema relacional y consultas de prueba", "Bases de Datos", "Pendiente (Vencida)", "Urgente", "22/11/2026", "10/11/2026"),
                        new PlanningTaskRow("Resumen Capítulo 4 — Concurrencia", "Guía de estudio para segundo parcial", "Sistemas Operativos", "Completada", "Media", "18/11/2026", "12/11/2026")
                ), previewDir, "04-leaf-grey-planificacion.png");
    }

    private void verifyTemplate(String template, String sheet, String title, String summary,
                                String[] kpis, List<?> rows, Path previewDir, String previewName) throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("reportTitle", title);
        params.put("studentAlias", "Sofía Martínez");
        params.put("periodLabel", "01/08/2026 al 25/11/2026");
        params.put("generatedAt", "25/11/2026 10:30");
        params.put("summaryLine", summary);
        params.put("emptyNotice", "Total de registros propios incluidos en este informe: " + rows.size() + ".");
        params.put("kpi1Lbl", kpis[0]); params.put("kpi1Val", kpis[1]);
        params.put("kpi2Lbl", kpis[2]); params.put("kpi2Val", kpis[3]);
        params.put("kpi3Lbl", kpis[4]); params.put("kpi3Val", kpis[5]);
        params.put("kpi4Lbl", kpis[6]); params.put("kpi4Val", kpis[7]);

        // Fuentes de datos reales para gráficos cuando la plantilla los utiliza
        Supplier<Map<String, Object>> freshParams = () -> {
            Map<String, Object> copy = new HashMap<>(params);
            copy.put("activityChartData", new JRBeanCollectionDataSource(List.of(
                    new ChartMetric("Actividad propia", "Publicaciones", 2),
                    new ChartMetric("Actividad propia", "Preguntas", 1),
                    new ChartMetric("Actividad propia", "Respuestas", 2),
                    new ChartMetric("Actividad propia", "Comentarios", 1),
                    new ChartMetric("Actividad propia", "Soluciones", 1)
            )));
            copy.put("communityChartData", new JRBeanCollectionDataSource(List.of(
                    new ChartMetric("Comunidades", "Programación Java", 3),
                    new ChartMetric("Comunidades", "Estructuras de Datos", 2),
                    new ChartMetric("Comunidades", "Bases de Datos", 2)
            )));
            copy.put("planningChartData", new JRBeanCollectionDataSource(List.of(
                    new ChartMetric("Tareas", "Pendientes", 1),
                    new ChartMetric("Tareas", "En proceso", 1),
                    new ChartMetric("Tareas", "En revisión", 0),
                    new ChartMetric("Tareas", "Completadas", 1),
                    new ChartMetric("Tareas", "Vencidas", 1)
            )));
            return copy;
        };

        // Con datos: PDF y XLSX
        byte[] pdf = engine.render(template, freshParams.get(), rows, JasperReportEngine.Format.PDF, sheet);
        assertPdf(pdf, template);
        byte[] xlsx = engine.render(template, freshParams.get(), rows, JasperReportEngine.Format.XLSX, sheet);
        assertXlsx(xlsx, template);

        // Renderizar vista previa PNG real de la página 1 si se solicita
        if (previewDir != null) {
            try (InputStream is = new ClassPathResource("reports/" + template + ".jrxml").getInputStream()) {
                JasperReport jr = JasperCompileManager.compileReport(is);
                JasperPrint print = JasperFillManager.fillReport(jr, freshParams.get(), new JRBeanCollectionDataSource(rows));
                BufferedImage img = (BufferedImage) JasperPrintManager.printPageToImage(print, 0, 1.5f);
                ImageIO.write(img, "png", previewDir.resolve(previewName).toFile());
            }
        }

        // Sin datos (período vacío): PDF y XLSX válidos
        byte[] emptyPdf = engine.render(template, freshParams.get(), List.of(), JasperReportEngine.Format.PDF, sheet);
        assertPdf(emptyPdf, template + " (empty)");
        byte[] emptyXlsx = engine.render(template, freshParams.get(), List.of(), JasperReportEngine.Format.XLSX, sheet);
        assertXlsx(emptyXlsx, template + " (empty)");
    }

    private static void assertPdf(byte[] bytes, String label) {
        assertNotNull(bytes, label);
        assertTrue(bytes.length > 500, label + " PDF too small");
        String header = new String(bytes, 0, 5, StandardCharsets.US_ASCII);
        assertEquals("%PDF-", header, label + " missing %PDF- header");
    }

    private static void assertXlsx(byte[] bytes, String label) throws Exception {
        assertNotNull(bytes, label);
        assertTrue(bytes.length > 500, label + " XLSX too small");
        Set<String> entries = new HashSet<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                entries.add(e.getName());
            }
        }
        assertTrue(entries.contains("[Content_Types].xml"), label + " missing [Content_Types].xml");
        assertTrue(entries.contains("xl/workbook.xml"), label + " missing xl/workbook.xml");
    }
}
