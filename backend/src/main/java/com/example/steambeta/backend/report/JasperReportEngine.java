package com.example.steambeta.backend.report;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import net.sf.jasperreports.engine.export.ooxml.JRXlsxExporter;
import net.sf.jasperreports.export.SimpleXlsxReportConfiguration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Motor central de compilación, rellenado y exportación en memoria con JasperReports 7.1.1.
 * No ejecuta SQL en plantillas: recibe exclusivamente colecciones Java ya autorizadas.
 */
@Service
public class JasperReportEngine {

    public enum Format {
        PDF("application/pdf", ".pdf"),
        XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", ".xlsx");

        private final String contentType;
        private final String extension;

        Format(String contentType, String extension) {
            this.contentType = contentType;
            this.extension = extension;
        }

        public String contentType() { return contentType; }
        public String extension() { return extension; }

        public static Format parse(String raw) {
            if (raw == null || raw.isBlank()) return PDF;
            return switch (raw.strip().toUpperCase()) {
                case "PDF" -> PDF;
                case "XLSX", "EXCEL" -> XLSX;
                default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Formato de reporte no admitido. Usa PDF o XLSX.");
            };
        }
    }

    private final Map<String, JasperReport> compiledCache = new ConcurrentHashMap<>();

    public byte[] render(String templateName, Map<String, Object> params, Collection<?> rows,
                         Format format, String sheetName) {
        try {
            JasperReport compiled = compiledCache.computeIfAbsent(templateName, this::compileTemplate);
            Map<String, Object> safeParams = new HashMap<>(params);
            JRDataSource ds = (rows == null || rows.isEmpty())
                    ? new JREmptyDataSource(0)
                    : new JRBeanCollectionDataSource(rows);

            JasperPrint print = JasperFillManager.fillReport(compiled, safeParams, ds);
            if (format == Format.PDF) {
                return JasperExportManager.exportReportToPdf(print);
            }
            return exportXlsx(print, sheetName);
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo generar el reporte solicitado.", ex);
        }
    }

    private JasperReport compileTemplate(String templateName) {
        String path = "reports/" + templateName + ".jrxml";
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            throw new IllegalStateException("Plantilla JRXML no encontrada: " + path);
        }
        try (InputStream is = resource.getInputStream()) {
            return JasperCompileManager.compileReport(is);
        } catch (Exception ex) {
            throw new IllegalStateException("Error al compilar la plantilla JRXML: " + path, ex);
        }
    }

    private static byte[] exportXlsx(JasperPrint print, String sheetName) throws JRException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JRXlsxExporter exporter = new JRXlsxExporter();
        exporter.setExporterInput(new SimpleExporterInput(print));
        exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(out));

        SimpleXlsxReportConfiguration config = new SimpleXlsxReportConfiguration();
        config.setDetectCellType(true);
        config.setRemoveEmptySpaceBetweenRows(true);
        config.setRemoveEmptySpaceBetweenColumns(true);
        config.setWhitePageBackground(false);
        config.setIgnoreGraphics(true);
        config.setCollapseRowSpan(false);
        if (sheetName != null && !sheetName.isBlank()) {
            config.setSheetNames(new String[]{sheetName});
        }
        exporter.setConfiguration(config);
        exporter.exportReport();
        return out.toByteArray();
    }
}
