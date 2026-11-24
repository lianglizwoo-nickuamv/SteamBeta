package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import com.example.steambeta.client.SessionScope;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.*;
import java.util.concurrent.ExecutorService;

/**
 * Controlador de Biblioteca Universitaria (FXML) — SteamBeta Campus:
 * Combina materias institucionales (cuando existen) con materias registradas manualmente
 * por el propio estudiante, diferenciando claramente su origen y permitiendo CRUD privado
 * sobre las materias manuales.
 */
public final class AcademicLibraryController {

    private record LibraryEntry(ApiClient.Subject institutional, ApiClient.ManualSubject manual) {
        boolean isManual() { return manual != null; }
        String displayName() { return isManual() ? manual.name() : institutional.name(); }
        String yearText() {
            if (isManual()) return String.valueOf(manual.academicYear());
            String tc = institutional.termCode();
            if (tc != null && tc.length() >= 4 && tc.substring(0, 4).chars().allMatch(Character::isDigit)) {
                return tc.substring(0, 4);
            }
            return "";
        }
        String termText() {
            return isManual() ? manual.termLabel() : (institutional.termCode() != null ? institutional.termCode() : "");
        }
        String uniqueKey() {
            return isManual() ? "M:" + manual.id() : "I:" + institutional.offeringId();
        }
    }

    private static final String ALL_YEARS = "Todos los años";
    private static final String ALL_TERMS = "Todos los períodos";

    @FXML private VBox emptyContainer;
    @FXML private HBox librarySplitBox;
    @FXML private ComboBox<String> yearFilterCombo;
    @FXML private ComboBox<String> termFilterCombo;
    @FXML private ListView<LibraryEntry> subjectsList;
    @FXML private Label libraryStatus;
    @FXML private VBox detailPane;
    @FXML private Label detailStatusLabel;
    @FXML private VBox detailContentBox;
    @FXML private Label subjectCodeLabel;
    @FXML private Label subjectSourceBadge;
    @FXML private Button btnEditManual;
    @FXML private Button btnDeleteManual;
    @FXML private Label subjectNameLabel;
    @FXML private Label subjectMetaLabel;
    @FXML private VBox institutionalDetailBox;
    @FXML private Label progressPercentLabel;
    @FXML private ProgressBar progressBar;
    @FXML private Label progressCountsLabel;
    @FXML private VBox activitiesListBox;
    @FXML private VBox manualDetailBox;
    @FXML private Label manualPeriodLabel;
    @FXML private Label manualGroupLabel;
    @FXML private Label manualTeacherLabel;
    @FXML private Label manualCreditsLabel;
    @FXML private Label manualScheduleLabel;
    @FXML private Label manualNotesLabel;

    private ApiClient api;
    private SessionScope scope;
    private ExecutorService workers;
    private final List<LibraryEntry> allEntries = new ArrayList<>();
    private boolean updatingFilters;

    public void init(ApiClient api, SessionScope scope, ExecutorService workers) {
        this.api = api;
        this.scope = scope;
        this.workers = workers;

        setupList();
        setupFilters();
        loadLibrary(null);
    }

    private void setupFilters() {
        yearFilterCombo.setItems(FXCollections.observableArrayList(ALL_YEARS));
        yearFilterCombo.setValue(ALL_YEARS);
        termFilterCombo.setItems(FXCollections.observableArrayList(ALL_TERMS));
        termFilterCombo.setValue(ALL_TERMS);

        yearFilterCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (!updatingFilters) applyFilters(null);
        });
        termFilterCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (!updatingFilters) applyFilters(null);
        });
    }

    private void setupList() {
        subjectsList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(LibraryEntry entry, boolean empty) {
                super.updateItem(entry, empty);
                setText(null);
                if (empty || entry == null) {
                    setGraphic(null);
                    return;
                }
                var cell = ViewLoader.<SubjectCellController>load("SubjectCell.fxml");
                if (entry.isManual()) {
                    cell.controller().setManual(entry.manual());
                } else {
                    cell.controller().set(entry.institutional());
                }
                setGraphic(cell.root());
            }
        });

        subjectsList.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected != null) {
                if (selected.isManual()) {
                    showManualDetail(selected.manual());
                } else {
                    loadInstitutionalDetail(selected.institutional());
                }
            }
        });
    }

    private void loadLibrary(String selectKey) {
        long epoch = scope.generation();
        libraryStatus.setText("Consultando materias institucionales y manuales…");

        workers.submit(() -> {
            List<ApiClient.Subject> instRows = List.of();
            List<ApiClient.ManualSubject> manualRows = List.of();
            String errorMsg = null;

            try {
                instRows = api.library();
            } catch (Exception ignored) {
                // Sin vinculación institucional activa: continuamos cargando materias manuales
            }

            try {
                manualRows = api.manualSubjects(null, null);
            } catch (Exception err) {
                errorMsg = err.getMessage() != null ? err.getMessage() : "No se pudieron cargar las materias manuales.";
            }

            final List<ApiClient.Subject> finalInst = instRows;
            final List<ApiClient.ManualSubject> finalManual = manualRows;
            final String finalErr = errorMsg;

            Platform.runLater(() -> {
                if (!scope.current(epoch)) return;
                allEntries.clear();
                for (var s : finalInst) allEntries.add(new LibraryEntry(s, null));
                for (var m : finalManual) allEntries.add(new LibraryEntry(null, m));

                refreshFilterOptions();

                if (allEntries.isEmpty()) {
                    String subtitle = finalErr != null
                            ? "Todavía no tenés materias registradas. Podés agregarlas manualmente para organizar tus actividades académicas. (" + finalErr + ")"
                            : "Todavía no tenés materias registradas. Podés agregarlas manualmente para organizar tus actividades académicas.";
                    showCompactEmptyState("📚", "Biblioteca Universitaria sin materias", subtitle);
                } else {
                    showLibraryContent();
                    applyFilters(selectKey);
                }
            });
        });
    }

    private void refreshFilterOptions() {
        updatingFilters = true;
        try {
            String prevYear = yearFilterCombo.getValue();
            String prevTerm = termFilterCombo.getValue();

            Set<String> years = new TreeSet<>(Comparator.reverseOrder());
            Set<String> terms = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            for (LibraryEntry e : allEntries) {
                if (!e.yearText().isBlank()) years.add(e.yearText());
                if (!e.termText().isBlank()) terms.add(e.termText());
            }

            List<String> yearItems = new ArrayList<>();
            yearItems.add(ALL_YEARS);
            yearItems.addAll(years);
            yearFilterCombo.setItems(FXCollections.observableArrayList(yearItems));
            yearFilterCombo.setValue(yearItems.contains(prevYear) ? prevYear : ALL_YEARS);

            List<String> termItems = new ArrayList<>();
            termItems.add(ALL_TERMS);
            termItems.addAll(terms);
            termFilterCombo.setItems(FXCollections.observableArrayList(termItems));
            termFilterCombo.setValue(termItems.contains(prevTerm) ? prevTerm : ALL_TERMS);
        } finally {
            updatingFilters = false;
        }
    }

    private void applyFilters(String selectKey) {
        String selectedYear = yearFilterCombo.getValue();
        String selectedTerm = termFilterCombo.getValue();

        List<LibraryEntry> filtered = allEntries.stream()
                .filter(e -> selectedYear == null || ALL_YEARS.equals(selectedYear) || selectedYear.equals(e.yearText()))
                .filter(e -> selectedTerm == null || ALL_TERMS.equals(selectedTerm) || selectedTerm.equalsIgnoreCase(e.termText()))
                .toList();

        subjectsList.getItems().setAll(filtered);

        long instCount = filtered.stream().filter(e -> !e.isManual()).count();
        long manualCount = filtered.stream().filter(LibraryEntry::isManual).count();
        libraryStatus.setText(filtered.size() + " materia(s) (" + instCount + " institucional(es) · " + manualCount + " manual(es))");

        if (filtered.isEmpty()) {
            detailContentBox.setVisible(false);
            detailContentBox.setManaged(false);
            detailStatusLabel.setVisible(true);
            detailStatusLabel.setManaged(true);
            detailStatusLabel.setText("No hay materias que coincidan con el filtro de año/período.");
            return;
        }

        LibraryEntry toSelect = filtered.getFirst();
        if (selectKey != null) {
            for (LibraryEntry e : filtered) {
                if (selectKey.equals(e.uniqueKey())) {
                    toSelect = e;
                    break;
                }
            }
        }
        subjectsList.getSelectionModel().select(toSelect);
    }

    private void showCompactEmptyState(String icon, String title, String description) {
        if (librarySplitBox != null) {
            librarySplitBox.setVisible(false);
            librarySplitBox.setManaged(false);
        }
        if (emptyContainer != null) {
            var empty = ViewLoader.<EmptyStateController>load("EmptyState.fxml");
            empty.controller().set(icon, title, description, "+ Agregar materia", this::onAddManualSubject);
            emptyContainer.getChildren().setAll(empty.root());
            emptyContainer.setVisible(true);
            emptyContainer.setManaged(true);
        }
    }

    private void showLibraryContent() {
        if (emptyContainer != null) {
            emptyContainer.setVisible(false);
            emptyContainer.setManaged(false);
        }
        if (librarySplitBox != null) {
            librarySplitBox.setVisible(true);
            librarySplitBox.setManaged(true);
        }
    }

    private void showManualDetail(ApiClient.ManualSubject m) {
        detailStatusLabel.setVisible(false);
        detailStatusLabel.setManaged(false);
        detailContentBox.setVisible(true);
        detailContentBox.setManaged(true);

        institutionalDetailBox.setVisible(false);
        institutionalDetailBox.setManaged(false);
        manualDetailBox.setVisible(true);
        manualDetailBox.setManaged(true);

        btnEditManual.setVisible(true);
        btnEditManual.setManaged(true);
        btnDeleteManual.setVisible(true);
        btnDeleteManual.setManaged(true);

        subjectCodeLabel.setText(m.code() != null && !m.code().isBlank() ? m.code() : "REGISTRO PERSONAL");
        subjectSourceBadge.setText("Registrada manualmente");
        subjectSourceBadge.getStyleClass().setAll("badge-role");
        subjectNameLabel.setText(m.name());
        subjectMetaLabel.setText("Año académico: " + m.academicYear() + " · Período: " + m.termLabel()
                + (m.groupSection() != null && !m.groupSection().isBlank() ? " · Grupo: " + m.groupSection() : ""));

        manualPeriodLabel.setText("Período académico: " + m.academicYear() + " — " + m.termLabel());
        manualGroupLabel.setText("Grupo / Sección: " + displayOrDash(m.groupSection()));
        manualTeacherLabel.setText("Docente: " + displayOrDash(m.teacherName()));
        manualCreditsLabel.setText("Créditos: " + (m.credits() != null ? String.valueOf(m.credits()) : "No especificado"));
        manualScheduleLabel.setText("Horario: " + displayOrDash(m.schedule()));
        manualNotesLabel.setText(m.notes() != null && !m.notes().isBlank() ? m.notes() : "Sin descripción ni notas personales registradas.");
    }

    private void loadInstitutionalDetail(ApiClient.Subject subject) {
        long epoch = scope.generation();
        detailStatusLabel.setVisible(true);
        detailStatusLabel.setManaged(true);
        detailStatusLabel.setText("Cargando actividades de " + subject.name() + "…");
        detailContentBox.setVisible(false);
        detailContentBox.setManaged(false);

        btnEditManual.setVisible(false);
        btnEditManual.setManaged(false);
        btnDeleteManual.setVisible(false);
        btnDeleteManual.setManaged(false);

        workers.submit(() -> {
            try {
                var data = api.detail(subject.offeringId());
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    var summary = data.summary();
                    String percent = summary.progressPercent() == null ? "Sin requisitos"
                            : summary.progressPercent().stripTrailingZeros().toPlainString() + "%";
                    double progressVal = summary.progressPercent() == null ? 0 : summary.progressPercent().doubleValue() / 100;

                    subjectCodeLabel.setText(summary.code());
                    subjectSourceBadge.setText("Institucional");
                    subjectSourceBadge.getStyleClass().setAll("badge-admin");
                    subjectNameLabel.setText(summary.name());
                    subjectMetaLabel.setText("Período: " + summary.termCode() + " · Sección: " + summary.sectionCode());

                    manualDetailBox.setVisible(false);
                    manualDetailBox.setManaged(false);
                    institutionalDetailBox.setVisible(true);
                    institutionalDetailBox.setManaged(true);

                    progressPercentLabel.setText(percent);
                    progressBar.setProgress(progressVal);
                    progressCountsLabel.setText(summary.completedCount() + " de " + summary.requiredCount() + " actividades obligatorias completadas");

                    activitiesListBox.getChildren().clear();
                    for (var act : data.activities()) {
                        var actCard = ViewLoader.<ActivityCardController>load("ActivityCard.fxml");
                        actCard.controller().set(act);
                        activitiesListBox.getChildren().add(actCard.root());
                    }

                    detailStatusLabel.setVisible(false);
                    detailStatusLabel.setManaged(false);
                    detailContentBox.setVisible(true);
                    detailContentBox.setManaged(true);
                });
            } catch (Exception err) {
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    detailStatusLabel.setText("Error al cargar el detalle institucional: " + err.getMessage());
                });
            }
        });
    }

    @FXML
    private void onAddManualSubject() {
        var view = ViewLoader.<RegisterSubjectDialogController>load("RegisterSubjectDialog.fxml");
        view.controller().init(api, workers, saved -> {
            yearFilterCombo.setValue(ALL_YEARS);
            termFilterCombo.setValue(ALL_TERMS);
            loadLibrary("M:" + saved.id());
        });
        UiHelper.createModalStage(view.root(), "Registrar Materia Manualmente", ownerStage()).showAndWait();
    }

    @FXML
    private void onEditManualSubject() {
        LibraryEntry selected = subjectsList.getSelectionModel().getSelectedItem();
        if (selected == null || !selected.isManual()) return;

        var view = ViewLoader.<RegisterSubjectDialogController>load("RegisterSubjectDialog.fxml");
        view.controller().initForEdit(api, workers, selected.manual(), saved -> loadLibrary("M:" + saved.id()));
        UiHelper.createModalStage(view.root(), "Editar Materia Manual", ownerStage()).showAndWait();
    }

    @FXML
    private void onDeleteManualSubject() {
        LibraryEntry selected = subjectsList.getSelectionModel().getSelectedItem();
        if (selected == null || !selected.isManual()) return;

        ApiClient.ManualSubject target = selected.manual();
        boolean confirmed = UiHelper.confirm(
                "Eliminar materia manual",
                "¿Deseas eliminar «" + target.name() + "» (" + target.academicYear() + " · " + target.termLabel() + ") de tu Biblioteca personal?");
        if (!confirmed) return;

        btnDeleteManual.setDisable(true);
        workers.submit(() -> {
            try {
                api.deleteManualSubject(target.id());
                Platform.runLater(() -> {
                    btnDeleteManual.setDisable(false);
                    loadLibrary(null);
                });
            } catch (Exception err) {
                Platform.runLater(() -> {
                    btnDeleteManual.setDisable(false);
                    UiHelper.showError(ownerStage(), "No se pudo eliminar la materia",
                            err.getMessage() != null ? err.getMessage() : "Error al eliminar la materia.");
                });
            }
        });
    }

    @FXML
    private void onRefresh() {
        LibraryEntry selected = subjectsList.getSelectionModel().getSelectedItem();
        loadLibrary(selected != null ? selected.uniqueKey() : null);
    }

    private Stage ownerStage() {
        if (librarySplitBox != null && librarySplitBox.getScene() != null && librarySplitBox.getScene().getWindow() instanceof Stage s) {
            return s;
        }
        if (emptyContainer != null && emptyContainer.getScene() != null && emptyContainer.getScene().getWindow() instanceof Stage s) {
            return s;
        }
        return null;
    }

    private static String displayOrDash(String value) {
        return value != null && !value.isBlank() ? value : "No especificado";
    }
}
