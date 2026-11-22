package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import com.example.steambeta.client.SessionScope;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * Controlador de Calendario y Fechas Clave (FXML) — SteamBeta Campus:
 * Proporciona vista mensual cuadrícula, vista semanal y vista de agenda,
 * navegación por meses y filtrado por tipo de evento.
 * Construcción 100% basada en FXML (Celdas cargadas via ViewLoader).
 */
public final class CalendarController {

    @FXML private Button viewMonthBtn;
    @FXML private Button viewWeekBtn;
    @FXML private Button viewAgendaBtn;

    @FXML private Label monthTitleLabel;
    @FXML private Button filterAllBtn;
    @FXML private Button filterExamsBtn;
    @FXML private Button filterSubsBtn;
    @FXML private Button filterMeetingsBtn;

    @FXML private Label statusLabel;
    @FXML private GridPane dayHeadersGrid;
    @FXML private GridPane calendarGrid;
    @FXML private VBox agendaListBox;

    @FXML private Label selectedDayTitleLabel;
    @FXML private Label selectedDateDisplayLabel;
    @FXML private VBox selectedDayEventsBox;

    private ApiClient api;
    private SessionScope scope;
    private ExecutorService workers;

    private YearMonth currentMonth = YearMonth.now();
    private LocalDate selectedDate = LocalDate.now();
    private String filterType = null;
    private String currentView = "MONTH";

    private final List<ApiClient.UpcomingEvent> allEvents = new ArrayList<>();
    private final Map<LocalDate, List<ApiClient.UpcomingEvent>> eventsByDate = new HashMap<>();

    public void init(ApiClient api, SessionScope scope, ExecutorService workers) {
        this.api = api;
        this.scope = scope;
        this.workers = workers;

        loadEvents();
    }

    private void loadEvents() {
        long epoch = scope.generation();
        statusLabel.setText("Consultando agenda en PostgreSQL...");

        workers.submit(() -> {
            try {
                var events = api.upcomingEvents();
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    allEvents.clear();
                    if (events != null) allEvents.addAll(events);
                    indexEvents();
                    renderView();
                    updateSelectedDayDetail();
                    statusLabel.setText("Agenda sincronizada (" + allEvents.size() + " evento(s) cargado(s)).");
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (!scope.current(epoch)) return;
                    statusLabel.setText("No se pudieron cargar los eventos del calendario.");
                });
            }
        });
    }

    private void indexEvents() {
        eventsByDate.clear();
        for (var ev : allEvents) {
            if (filterType != null && !filterType.equalsIgnoreCase(ev.eventType())) {
                continue;
            }
            LocalDate d = parseEventDate(ev.eventDate());
            if (d != null) {
                eventsByDate.computeIfAbsent(d, k -> new ArrayList<>()).add(ev);
            }
        }
    }

    private LocalDate parseEventDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            if (dateStr.length() == 10 && dateStr.charAt(4) == '-' && dateStr.charAt(7) == '-') {
                return LocalDate.parse(dateStr);
            }
            Instant instant = Instant.parse(dateStr);
            return instant.atZone(ZoneId.systemDefault()).toLocalDate();
        } catch (Exception e) {
            try {
                return LocalDate.parse(dateStr.substring(0, 10));
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    private void renderView() {
        updateMonthHeader();

        if ("MONTH".equals(currentView)) {
            dayHeadersGrid.setVisible(true);
            dayHeadersGrid.setManaged(true);
            calendarGrid.setVisible(true);
            calendarGrid.setManaged(true);
            agendaListBox.setVisible(false);
            agendaListBox.setManaged(false);
            renderMonthGrid();
        } else if ("WEEK".equals(currentView)) {
            dayHeadersGrid.setVisible(true);
            dayHeadersGrid.setManaged(true);
            calendarGrid.setVisible(true);
            calendarGrid.setManaged(true);
            agendaListBox.setVisible(false);
            agendaListBox.setManaged(false);
            renderWeekGrid();
        } else {
            dayHeadersGrid.setVisible(false);
            dayHeadersGrid.setManaged(false);
            calendarGrid.setVisible(false);
            calendarGrid.setManaged(false);
            agendaListBox.setVisible(true);
            agendaListBox.setManaged(true);
            renderAgendaList();
        }
    }

    private void updateMonthHeader() {
        String monthName = currentMonth.getMonth().getDisplayName(java.time.format.TextStyle.FULL, new Locale("es", "ES"));
        monthName = monthName.substring(0, 1).toUpperCase() + monthName.substring(1);
        monthTitleLabel.setText(monthName + " " + currentMonth.getYear());
    }

    private void renderMonthGrid() {
        calendarGrid.getChildren().clear();

        LocalDate firstOfMonth = currentMonth.atDay(1);
        LocalDate startGrid = firstOfMonth.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        for (int i = 0; i < 42; i++) {
            LocalDate date = startGrid.plusDays(i);
            int col = i % 7;
            int row = i / 7;

            boolean isCurrentMonth = date.getMonth().equals(currentMonth.getMonth());
            boolean isToday = date.equals(LocalDate.now());
            boolean isSelected = date.equals(selectedDate);

            var cellLoader = ViewLoader.<CalendarDayCellController>load("CalendarDayCell.fxml");
            var dayEvents = eventsByDate.get(date);

            final LocalDate clickedDate = date;
            cellLoader.controller().set(date, isCurrentMonth, isToday, isSelected, dayEvents, () -> {
                selectedDate = clickedDate;
                renderView();
                updateSelectedDayDetail();
            });

            calendarGrid.add(cellLoader.root(), col, row);
        }
    }

    private void renderWeekGrid() {
        calendarGrid.getChildren().clear();

        LocalDate startOfWeek = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        for (int i = 0; i < 7; i++) {
            LocalDate date = startOfWeek.plusDays(i);
            boolean isCurrentMonth = date.getMonth().equals(currentMonth.getMonth());
            boolean isToday = date.equals(LocalDate.now());
            boolean isSelected = date.equals(selectedDate);

            var cellLoader = ViewLoader.<CalendarDayCellController>load("CalendarDayCell.fxml");
            var dayEvents = eventsByDate.get(date);

            final LocalDate clickedDate = date;
            cellLoader.controller().set(date, isCurrentMonth, isToday, isSelected, dayEvents, () -> {
                selectedDate = clickedDate;
                renderView();
                updateSelectedDayDetail();
            });

            calendarGrid.add(cellLoader.root(), i, 0);
        }
    }

    private void renderAgendaList() {
        agendaListBox.getChildren().clear();

        var filtered = allEvents.stream()
                .filter(e -> filterType == null || filterType.equalsIgnoreCase(e.eventType()))
                .toList();

        if (filtered.isEmpty()) {
            var empty = ViewLoader.<EmptyStateController>load("EmptyState.fxml");
            empty.controller().set("✓", "Sin eventos en agenda",
                    "No hay hitos programados en el período actual.", null, null);
            agendaListBox.getChildren().add(empty.root());
        } else {
            for (var ev : filtered) {
                var evCard = ViewLoader.<EventCardController>load("EventCard.fxml");
                evCard.controller().set(ev);
                agendaListBox.getChildren().add(evCard.root());
            }
        }
    }

    private void updateSelectedDayDetail() {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM, yyyy", new Locale("es", "ES"));
        selectedDateDisplayLabel.setText(selectedDate.format(dtf));

        selectedDayEventsBox.getChildren().clear();
        var dayEvents = eventsByDate.get(selectedDate);

        if (dayEvents == null || dayEvents.isEmpty()) {
            var empty = ViewLoader.<EmptyStateController>load("EmptyState.fxml");
            empty.controller().set("●", "Sin eventos en esta fecha",
                    "No hay exámenes ni entregas registradas para este día.", null, null);
            selectedDayEventsBox.getChildren().add(empty.root());
        } else {
            for (var ev : dayEvents) {
                var evCard = ViewLoader.<EventCardController>load("EventCard.fxml");
                evCard.controller().set(ev);
                selectedDayEventsBox.getChildren().add(evCard.root());
            }
        }
    }

    @FXML
    private void onPrevMonth() {
        currentMonth = currentMonth.minusMonths(1);
        renderView();
    }

    @FXML
    private void onNextMonth() {
        currentMonth = currentMonth.plusMonths(1);
        renderView();
    }

    @FXML
    private void onToday() {
        currentMonth = YearMonth.now();
        selectedDate = LocalDate.now();
        renderView();
        updateSelectedDayDetail();
    }

    @FXML
    private void onViewMonthly() {
        currentView = "MONTH";
        updateViewButtons(viewMonthBtn, viewWeekBtn, viewAgendaBtn);
        renderView();
    }

    @FXML
    private void onViewWeekly() {
        currentView = "WEEK";
        updateViewButtons(viewWeekBtn, viewMonthBtn, viewAgendaBtn);
        renderView();
    }

    @FXML
    private void onViewAgenda() {
        currentView = "AGENDA";
        updateViewButtons(viewAgendaBtn, viewMonthBtn, viewWeekBtn);
        renderView();
    }

    private void updateViewButtons(Button active, Button... others) {
        active.getStyleClass().removeAll("tab-btn", "tab-btn-active");
        active.getStyleClass().add("tab-btn-active");
        for (var b : others) {
            b.getStyleClass().removeAll("tab-btn", "tab-btn-active");
            b.getStyleClass().add("tab-btn");
        }
    }

    @FXML
    private void onFilterAll() {
        filterType = null;
        updateButtons(filterAllBtn, filterExamsBtn, filterSubsBtn, filterMeetingsBtn);
        indexEvents();
        renderView();
        updateSelectedDayDetail();
    }

    @FXML
    private void onFilterExams() {
        filterType = "EXAM";
        updateButtons(filterExamsBtn, filterAllBtn, filterSubsBtn, filterMeetingsBtn);
        indexEvents();
        renderView();
        updateSelectedDayDetail();
    }

    @FXML
    private void onFilterSubs() {
        filterType = "SUBMISSION";
        updateButtons(filterSubsBtn, filterAllBtn, filterExamsBtn, filterMeetingsBtn);
        indexEvents();
        renderView();
        updateSelectedDayDetail();
    }

    @FXML
    private void onFilterMeetings() {
        filterType = "MEETING";
        updateButtons(filterMeetingsBtn, filterAllBtn, filterExamsBtn, filterSubsBtn);
        indexEvents();
        renderView();
        updateSelectedDayDetail();
    }

    private void updateButtons(Button active, Button... others) {
        active.getStyleClass().removeAll("tab-btn", "tab-btn-active");
        active.getStyleClass().add("tab-btn-active");
        for (var b : others) {
            b.getStyleClass().removeAll("tab-btn", "tab-btn-active");
            b.getStyleClass().add("tab-btn");
        }
    }

    @FXML
    private void onRefresh() {
        loadEvents();
    }
}
