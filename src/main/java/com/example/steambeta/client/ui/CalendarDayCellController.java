package com.example.steambeta.client.ui;

import com.example.steambeta.client.ApiClient;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import java.time.LocalDate;
import java.util.List;

public final class CalendarDayCellController {

    @FXML private VBox cellContainer;
    @FXML private Label dayNumberLabel;
    @FXML private Label eventCountBadge;
    @FXML private VBox eventsPreviewBox;

    private LocalDate date;
    private Runnable onSelect;

    public void set(LocalDate date, boolean isCurrentMonth, boolean isToday, boolean isSelected,
                    List<ApiClient.UpcomingEvent> dayEvents, Runnable onSelect) {
        this.date = date;
        this.onSelect = onSelect;

        dayNumberLabel.setText(String.valueOf(date.getDayOfMonth()));

        cellContainer.getStyleClass().removeAll(
                "calendar-day-cell-other",
                "calendar-day-cell-today",
                "calendar-day-cell-selected"
        );

        if (!isCurrentMonth) {
            cellContainer.getStyleClass().add("calendar-day-cell-other");
        }
        if (isToday) {
            cellContainer.getStyleClass().add("calendar-day-cell-today");
        }
        if (isSelected) {
            cellContainer.getStyleClass().add("calendar-day-cell-selected");
        }

        eventsPreviewBox.getChildren().clear();

        if (dayEvents != null && !dayEvents.isEmpty()) {
            eventCountBadge.setText(String.valueOf(dayEvents.size()));
            eventCountBadge.setVisible(true);
            eventCountBadge.setManaged(true);

            // Cargar etiquetas FXML para hasta 2 eventos
            int limit = Math.min(2, dayEvents.size());
            for (int i = 0; i < limit; i++) {
                var ev = dayEvents.get(i);
                var tagView = ViewLoader.<CalendarEventTagController>load("CalendarEventTag.fxml");
                String badgeStyle = "badge-academic";
                if ("EXAM".equalsIgnoreCase(ev.eventType())) {
                    badgeStyle = "badge-pending";
                } else if ("SUBMISSION".equalsIgnoreCase(ev.eventType())) {
                    badgeStyle = "badge-resolved";
                }
                String shortTitle = ev.title() != null && ev.title().length() > 9
                        ? ev.title().substring(0, 8) + "…" : ev.title();
                tagView.controller().set(shortTitle, badgeStyle);
                eventsPreviewBox.getChildren().add(tagView.root());
            }
        } else {
            eventCountBadge.setVisible(false);
            eventCountBadge.setManaged(false);
        }
    }

    public LocalDate getDate() {
        return date;
    }

    @FXML
    private void onCellClicked() {
        if (onSelect != null) {
            onSelect.run();
        }
    }
}
