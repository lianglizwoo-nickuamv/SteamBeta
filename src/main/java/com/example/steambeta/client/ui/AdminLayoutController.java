package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import com.example.steambeta.client.SessionScope;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Controlador de la Consola Administrativa Independiente (SteamBeta Admin — FXML).
 * Navegación simplificada en 3 secciones:
 *   1. Dashboard institucional
 *   2. Directorio de usuarios (incluye Informe completo de usuarios en PDF/XLSX)
 *   3. Expediente individual (incluye los 4 reportes del estudiante seleccionado)
 */
public final class AdminLayoutController {

    @FXML private Label dbAuthBadge;
    @FXML private Label sessionInfoLabel;
    @FXML private VBox dbWarningBox;
    @FXML private Label dbWarningLabel;

    @FXML private Button navDashboard;
    @FXML private Button navDirectory;
    @FXML private Button navDossier;

    @FXML private VBox paneDashboard;
    @FXML private VBox paneDirectory;
    @FXML private VBox paneDossier;

    // Dashboard KPIs
    @FXML private Label dashScopeNotice;
    @FXML private Label kpiTotalUsers;
    @FXML private Label kpiCommunities;
    @FXML private Label kpiPosts;
    @FXML private Label kpiGeneralActivity;
    @FXML private Label kpiNew30Days;
    @FXML private Label kpiAuditEntries;

    // Directorio
    @FXML private ComboBox<String> dirReportFormatCombo;
    @FXML private Button btnGenerateFullReport;
    @FXML private TextField dirSearchField;
    @FXML private ComboBox<String> dirSortCombo;
    @FXML private Label dirStatusLabel;
    @FXML private TableView<ApiClient.StudentOverview> usersTable;
    @FXML private TableColumn<ApiClient.StudentOverview, String> colAlias;
    @FXML private TableColumn<ApiClient.StudentOverview, String> colLogin;
    @FXML private TableColumn<ApiClient.StudentOverview, String> colUniversity;
    @FXML private TableColumn<ApiClient.StudentOverview, String> colCareer;
    @FXML private TableColumn<ApiClient.StudentOverview, String> colRole;
    @FXML private TableColumn<ApiClient.StudentOverview, String> colCreated;
    @FXML private Button btnPrevPage;
    @FXML private Label pageLabel;
    @FXML private Button btnNextPage;
    @FXML private Button btnDeleteUser;
    @FXML private Label dirDeleteNoticeLabel;

    // Expediente
    @FXML private Label dosHeaderTitle;
    @FXML private Button btnExportDossierPdf;
    @FXML private Button btnExportDossierXlsx;
    @FXML private VBox dosEmptyBox;
    @FXML private Label dosEmptyLabel;
    @FXML private VBox dosErrorBox;
    @FXML private Label dosErrorTitle;
    @FXML private Label dosErrorLabel;
    @FXML private VBox dosContentBox;
    @FXML private Label dosAlias;
    @FXML private Label dosLogin;
    @FXML private Label dosEmail;
    @FXML private Label dosCreated;
    @FXML private Label dosRole;
    @FXML private Label dosUniversity;
    @FXML private Label dosCareer;
    @FXML private Label dosSemester;
    @FXML private Label dosBio;
    @FXML private Label dosInterests;
    @FXML private Label dosCommunities;
    @FXML private Label dosPosts;
    @FXML private Label dosExtendedActivity;
    @FXML private Label dosSanctions;
    @FXML private Label dosModDetail;
    @FXML private DatePicker dosRepFromDate;
    @FXML private DatePicker dosRepToDate;
    @FXML private ComboBox<String> dosRepFormatCombo;
    @FXML private Label dosRepStatusLabel;

    private ApiClient api;
    private SessionScope scope;
    private ExecutorService workers;
    private Runnable onLogout;

    private int currentPage = 0;
    private int totalPages = 1;
    private ApiClient.StudentOverview selectedStudent;
    private ApiClient.AdminStudentDossier activeDossier;

    @FXML
    public void initialize() {
        dirSortCombo.getItems().setAll("Más recientes", "Alias (A-Z)", "Login (A-Z)", "Universidad (A-Z)", "Más publicaciones");
        dirSortCombo.getSelectionModel().selectFirst();
        dirReportFormatCombo.getItems().setAll("PDF (.pdf)", "Excel (.xlsx)");
        dirReportFormatCombo.getSelectionModel().selectFirst();
        dosRepFormatCombo.getItems().setAll("PDF (.pdf)", "Excel (.xlsx)");
        dosRepFormatCombo.getSelectionModel().selectFirst();

        colAlias.setCellValueFactory(c -> new SimpleStringProperty(safe(c.getValue().alias())));
        colLogin.setCellValueFactory(c -> new SimpleStringProperty(safe(c.getValue().loginIdentifier())));
        colUniversity.setCellValueFactory(c -> new SimpleStringProperty(safe(c.getValue().declaredUniversity())));
        colCareer.setCellValueFactory(c -> new SimpleStringProperty(safe(c.getValue().declaredCareer())));
        colRole.setCellValueFactory(c -> new SimpleStringProperty(safe(c.getValue().globalRole())));
        colCreated.setCellValueFactory(c -> new SimpleStringProperty(UiHelper.formatDateShort(c.getValue().createdAt())));

        usersTable.getSelectionModel().selectedItemProperty().addListener((o, old, sel) -> {
            if (sel != null) {
                selectedStudent = sel;
                boolean canRemove = "USER".equalsIgnoreCase(sel.globalRole());
                btnDeleteUser.setDisable(!canRemove);
                dirDeleteNoticeLabel.setText("Cuenta seleccionada: " + sel.alias() + " (" + sel.loginIdentifier()
                        + "). Al pulsar Eliminar se mostrará una vista previa detallada de los datos personales que se eliminan y los contenidos compartidos que se conservan anónimamente.");
            } else {
                btnDeleteUser.setDisable(true);
            }
        });

        usersTable.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                var sel = usersTable.getSelectionModel().getSelectedItem();
                if (sel != null) {
                    selectedStudent = sel;
                    loadDossier(sel.accountId(), sel.loginIdentifier());
                }
            }
        });

        btnDeleteUser.setDisable(true);
        showDossierEmpty();
    }

    public void init(ApiClient api, SessionScope scope, ExecutorService workers,
                     ApiClient.AdminSessionView session, Runnable onLogout) {
        this.api = api;
        this.scope = scope;
        this.workers = workers;
        this.onLogout = onLogout;

        if (session != null) {
            applyCapabilities(session.capabilities());
            sessionInfoLabel.setText("Sesión administrativa · " + UiHelper.formatDate(session.authenticatedAt()));
        }
        showSection("DASHBOARD");
    }

    private void applyCapabilities(ApiClient.AdminCapabilities cap) {
        if (cap == null) return;
        if (cap.dbAuthorized()) {
            dbAuthBadge.setText("● PostgreSQL autorizado");
            dbAuthBadge.getStyleClass().setAll("badge-resolved");
            dbWarningLabel.setText(cap.limitationReason());
            UiHelper.setVisible(dbWarningBox, true);
            btnGenerateFullReport.setDisable(false);
        } else {
            dbAuthBadge.setText("● Acceso a datos no configurado");
            dbAuthBadge.getStyleClass().setAll("badge-danger");
            dbWarningLabel.setText(cap.limitationReason());
            UiHelper.setVisible(dbWarningBox, true);
            btnGenerateFullReport.setDisable(true);
            btnDeleteUser.setDisable(true);
        }
    }

    private void switchPane(String section) {
        for (Button b : List.of(navDashboard, navDirectory, navDossier)) {
            b.getStyleClass().removeAll("nav-item-active", "nav-item");
            b.getStyleClass().add("nav-item");
        }
        Button active = switch (section) {
            case "DIRECTORY" -> navDirectory;
            case "DOSSIER" -> navDossier;
            default -> navDashboard;
        };
        active.getStyleClass().remove("nav-item");
        active.getStyleClass().add("nav-item-active");

        UiHelper.setVisible(paneDashboard, "DASHBOARD".equals(section));
        UiHelper.setVisible(paneDirectory, "DIRECTORY".equals(section));
        UiHelper.setVisible(paneDossier, "DOSSIER".equals(section));
    }

    private void showSection(String section) {
        switchPane(section);
        switch (section) {
            case "DASHBOARD" -> loadDashboard();
            case "DIRECTORY" -> loadDirectory();
            case "DOSSIER" -> {
                if (selectedStudent != null
                        && (activeDossier == null || !selectedStudent.accountId().equals(activeDossier.accountId()))) {
                    loadDossier(selectedStudent.accountId(), selectedStudent.loginIdentifier());
                } else if (activeDossier == null) {
                    showDossierEmpty();
                }
            }
            default -> {}
        }
    }

    private void loadDashboard() {
        long epoch = scope.generation();
        workers.submit(() -> {
            try {
                var dash = api.adminDashboard();
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    applyCapabilities(dash.capabilities());
                    boolean partial = dash.partialWindow();
                    if (dashScopeNotice != null && dash.metricsScopeNotice() != null && !dash.metricsScopeNotice().isBlank()) {
                        dashScopeNotice.setText(dash.metricsScopeNotice());
                    }
                    String suffix = partial ? " (muestra parcial)" : "";
                    long generalActivity = dash.totalCommunitiesSum() + dash.totalPostsSum();
                    kpiTotalUsers.setText(dash.totalUsers() + suffix);
                    kpiCommunities.setText(dash.totalCommunitiesSum() + suffix);
                    kpiPosts.setText(dash.totalPostsSum() + suffix);
                    kpiGeneralActivity.setText(generalActivity + suffix);
                    kpiNew30Days.setText(dash.newRegistrations30Days() < 0
                            ? "Muestra parcial"
                            : String.valueOf(dash.newRegistrations30Days()));
                    kpiAuditEntries.setText(dash.auditWindowCapReached()
                            ? dash.recentAuditEntries() + "+"
                            : String.valueOf(dash.recentAuditEntries()));
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    if (dashScopeNotice != null) {
                        dashScopeNotice.setText("Error al consultar métricas reales: " + ex.getMessage());
                    }
                });
            }
        });
    }

    private void loadDirectory() {
        long epoch = scope.generation();
        String query = dirSearchField.getText();
        String sort = switch (dirSortCombo.getSelectionModel().getSelectedIndex()) {
            case 1 -> "ALIAS_ASC";
            case 2 -> "LOGIN_ASC";
            case 3 -> "UNIVERSITY_ASC";
            case 4 -> "POSTS_DESC";
            default -> "CREATED_DESC";
        };

        dirStatusLabel.setText("Consultando directorio autorizado…");
        workers.submit(() -> {
            try {
                var paged = api.adminUsers(query, "ALL", "ALL", sort, currentPage, 15);
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    applyCapabilities(paged.capabilities());
                    String prevSelectedId = selectedStudent != null ? selectedStudent.accountId() : null;
                    usersTable.getItems().setAll(paged.items());
                    if (prevSelectedId != null) {
                        for (int i = 0; i < paged.items().size(); i++) {
                            if (prevSelectedId.equals(paged.items().get(i).accountId())) {
                                usersTable.getSelectionModel().select(i);
                                break;
                            }
                        }
                    }
                    totalPages = Math.max(1, paged.totalPages());
                    pageLabel.setText(paged.v11WindowCapReached()
                            ? "Resultados disponibles · Bloque " + (paged.page() + 1) + " / " + totalPages
                            : "Bloque " + (paged.page() + 1) + " / " + totalPages);
                    btnPrevPage.setDisable(paged.page() <= 0);
                    btnNextPage.setDisable(paged.page() + 1 >= totalPages);
                    if (paged.v11WindowCapReached()) {
                        dirStatusLabel.setText("Mostrados: " + paged.items().size()
                                + " · Usuarios encontrados en los resultados disponibles: " + paged.totalItems() + ". "
                                + "La consulta actual tiene un alcance limitado. Utiliza el buscador por alias o login para localizar otros registros.");
                    } else {
                        dirStatusLabel.setText("Mostrados: " + paged.items().size()
                                + " · Usuarios encontrados: " + paged.totalItems() + ".");
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    dirStatusLabel.setText("No se pudo consultar el directorio: " + ex.getMessage());
                });
            }
        });
    }

    private void showDossierEmpty() {
        activeDossier = null;
        dosHeaderTitle.setText("Expediente individual");
        btnExportDossierPdf.setDisable(true);
        btnExportDossierXlsx.setDisable(true);
        UiHelper.setVisible(dosEmptyBox, true);
        UiHelper.setVisible(dosErrorBox, false);
        UiHelper.setVisible(dosContentBox, false);
    }

    private void showDossierStatus(String title, String message) {
        activeDossier = null;
        btnExportDossierPdf.setDisable(true);
        btnExportDossierXlsx.setDisable(true);
        dosErrorTitle.setText(title);
        dosErrorLabel.setText(message);
        UiHelper.setVisible(dosEmptyBox, false);
        UiHelper.setVisible(dosErrorBox, true);
        UiHelper.setVisible(dosContentBox, false);
    }

    private void loadDossier(String accountId, String searchHint) {
        if (accountId == null || accountId.isBlank()) {
            showDossierEmpty();
            return;
        }
        long epoch = scope.generation();
        switchPane("DOSSIER");
        showDossierStatus("CARGANDO EXPEDIENTE", "Consultando información de la cuenta seleccionada…");
        workers.submit(() -> {
            try {
                var d = api.adminDossier(accountId, searchHint);
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    activeDossier = d;
                    dosHeaderTitle.setText("Expediente: " + safe(d.alias()));
                    dosAlias.setText("Alias: " + safe(d.alias()));
                    dosLogin.setText("Login: " + safe(d.loginIdentifier()));
                    dosEmail.setText("Correo: " + safeNotice(d.emailNotice()));
                    dosCreated.setText("Fecha creación: " + (d.createdAt() != null ? UiHelper.formatDate(d.createdAt()) : "No disponible"));
                    dosRole.setText("Rol global: " + safe(d.globalRole()));
                    dosUniversity.setText("Universidad declarada: " + safe(d.declaredUniversity()));
                    dosCareer.setText("Carrera declarada: " + safe(d.declaredCareer()));
                    dosSemester.setText("Semestre: " + safeNotice(d.semesterNotice()));
                    dosBio.setText("Biografía: " + safeNotice(d.bioNotice()));
                    dosInterests.setText("Intereses: " + safeNotice(d.interestsNotice()));
                    dosCommunities.setText("Comunidades activas: " + d.communitiesCount());
                    dosPosts.setText("Publicaciones registradas: " + d.postsCount());
                    dosExtendedActivity.setText(safeNotice(d.extendedActivityNotice()));
                    dosSanctions.setText("Sanciones activas: " + d.activeSanctionsCount());
                    dosModDetail.setText(safeNotice(d.moderationDetailNotice()));
                    btnExportDossierPdf.setDisable(false);
                    btnExportDossierXlsx.setDisable(false);
                    UiHelper.setVisible(dosEmptyBox, false);
                    UiHelper.setVisible(dosErrorBox, false);
                    UiHelper.setVisible(dosContentBox, true);
                });
            } catch (ApiClient.Failure f) {
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    if (f.status() == 404) {
                        showDossierStatus("USUARIO NO ENCONTRADO",
                                "No se encontró la cuenta solicitada dentro de los resultados disponibles.");
                    } else {
                        showDossierStatus("ERROR AL CARGAR EXPEDIENTE",
                                "No se pudo consultar el expediente (HTTP " + f.status() + "): " + f.getMessage());
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    showDossierStatus("ERROR DE CONEXIÓN AL CARGAR EXPEDIENTE",
                            "No se pudo completar la consulta del expediente: " + ex.getMessage());
                });
            }
        });
    }

    @FXML
    private void onGenerateFullUsersReport() {
        boolean isXlsx = dirReportFormatCombo.getSelectionModel().getSelectedIndex() == 1;
        String format = isXlsx ? "XLSX" : "PDF";
        File target = chooseSaveFile("Guardar informe de usuarios",
                "steambeta_admin_informe_usuarios" + (isXlsx ? ".xlsx" : ".pdf"), isXlsx);
        if (target == null) return;

        String query = dirSearchField.getText();
        dirStatusLabel.setText("Generando informe de usuarios (" + format + ")…");
        btnGenerateFullReport.setDisable(true);
        workers.submit(() -> {
            try {
                byte[] bytes = api.downloadAdminFullUsersReport(format, query);
                Files.write(target.toPath(), bytes);
                Platform.runLater(() -> {
                    btnGenerateFullReport.setDisable(false);
                    dirStatusLabel.setText("Informe guardado: " + target.getName() + " (" + UiHelper.formatFileSize(bytes.length) + ").");
                    UiHelper.showSuccess(stage(), "Informe de usuarios generado", target.getAbsolutePath());
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    btnGenerateFullReport.setDisable(false);
                    dirStatusLabel.setText("No se pudo generar el informe: " + ex.getMessage());
                    UiHelper.showError(stage(), "Error al generar informe", ex.getMessage());
                });
            }
        });
    }

    private void exportDossier(boolean isXlsx) {
        if (activeDossier == null) {
            UiHelper.showError(stage(), "Sin expediente", "Selecciona primero un estudiante en el Directorio.");
            return;
        }
        File target = chooseSaveFile("Exportar expediente confidencial",
                "steambeta_admin_expediente_" + activeDossier.loginIdentifier() + (isXlsx ? ".xlsx" : ".pdf"), isXlsx);
        if (target == null) return;

        workers.submit(() -> {
            try {
                byte[] bytes = api.downloadAdminDossierReport(activeDossier.accountId(), isXlsx ? "XLSX" : "PDF", activeDossier.loginIdentifier());
                Files.write(target.toPath(), bytes);
                Platform.runLater(() -> UiHelper.showSuccess(stage(), "Expediente exportado", target.getAbsolutePath()));
            } catch (Exception ex) {
                Platform.runLater(() -> UiHelper.showError(stage(), "Error al exportar expediente", ex.getMessage()));
            }
        });
    }

    private void exportStudentPersonalReport(String kind, String filePrefix) {
        if (activeDossier == null) {
            UiHelper.showError(stage(), "Sin estudiante seleccionado", "Selecciona primero un estudiante en el Directorio.");
            return;
        }
        LocalDate from = dosRepFromDate.getValue();
        LocalDate to = dosRepToDate.getValue();
        if (from != null && to != null && from.isAfter(to)) {
            dosRepStatusLabel.setText("La fecha inicial no puede ser posterior a la final.");
            return;
        }
        boolean isXlsx = dosRepFormatCombo.getSelectionModel().getSelectedIndex() == 1;
        String format = isXlsx ? "XLSX" : "PDF";
        String slug = activeDossier.loginIdentifier() != null ? activeDossier.loginIdentifier().replaceAll("[^a-zA-Z0-9._-]", "_") : "estudiante";
        File target = chooseSaveFile("Guardar reporte del estudiante",
                "steambeta_admin_" + filePrefix + "_" + slug + (isXlsx ? ".xlsx" : ".pdf"), isXlsx);
        if (target == null) return;

        dosRepStatusLabel.setText("Generando reporte de " + activeDossier.alias() + "…");
        String targetId = activeDossier.accountId();
        String hint = activeDossier.loginIdentifier();
        workers.submit(() -> {
            try {
                byte[] bytes = api.downloadAdminStudentPersonalReport(targetId, kind, format,
                        from != null ? from.toString() : null, to != null ? to.toString() : null, hint);
                Files.write(target.toPath(), bytes);
                Platform.runLater(() -> {
                    dosRepStatusLabel.setText("Reporte guardado: " + target.getName() + " (" + UiHelper.formatFileSize(bytes.length) + ").");
                    UiHelper.showSuccess(stage(), "Reporte del estudiante generado", target.getAbsolutePath());
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    dosRepStatusLabel.setText("No se pudo generar el reporte: " + ex.getMessage());
                    UiHelper.showError(stage(), "Error al generar reporte", ex.getMessage());
                });
            }
        });
    }

    private File chooseSaveFile(String title, String initialName, boolean isXlsx) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.setInitialFileName(initialName);
        chooser.getExtensionFilters().add(isXlsx
                ? new FileChooser.ExtensionFilter("Libro Excel (*.xlsx)", "*.xlsx")
                : new FileChooser.ExtensionFilter("Documento PDF (*.pdf)", "*.pdf"));
        return chooser.showSaveDialog(stage());
    }

    private Stage stage() {
        return dbAuthBadge.getScene() != null ? (Stage) dbAuthBadge.getScene().getWindow() : null;
    }

    private static String safe(String s) { return s != null && !s.isBlank() ? s : "—"; }
    private static String safeNotice(String s) { return (s != null && !s.isBlank()) ? s : "Información no disponible para esta consulta"; }

    @FXML private void onNavDashboard() { showSection("DASHBOARD"); }
    @FXML private void onNavDirectory() { currentPage = 0; showSection("DIRECTORY"); }
    @FXML private void onBackToDirectory() { showSection("DIRECTORY"); }
    @FXML private void onNavDossier() { showSection("DOSSIER"); }
    @FXML private void onRefreshDashboard() { loadDashboard(); }
    @FXML private void onSearchDirectory() { currentPage = 0; loadDirectory(); }
    @FXML private void onPrevPage() { if (currentPage > 0) { currentPage--; loadDirectory(); } }
    @FXML private void onNextPage() { if (currentPage + 1 < totalPages) { currentPage++; loadDirectory(); } }
    @FXML private void onExportDossierPdf() { exportDossier(false); }
    @FXML private void onExportDossierXlsx() { exportDossier(true); }
    @FXML private void onDosRepActivity() { exportStudentPersonalReport("academic-activity", "actividad"); }
    @FXML private void onDosRepCommunities() { exportStudentPersonalReport("communities", "comunidades"); }
    @FXML private void onDosRepPlanning() { exportStudentPersonalReport("academic-planning", "planificacion"); }
    @FXML private void onDosRepExchange() { exportStudentPersonalReport("exchange-publications", "intercambio"); }

    @FXML
    private void onOpenSelectedDossier() {
        var sel = usersTable.getSelectionModel().getSelectedItem();
        if (sel == null) {
            UiHelper.showError(stage(), "Selecciona un usuario", "Elige una cuenta de la tabla para abrir su expediente.");
            return;
        }
        selectedStudent = sel;
        loadDossier(sel.accountId(), sel.loginIdentifier());
    }

    @FXML
    private void onDeleteSelectedUser() {
        var sel = usersTable.getSelectionModel().getSelectedItem();
        if (sel == null) {
            UiHelper.showError(stage(), "Selecciona un estudiante", "Elige una cuenta de la tabla para consultar su vista previa de eliminación.");
            return;
        }
        if (!"USER".equalsIgnoreCase(sel.globalRole())) {
            UiHelper.showError(stage(), "Operación no permitida", "Solo se permite eliminar cuentas estudiantiles.");
            return;
        }
        btnDeleteUser.setDisable(true);
        dirStatusLabel.setText("Consultando vista previa de eliminación para " + sel.alias() + "…");
        String targetId = sel.accountId();
        workers.submit(() -> {
            try {
                var preview = api.adminRemovalPreview(targetId);
                Platform.runLater(() -> {
                    btnDeleteUser.setDisable(false);
                    var loader = ViewLoader.<ConfirmStudentRemovalDialogController>load("ConfirmStudentRemovalDialog.fxml");
                    loader.controller().setPreview(preview);
                    var modal = UiHelper.createModalStage(loader.root(), "Eliminar definitivamente al estudiante", stage());
                    modal.showAndWait();

                    if (!loader.controller().isConfirmed()) {
                        dirStatusLabel.setText("Operación cancelada por el administrador.");
                        return;
                    }
                    String confirmText = loader.controller().getConfirmationText();
                    String reason = loader.controller().getReason();
                    btnDeleteUser.setDisable(true);
                    dirStatusLabel.setText("Ejecutando eliminación física definitiva de la cuenta…");
                    workers.submit(() -> {
                        try {
                            var result = api.adminDeleteStudent(targetId, confirmText, reason);
                            Platform.runLater(() -> {
                                selectedStudent = null;
                                if (activeDossier != null && targetId.equals(activeDossier.accountId())) {
                                    showDossierEmpty();
                                }
                                String summary = (result.summaryMessage() != null && !result.summaryMessage().isBlank())
                                        ? result.summaryMessage()
                                        : "Cuenta eliminada físicamente.";
                                dirStatusLabel.setText(summary);
                                UiHelper.showSuccess(stage(), "Cuenta eliminada físicamente", summary);
                                loadDirectory();
                            });
                        } catch (Exception ex) {
                            Platform.runLater(() -> {
                                btnDeleteUser.setDisable(false);
                                dirStatusLabel.setText("No se pudo completar la eliminación física: " + ex.getMessage());
                                UiHelper.showError(stage(), "Error en eliminación definitiva", ex.getMessage());
                            });
                        }
                    });
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    btnDeleteUser.setDisable(false);
                    dirStatusLabel.setText("No se pudo cargar la vista previa: " + ex.getMessage());
                    UiHelper.showError(stage(), "Vista previa no disponible", ex.getMessage());
                });
            }
        });
    }

    @FXML
    private void onLogout() {
        if (onLogout != null) onLogout.run();
    }
}
