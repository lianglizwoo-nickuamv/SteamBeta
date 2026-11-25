package com.example.steambeta.backend.report;

import static org.junit.jupiter.api.Assertions.*;

import com.example.steambeta.backend.report.PersonalReportDtos.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

class PersonalReportEngineTest {

    private final JasperReportEngine engine = new JasperReportEngine();

    @Test
    void allFourTemplatesExportValidPdfAndXlsxWithDataAndEmpty() throws Exception {
        verifyTemplate("personal_academic_activity", "Actividad", List.of(
                new AcademicActivityRow("Publicación", "Java 21", "Guía de colecciones", "Activa", "24/11/2026 18:00"),
                new AcademicActivityRow("Respuesta Q&A", "Java 21", "Uso de PriorityQueue", "Solución aceptada", "24/11/2026 18:30")
        ));
        verifyTemplate("personal_communities", "Comunidades", List.of(
                new CommunityMembershipRow("Programación Java", "Académica", "Pública", "Administrador", "Activa", "20/11/2026", "12 / 50")
        ));
        verifyTemplate("personal_academic_planning", "Planificacion", List.of(
                new PlanningTaskRow("Entrega Laboratorio 3", "Implementar árbol AVL", "Estructuras", "En proceso", "Alta", "28/11/2026", "20/11/2026")
        ));
        verifyTemplate("personal_exchange_publications", "Intercambio", List.of(
                new ExchangePublicationRow("Cálculo Stewart", "Libros", "Donación", "Buen estado", "Disponible", "1 adj. (PDF)", "24/11/2026")
        ));
    }

    private void verifyTemplate(String template, String sheet, List<?> rows) throws Exception {
        Map<String, Object> params = Map.of(
                "reportTitle", "Reporte de Prueba",
                "studentAlias", "Estudiante QA",
                "periodLabel", "Histórico completo",
                "generatedAt", "24/11/2026 21:00",
                "summaryLine", "Resumen real verificado",
                "emptyNotice", "Nota informativa."
        );

        // Con datos: PDF y XLSX
        byte[] pdf = engine.render(template, params, rows, JasperReportEngine.Format.PDF, sheet);
        assertPdf(pdf, template);
        byte[] xlsx = engine.render(template, params, rows, JasperReportEngine.Format.XLSX, sheet);
        assertXlsx(xlsx, template);

        // Sin datos (período vacío): PDF y XLSX válidos
        byte[] emptyPdf = engine.render(template, params, List.of(), JasperReportEngine.Format.PDF, sheet);
        assertPdf(emptyPdf, template + " (empty)");
        byte[] emptyXlsx = engine.render(template, params, List.of(), JasperReportEngine.Format.XLSX, sheet);
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
