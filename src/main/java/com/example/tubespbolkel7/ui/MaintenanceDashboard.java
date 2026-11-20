package com.example.tubespbolkel7.ui;

import com.example.tubespbolkel7.model.MaintenanceStock;
import com.example.tubespbolkel7.model.RoomInventory;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static com.example.tubespbolkel7.model.RoomInventory.Condition;
import static com.example.tubespbolkel7.model.RoomInventory.Job;
import static com.example.tubespbolkel7.model.RoomInventory.Status;
import static com.example.tubespbolkel7.ui.PageComponents.*;

final class MaintenanceDashboard {
    private static final String ALL = "Semua tugas";
    private static final String REPAIR = "Perbaikan";
    private static final String CLEANING = "Pembersihan";

    static Node view(HotelOperations app) {
        RoomInventory inventory = app.data().inventory();
        var hotel = app.data().settings().hotel();
        var profile = app.data().settings().profile(app.username());
        Label refreshed = caption("");
        Label urgent = metric();
        Label cleaning = metric();
        Label completed = metric();
        GridPane metrics = new GridPane();
        metrics.setHgap(12);
        for (int index = 0; index < 3; index++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(100.0 / 3);
            metrics.getColumnConstraints().add(column);
        }
        metrics.addRow(0, metricCard("Tugas Darurat", urgent, "Perlu ditangani segera"),
                metricCard("Antrean Housekeeping", cleaning, "Menunggu pembersihan"),
                metricCard("Selesai Hari Ini", completed, "Pekerjaan ditutup"));
        FlowPane statusCards = new FlowPane(8, 8);
        FlowPane categories = new FlowPane(8, 8);
        TextField search = text("maintenance-task-search", "", "Cari tiket, nomor kamar, atau kendala...");
        ComboBox<String> assignee = select("maintenance-assignment-filter",
                List.of("Semua penugasan", "Tugas saya", "Belum ditugaskan"), "Semua penugasan");
        search.setPrefWidth(292);
        SimpleIntegerProperty pageIndex = new SimpleIntegerProperty();
        VBox pageBar = new VBox();
        TableView<Job> table = table("maintenance-task-table", "Belum ada tugas aktif untuk filter ini.");
        VBox stockPreview = new VBox(9);
        ComboBox<String> selection = select("maintenance-category", List.of(ALL, REPAIR, CLEANING), ALL);
        Runnable refresh = () -> {
            List<Job> all = inventory.jobs();
            List<Job> active = all.stream().filter(Job::active).toList();
            long cleaningCount = count(active, CLEANING);
            long repairCount = count(active, REPAIR);
            urgent.setText(Long.toString(active.stream().filter(job -> job.priority() == RoomInventory.Priority.URGENT).count()));
            cleaning.setText(Long.toString(cleaningCount));
            completed.setText(Long.toString(all.stream().filter(job -> job.finished() != null)
                    .filter(job -> job.finished().toLocalDate().equals(app.data().today())).count()));
            statusCards.getChildren().setAll(java.util.Arrays.stream(Status.values()).map(status -> {
                long rooms = inventory.units().stream().filter(unit -> app.data().roomStatus(unit) == status).count();
                return button("maintenance-status-" + status.name().toLowerCase(Locale.ROOT), status + "  " + rooms,
                        false, () -> app.openRoomGrid(status));
            }).toList());
            categories.getChildren().setAll(tab(selection, "all", ALL, active.size()), tab(selection, "repair", REPAIR, repairCount),
                    tab(selection, "cleaning", CLEANING, cleaningCount));
            String query = search.getText().strip().toLowerCase(Locale.ROOT);
            List<Job> matched = active.stream().filter(job -> matchesCategory(job, selection.getValue()))
                    .filter(job -> matchesAssignee(job, assignee.getValue(), app.username(), profile.displayName()))
                    .filter(job -> (job.code() + " " + job.room() + " " + job.description()).toLowerCase(Locale.ROOT).contains(query))
                    .sorted(Comparator.comparing(Job::priority).reversed().thenComparing(Job::opened)).toList();
            int current = Math.min(pageIndex.get(), Math.max(0, (matched.size() - 1) / PAGE_SIZE));
            int offset = current * PAGE_SIZE;
            table.getItems().setAll(matched.subList(offset, Math.min(offset + PAGE_SIZE, matched.size())));
            sizeTable(table, table.getItems().size());
            pageBar.getChildren().setAll(pageControls("maintenance-tasks", current, matched.size(), pageIndex::set));
            updateStock(stockPreview, inventory.stock().items());
            refreshed.setText("Diperbarui " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        };
        table.getColumns().addAll(List.of(column("Tiket", Job::code), column("Kamar", Job::room),
                column("Kendala", Job::description), column("Prioritas", job -> job.priority().toString()),
                column("Estimasi", job -> job.estimatedMinutes() == 0 ? "—" : job.estimatedMinutes() + " menit"),
                actions(job -> button("maintenance-open-" + job.code(), "Buka", false,
                        () -> app.housekeeping().openJob(job, refresh)))));
        Runnable filterChanged = () -> {
            if (pageIndex.get() == 0) refresh.run();
            else pageIndex.set(0);
        };
        search.textProperty().addListener((obs, old, value) -> filterChanged.run());
        assignee.valueProperty().addListener((obs, old, value) -> filterChanged.run());
        selection.valueProperty().addListener((obs, old, value) -> filterChanged.run());
        pageIndex.addListener((obs, old, value) -> refresh.run());
        VBox root = page("Dasbor Operasional", hotel.name() + (profile.displayName().isBlank() ? "" : " · " + profile.displayName()),
                button("maintenance-contact", "Hubungi Resepsionis", false, () -> app.maintenance().contact(null)),
                button("maintenance-refresh", "Perbarui Data", false, refresh),
                button("maintenance-scan", "Scan Kamar", true, () -> app.maintenance().scan(refresh)));
        root.getChildren().addAll(metrics,
                card(row(title("Status Kamar"), spacer(), button("maintenance-grid", "Buka Grid Status", false,
                                () -> app.open(HotelRoute.ROOM_GRID))),
                        statusCards),
                card(row(title("Antrean Tugas"), spacer(), button("maintenance-tasks", "Buka Housekeeping", false,
                                () -> app.open(HotelRoute.HOUSEKEEPING))),
                        categories, new FlowPane(8, 8, search, selection, assignee), table, pageBar),
                card(row(title("Log Peralatan & Sparepart"), spacer(), button("maintenance-stock", "Kelola Stok", false,
                                () -> MaintenanceStockPage.show(app, refresh))),
                        stockPreview),
                refreshed);
        Timeline updates = new Timeline(new KeyFrame(Duration.seconds(15), event -> refresh.run()));
        updates.setCycleCount(Timeline.INDEFINITE);
        root.sceneProperty().addListener((obs, old, scene) -> {
            if (scene == null) updates.stop();
            else updates.playFromStart();
        });
        refresh.run();
        return root;
    }

    private static Button tab(ComboBox<String> selected, String id, String label, long count) {
        return button("maintenance-tab-" + id, label + " (" + count + ")", label.equals(selected.getValue()),
                () -> selected.setValue(label));
    }

    private static boolean matchesCategory(Job job, String filter) {
        if (REPAIR.equals(filter)) return job.condition() == Condition.MAINTENANCE;
        if (CLEANING.equals(filter)) return job.condition() == Condition.CLEANING;
        return true;
    }

    private static boolean matchesAssignee(Job job, String filter, String username, String displayName) {
        if ("Tugas saya".equals(filter)) return !job.assignee().isBlank()
                && (job.assignee().equalsIgnoreCase(username) || job.assignee().equalsIgnoreCase(displayName));
        if ("Belum ditugaskan".equals(filter)) return job.assignee().isBlank();
        return true;
    }

    private static long count(List<Job> jobs, String kind) {
        return jobs.stream().filter(job -> matchesCategory(job, kind)).count();
    }

    private static Label metric() {
        return label("", 27, FontWeight.BOLD, NAVY);
    }

    private static VBox metricCard(String heading, Label count, String hint) {
        VBox card = card(caption(heading), count, caption(hint));
        card.setMinWidth(0);
        card.setPrefHeight(112);
        card.setMaxHeight(112);
        return card;
    }

    private static void updateStock(VBox preview, List<MaintenanceStock.Item> items) {
        preview.getChildren().clear();
        if (items.isEmpty()) {
            preview.getChildren().add(caption("Belum ada stok tercatat."));
            return;
        }
        items.stream().sorted(Comparator.comparingInt(MaintenanceStock.Item::quantity)).limit(4).forEach(item -> {
            Label itemName = caption(item.name());
            HBox.setHgrow(itemName, Priority.ALWAYS);
            preview.getChildren().add(row(itemName, spacer(), caption(item.quantity() + " " + item.unit() + " · " + item.status())));
        });
    }
}
