package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import com.example.steambeta.client.SessionScope;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

/**
 * Controlador de Comunidades Universitarias (FXML) — SteamBeta Campus:
 * Separa claramente dos vistas:
 * 1. MIS COMUNIDADES: espacios en los que participa el estudiante autenticado.
 * 2. EXPLORAR COMUNIDADES: catálogo global de comunidades públicas y solo con invitación
 *    con filtros combinables de categoría, acceso, disponibilidad y ordenamiento.
 */
public final class CommunitiesController {

    private static final String ACCESS_ALL = "Todos";
    private static final String ACCESS_PUBLIC = "Públicas";
    private static final String ACCESS_INVITE = "Requieren invitación";

    private static final String AVAIL_ALL = "Todas";
    private static final String AVAIL_OPEN = "Con espacios disponibles";
    private static final String AVAIL_FULL = "Llenas";

    private static final String SORT_NAME = "Nombre";
    private static final String SORT_MOST_MEMBERS = "Mayor cantidad de integrantes";
    private static final String SORT_LEAST_MEMBERS = "Menor cantidad de integrantes";
    private static final String SORT_MOST_SLOTS = "Mayor disponibilidad";

    @FXML private Button tabMyCommunitiesBtn;
    @FXML private Button tabExploreBtn;

    @FXML private TextField searchField;
    @FXML private Button catAllBtn;
    @FXML private Button catAcadBtn;
    @FXML private Button catStudyBtn;
    @FXML private Button catProjBtn;
    @FXML private Button catCareerBtn;
    @FXML private Button catInterestBtn;

    @FXML private HBox catalogFiltersBar;
    @FXML private ComboBox<String> accessFilterCombo;
    @FXML private ComboBox<String> availabilityFilterCombo;
    @FXML private ComboBox<String> sortCombo;
    @FXML private Button clearFiltersBtn;

    @FXML private Label statusLabel;
    @FXML private VBox communityListBox;

    private ApiClient api;
    private SessionScope scope;
    private ExecutorService workers;
    private MainLayoutController navigator;

    private String activeView = "MINE"; // "MINE" o "EXPLORE"
    private List<ApiClient.Community> myCommunities = new ArrayList<>();
    private List<ApiClient.CommunityOverview> exploreCatalog = new ArrayList<>();
    private String selectedCategory = null;
    private boolean suppressFilterEvents = false;

    @FXML
    public void initialize() {
        suppressFilterEvents = true;
        if (accessFilterCombo != null) {
            accessFilterCombo.getItems().setAll(ACCESS_ALL, ACCESS_PUBLIC, ACCESS_INVITE);
            accessFilterCombo.setValue(ACCESS_ALL);
        }
        if (availabilityFilterCombo != null) {
            availabilityFilterCombo.getItems().setAll(AVAIL_ALL, AVAIL_OPEN, AVAIL_FULL);
            availabilityFilterCombo.setValue(AVAIL_ALL);
        }
        if (sortCombo != null) {
            sortCombo.getItems().setAll(SORT_NAME, SORT_MOST_MEMBERS, SORT_LEAST_MEMBERS, SORT_MOST_SLOTS);
            sortCombo.setValue(SORT_NAME);
        }
        suppressFilterEvents = false;
    }

    public void init(ApiClient api, SessionScope scope, ExecutorService workers, MainLayoutController navigator) {
        this.api = api;
        this.scope = scope;
        this.workers = workers;
        this.navigator = navigator;

        updateCatalogFiltersVisibility();
        loadMyCommunities();
    }

    private void updateCatalogFiltersVisibility() {
        boolean isExplore = "EXPLORE".equals(activeView);
        if (catalogFiltersBar != null) {
            catalogFiltersBar.setVisible(isExplore);
            catalogFiltersBar.setManaged(isExplore);
        }
    }

    // =========================================================================
    // PESTAÑA 1: MIS COMUNIDADES
    // =========================================================================

    private void loadMyCommunities() {
        long epoch = scope.generation();
        statusLabel.setText("Cargando tus comunidades desde PostgreSQL...");

        workers.submit(() -> {
            try {
                var list = api.communities();
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    this.myCommunities = list != null ? list : new ArrayList<>();
                    if ("MINE".equals(activeView)) {
                        renderMyCommunities();
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    if (!"MINE".equals(activeView)) return;
                    String msg = e.getMessage() != null && !e.getMessage().isBlank() ? e.getMessage() : "Error de conexión.";
                    statusLabel.setText("Error al consultar tus comunidades: " + msg);
                    communityListBox.getChildren().clear();
                    var empty = ViewLoader.<EmptyStateController>load("EmptyState.fxml");
                    empty.controller().set("✕", "Error al consultar tus comunidades", msg, "Reintentar", this::loadMyCommunities);
                    communityListBox.getChildren().add(empty.root());
                });
            }
        });
    }

    private void renderMyCommunities() {
        communityListBox.getChildren().clear();
        String query = searchField.getText() != null ? searchField.getText().strip().toLowerCase(Locale.ROOT) : "";

        var filtered = myCommunities.stream()
                .filter(c -> selectedCategory == null
                        || (c.category() != null && selectedCategory.equalsIgnoreCase(c.category())))
                .filter(c -> query.isBlank()
                        || (c.name() != null && c.name().toLowerCase(Locale.ROOT).contains(query))
                        || (c.description() != null && c.description().toLowerCase(Locale.ROOT).contains(query))
                        || (c.slug() != null && c.slug().toLowerCase(Locale.ROOT).contains(query)))
                .toList();

        if (filtered.isEmpty()) {
            if (myCommunities.isEmpty()) {
                statusLabel.setText("Aún no perteneces a ninguna comunidad universitaria.");
                var empty = ViewLoader.<EmptyStateController>load("EmptyState.fxml");
                empty.controller().set("●", "Aún no te has unido a ninguna comunidad",
                        "Explora el catálogo global de comunidades públicas o crea un nuevo espacio de estudio.",
                        "Explorar Comunidades", this::onTabExplore);
                communityListBox.getChildren().add(empty.root());
            } else {
                statusLabel.setText("Sin coincidencias en tus comunidades para la búsqueda actual.");
                var empty = ViewLoader.<EmptyStateController>load("EmptyState.fxml");
                empty.controller().set("◆", "Sin coincidencias",
                        "Ninguna de tus comunidades coincide con el filtro ingresado.",
                        "Limpiar filtros", this::onClearFilters);
                communityListBox.getChildren().add(empty.root());
            }
        } else {
            statusLabel.setText("Mis Comunidades · Perteneces a " + filtered.size() + " espacio(s):");
            for (var comm : filtered) {
                var card = ViewLoader.<CommunityCardController>load("CommunityCard.fxml");
                card.controller().set(comm, c -> {
                    if (navigator != null) navigator.openCommunityDetail(c);
                });
                communityListBox.getChildren().add(card.root());
            }
        }
    }

    // =========================================================================
    // PESTAÑA 2: EXPLORAR COMUNIDADES (CATÁLOGO GLOBAL V14 + FILTROS LOCALES)
    // =========================================================================

    private void loadExploreCatalog() {
        long epoch = scope.generation();
        statusLabel.setText("Consultando catálogo global de comunidades en el servidor...");

        workers.submit(() -> {
            try {
                // Obtiene el catálogo completo V14 (solo PUBLIC e INVITE_ONLY; nunca PRIVATE)
                // y aplica todos los filtros de manera local e instantánea.
                var list = api.exploreCommunities(null, null);
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    this.exploreCatalog = list != null ? list : new ArrayList<>();
                    if ("EXPLORE".equals(activeView)) {
                        renderExploreCatalog();
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    if (!"EXPLORE".equals(activeView)) return;
                    String msg = e.getMessage() != null && !e.getMessage().isBlank() ? e.getMessage() : "Error al consultar el catálogo.";
                    statusLabel.setText("Error al consultar el catálogo de comunidades: " + msg);
                    communityListBox.getChildren().clear();
                    var empty = ViewLoader.<EmptyStateController>load("EmptyState.fxml");
                    empty.controller().set("✕", "Error al consultar el catálogo", msg, "Reintentar", this::loadExploreCatalog);
                    communityListBox.getChildren().add(empty.root());
                });
            }
        });
    }

    private void renderExploreCatalog() {
        communityListBox.getChildren().clear();

        String query = searchField.getText() != null ? searchField.getText().strip().toLowerCase(Locale.ROOT) : "";
        String access = accessFilterCombo != null && accessFilterCombo.getValue() != null
                ? accessFilterCombo.getValue() : ACCESS_ALL;
        String availability = availabilityFilterCombo != null && availabilityFilterCombo.getValue() != null
                ? availabilityFilterCombo.getValue() : AVAIL_ALL;
        String sort = sortCombo != null && sortCombo.getValue() != null
                ? sortCombo.getValue() : SORT_NAME;

        Comparator<ApiClient.CommunityOverview> comparator = switch (sort) {
            case SORT_MOST_MEMBERS -> Comparator.comparingLong(ApiClient.CommunityOverview::memberCount).reversed()
                    .thenComparing(c -> safeLower(c.name()));
            case SORT_LEAST_MEMBERS -> Comparator.comparingLong(ApiClient.CommunityOverview::memberCount)
                    .thenComparing(c -> safeLower(c.name()));
            case SORT_MOST_SLOTS -> Comparator.comparingInt(ApiClient.CommunityOverview::availableSlots).reversed()
                    .thenComparing(c -> safeLower(c.name()));
            default -> Comparator.comparing(c -> safeLower(c.name()));
        };

        // Garantizar siempre que PRIVATE jamás aparezca en el catálogo público
        var visible = exploreCatalog.stream()
                .filter(c -> "PUBLIC".equalsIgnoreCase(c.visibility()) || "INVITE_ONLY".equalsIgnoreCase(c.visibility()))
                .filter(c -> selectedCategory == null
                        || (c.category() != null && selectedCategory.equalsIgnoreCase(c.category())))
                .filter(c -> switch (access) {
                    case ACCESS_PUBLIC -> "PUBLIC".equalsIgnoreCase(c.visibility());
                    case ACCESS_INVITE -> "INVITE_ONLY".equalsIgnoreCase(c.visibility());
                    default -> true;
                })
                .filter(c -> switch (availability) {
                    case AVAIL_OPEN -> c.availableSlots() > 0;
                    case AVAIL_FULL -> c.availableSlots() <= 0;
                    default -> true;
                })
                .filter(c -> query.isBlank()
                        || safeLower(c.name()).contains(query)
                        || safeLower(c.description()).contains(query)
                        || safeLower(c.slug()).contains(query)
                        || safeLower(c.category()).contains(query))
                .sorted(comparator)
                .toList();

        boolean hasFilters = !query.isBlank()
                || selectedCategory != null
                || !ACCESS_ALL.equals(access)
                || !AVAIL_ALL.equals(availability)
                || !SORT_NAME.equals(sort);

        if (visible.isEmpty()) {
            statusLabel.setText(hasFilters
                    ? "Sin coincidencias en el catálogo global para los filtros combinados."
                    : "No hay comunidades públicas ni por invitación registradas aún en el campus.");
            var empty = ViewLoader.<EmptyStateController>load("EmptyState.fxml");
            empty.controller().set(hasFilters ? "◆" : "●",
                    hasFilters ? "Sin coincidencias" : "Catálogo sin comunidades disponibles",
                    hasFilters
                            ? "Prueba con otros criterios o pulsa 'Limpiar filtros' para ver el catálogo completo."
                            : "Sé el primero en crear una comunidad pública para otros estudiantes.",
                    hasFilters ? "Limpiar filtros" : "+ Crear Comunidad",
                    hasFilters ? this::onClearFilters : this::onCreateCommunity);
            communityListBox.getChildren().add(empty.root());
            return;
        }

        statusLabel.setText("Explorar Comunidades · Mostrando " + visible.size() + " de "
                + exploreCatalog.stream().filter(c -> "PUBLIC".equalsIgnoreCase(c.visibility()) || "INVITE_ONLY".equalsIgnoreCase(c.visibility())).count()
                + " comunidad(es) disponible(s):");
        for (var ov : visible) {
            var card = ViewLoader.<CommunityOverviewCardController>load("CommunityOverviewCard.fxml");
            card.controller().set(ov, this::handleOverviewAction);
            communityListBox.getChildren().add(card.root());
        }
    }

    private static String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private void handleOverviewAction(ApiClient.CommunityOverview ov) {
        Stage owner = communityListBox.getScene() != null ? (Stage) communityListBox.getScene().getWindow() : null;

        if (ov.isMember()) {
            openOverviewAsCommunity(ov);
            return;
        }

        if ("INVITE_ONLY".equalsIgnoreCase(ov.visibility())) {
            var loader = ViewLoader.<JoinCommunityDialogController>load("JoinCommunityDialog.fxml");
            var modal = UiHelper.createModalStage(loader.root(), "Ingresar Código · " + ov.name(), owner);
            loader.controller().initForCommunity(api, workers, ov.id(), ov.name(), community -> {
                loadMyCommunities();
                loadExploreCatalog();
                UiHelper.showSuccess(owner, "¡Bienvenido al Espacio Universitario!",
                        "Te has incorporado exitosamente a '" + community.name() + "'.");
                if (navigator != null) {
                    navigator.openCommunityDetail(community);
                }
            });
            modal.showAndWait();
            return;
        }

        // Comunidad pública: unión directa validada atómicamente en el servidor (V14)
        statusLabel.setText("Uniéndote a '" + ov.name() + "'...");
        workers.submit(() -> {
            try {
                var joined = api.joinPublicCommunity(ov.id());
                Platform.runLater(() -> {
                    loadMyCommunities();
                    loadExploreCatalog();
                    UiHelper.showSuccess(owner, "¡Te has unido a la comunidad!",
                            "Ahora eres miembro de '" + joined.name() + "' ("
                                    + joined.memberCount() + " / " + joined.maxMembers() + " integrantes).");
                    if (navigator != null) {
                        navigator.openCommunityDetail(joined);
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    String msg = ex.getMessage() != null && !ex.getMessage().isBlank()
                            ? ex.getMessage() : "No se pudo completar la unión a la comunidad.";
                    statusLabel.setText("No se pudo unir: " + msg);
                    UiHelper.showError(owner, "No se pudo unir a la comunidad", msg);
                    loadExploreCatalog();
                });
            }
        });
    }

    private void openOverviewAsCommunity(ApiClient.CommunityOverview ov) {
        var match = myCommunities.stream().filter(c -> c.id().equals(ov.id())).findFirst();
        if (match.isPresent()) {
            if (navigator != null) navigator.openCommunityDetail(match.get());
        } else {
            var comm = new ApiClient.Community(ov.id(), ov.slug(), ov.name(), ov.description(),
                    "", ov.userRole() != null ? ov.userRole() : "MEMBER", null,
                    ov.memberCount(), ov.maxMembers(), ov.availableSlots(),
                    ov.category(), ov.visibility());
            if (navigator != null) navigator.openCommunityDetail(comm);
        }
    }

    // =========================================================================
    // NAVEGACIÓN DE PESTAÑAS Y FILTROS
    // =========================================================================

    @FXML
    private void onTabMyCommunities() {
        activeView = "MINE";
        updateMainTabs(tabMyCommunitiesBtn, tabExploreBtn);
        updateCatalogFiltersVisibility();
        loadMyCommunities();
    }

    @FXML
    private void onTabExplore() {
        activeView = "EXPLORE";
        updateMainTabs(tabExploreBtn, tabMyCommunitiesBtn);
        updateCatalogFiltersVisibility();
        loadExploreCatalog();
    }

    private void updateMainTabs(Button active, Button inactive) {
        active.getStyleClass().removeAll("tab-btn", "tab-btn-active");
        active.getStyleClass().add("tab-btn-active");
        inactive.getStyleClass().removeAll("tab-btn", "tab-btn-active");
        inactive.getStyleClass().add("tab-btn");
    }

    @FXML
    private void onSearch() {
        if ("EXPLORE".equals(activeView)) {
            renderExploreCatalog();
        } else {
            renderMyCommunities();
        }
    }

    @FXML
    private void onCatalogFilterChanged() {
        if (suppressFilterEvents) return;
        if ("EXPLORE".equals(activeView)) {
            renderExploreCatalog();
        }
    }

    @FXML
    private void onClearFilters() {
        suppressFilterEvents = true;
        searchField.setText("");
        selectedCategory = null;
        updateCategoryButtons(catAllBtn, catAcadBtn, catStudyBtn, catProjBtn, catCareerBtn, catInterestBtn);
        if (accessFilterCombo != null) accessFilterCombo.setValue(ACCESS_ALL);
        if (availabilityFilterCombo != null) availabilityFilterCombo.setValue(AVAIL_ALL);
        if (sortCombo != null) sortCombo.setValue(SORT_NAME);
        suppressFilterEvents = false;

        if ("EXPLORE".equals(activeView)) {
            renderExploreCatalog();
        } else {
            renderMyCommunities();
        }
    }

    @FXML
    private void onFilterAll() {
        selectedCategory = null;
        updateCategoryButtons(catAllBtn, catAcadBtn, catStudyBtn, catProjBtn, catCareerBtn, catInterestBtn);
        onSearch();
    }

    @FXML
    private void onFilterAcad() {
        selectedCategory = "ACADEMICA";
        updateCategoryButtons(catAcadBtn, catAllBtn, catStudyBtn, catProjBtn, catCareerBtn, catInterestBtn);
        onSearch();
    }

    @FXML
    private void onFilterStudy() {
        selectedCategory = "ESTUDIO";
        updateCategoryButtons(catStudyBtn, catAllBtn, catAcadBtn, catProjBtn, catCareerBtn, catInterestBtn);
        onSearch();
    }

    @FXML
    private void onFilterProj() {
        selectedCategory = "PROYECTO";
        updateCategoryButtons(catProjBtn, catAllBtn, catAcadBtn, catStudyBtn, catCareerBtn, catInterestBtn);
        onSearch();
    }

    @FXML
    private void onFilterCareer() {
        selectedCategory = "CARRERA";
        updateCategoryButtons(catCareerBtn, catAllBtn, catAcadBtn, catStudyBtn, catProjBtn, catInterestBtn);
        onSearch();
    }

    @FXML
    private void onFilterInterest() {
        selectedCategory = "INTERES";
        updateCategoryButtons(catInterestBtn, catAllBtn, catAcadBtn, catStudyBtn, catProjBtn, catCareerBtn);
        onSearch();
    }

    private void updateCategoryButtons(Button active, Button... others) {
        active.getStyleClass().removeAll("tab-btn", "tab-btn-active");
        active.getStyleClass().add("tab-btn-active");
        for (var b : others) {
            b.getStyleClass().removeAll("tab-btn", "tab-btn-active");
            b.getStyleClass().add("tab-btn");
        }
    }

    @FXML
    private void onRefresh() {
        if ("EXPLORE".equals(activeView)) {
            loadExploreCatalog();
        } else {
            loadMyCommunities();
        }
    }

    @FXML
    private void onCreateCommunity() {
        var loader = ViewLoader.<CreateCommunityDialogController>load("CreateCommunityDialog.fxml");
        Stage owner = communityListBox.getScene() != null ? (Stage) communityListBox.getScene().getWindow() : null;
        var modal = UiHelper.createModalStage(loader.root(), "Crear Nueva Comunidad", owner);

        loader.controller().init(api, workers, community -> {
            loadMyCommunities();
            if (community.initialInvitationCode() != null && !community.initialInvitationCode().isBlank()) {
                var successLoader = ViewLoader.<CommunityCreatedSuccessDialogController>load("CommunityCreatedSuccessDialog.fxml");
                successLoader.controller().set(community);
                var successModal = UiHelper.createModalStage(successLoader.root(), "¡Comunidad Creada Exitosamente!", owner);
                successModal.showAndWait();
            } else {
                UiHelper.showSuccess(owner, "¡Comunidad Creada!",
                        "La comunidad '" + community.name() + "' ha sido creada exitosamente.");
            }
            if (navigator != null) {
                navigator.openCommunityDetail(community);
            }
        });

        modal.showAndWait();
    }

    @FXML
    private void onJoinWithCode() {
        var loader = ViewLoader.<JoinCommunityDialogController>load("JoinCommunityDialog.fxml");
        Stage owner = communityListBox.getScene() != null ? (Stage) communityListBox.getScene().getWindow() : null;
        var modal = UiHelper.createModalStage(loader.root(), "Unirse con Código de Invitación", owner);

        loader.controller().init(api, workers, community -> {
            loadMyCommunities();
            if ("EXPLORE".equals(activeView)) loadExploreCatalog();
            UiHelper.showSuccess(owner, "¡Bienvenido al Espacio Universitario!",
                    "Te has incorporado exitosamente a la comunidad '" + community.name() + "'.");
            if (navigator != null) {
                navigator.openCommunityDetail(community);
            }
        });

        modal.showAndWait();
    }
}
