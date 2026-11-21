package com.example.tubespbolkel7.ui;

import com.example.tubespbolkel7.model.HotelData;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.FontWeight;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

import static com.example.tubespbolkel7.model.HotelData.Booking;
import static com.example.tubespbolkel7.model.RoomInventory.Status;
import static com.example.tubespbolkel7.ui.PageComponents.*;

final class OwnerDashboard {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("id-ID"));

    private OwnerDashboard() { }

    static Node view(HotelOperations app) {
        HotelData data = app.data();
        LocalDate today = data.today();
        long occupied = data.inventory().units().stream().filter(unit -> data.roomStatus(unit) == Status.OCCUPIED).count();
        int capacity = data.rooms().size();
        long checkIns = data.bookings().stream().map(item -> data.stay(item.code()))
                .filter(stay -> stay != null && today.equals(stay.checkIn())).count();
        long checkOuts = data.bookings().stream().map(item -> data.stay(item.code()))
                .filter(stay -> stay != null && today.equals(stay.checkOut())).count();

        VBox root = page("Dasbor Operasional", data.settings().hotel().name() + "  •  Owner",
                button("owner-dashboard-reports", "Laporan", false, () -> app.open(HotelRoute.OCCUPANCY)),
                button("owner-dashboard-reservation", "+ Reservasi Baru", true, () -> {
                    app.select(null);
                    app.open(HotelRoute.NEW_RESERVATION);
                }));

        TilePane summary = new TilePane(12, 10,
                metric("Okupansi saat ini", capacity == 0 ? "0%" : Math.round(100.0 * occupied / capacity) + "%",
                        occupied + " dari " + capacity + " kamar", () -> app.open(HotelRoute.ROOM_GRID)),
                metric("Check-in hari ini", Long.toString(checkIns), "Kedatangan tercatat",
                        () -> app.open(HotelRoute.CHECK_IN)),
                metric("Check-out hari ini", Long.toString(checkOuts), "Keberangkatan tercatat",
                        () -> app.open(HotelRoute.CHECK_OUT)),
                metric("Penerimaan hari ini", money(receipts(data, today)), "Pembayaran tercatat",
                        () -> app.open(HotelRoute.REVENUE))
        );
        summary.setPrefColumns(4);
        summary.setPrefTileHeight(118);
        summary.widthProperty().addListener((obs, before, width) -> {
            double available = width.doubleValue();
            int columns = available >= 770 ? 4 : available >= 360 ? 2 : 1;
            if (available > 0) {
                summary.setPrefTileWidth(Math.max(1, Math.floor((available - (columns - 1) * 12) / columns) - 1));
            }
        });

        root.getChildren().addAll(summary, analytics(app), reservations(app));
        return root;
    }

    private static VBox metric(String heading, String value, String hint, Runnable action) {
        Button open = button(null, "", false, action);
        open.setAccessibleText(heading + ", " + value + ". Buka halaman terkait.");
        open.setGraphic(new VBox(5, caption(heading), label(value, 24, FontWeight.BOLD, NAVY), caption(hint)));
        open.setMinWidth(0);
        open.setMaxWidth(Double.MAX_VALUE);
        open.setPrefHeight(86);
        open.setMaxHeight(86);
        open.setAlignment(Pos.CENTER_LEFT);
        open.setBackground(fill(Color.TRANSPARENT, 8));
        open.hoverProperty().addListener((obs, wasHover, isHover) ->
                open.setBackground(fill(isHover ? Color.web("#F4F7FE") : Color.TRANSPARENT, 8)));

        VBox metric = card(open);
        metric.setMinHeight(118);
        metric.setPrefHeight(118);
        metric.setMaxHeight(118);
        metric.setMaxWidth(Double.MAX_VALUE);
        return metric;
    }

    private static VBox analytics(HotelOperations app) {
        VBox revenue = revenue(app);
        VBox status = rooms(app);
        VBox analytics = new VBox(14, revenue, status);
        HBox split = row();
        split.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(revenue, Priority.ALWAYS);
        revenue.setMinWidth(0);

        analytics.widthProperty().addListener((obs, before, width) -> {
            boolean sideBySide = width.doubleValue() >= 1000;
            if (sideBySide == analytics.getChildren().contains(split)) {
                return;
            }
            analytics.getChildren().clear();
            split.getChildren().clear();
            if (sideBySide) {
                status.setPrefWidth(264);
                status.setMaxWidth(264);
                split.getChildren().setAll(revenue, status);
                analytics.getChildren().add(split);
            } else {
                status.setMaxWidth(Double.MAX_VALUE);
                analytics.getChildren().setAll(revenue, status);
            }
        });
        return analytics;
    }

    private static VBox revenue(HotelOperations app) {
        HotelData data = app.data();
        ComboBox<Integer> period = select("owner-revenue-period", List.of(7, 14, 30), 7);
        Label heading = title("");
        Label feedback = caption("");
        CategoryAxis dates = new CategoryAxis();
        NumberAxis amounts = new NumberAxis();
        amounts.setMinorTickVisible(false);
        amounts.setTickLabelFormatter(new StringConverter<>() {
            @Override public String toString(Number value) { return money(value.longValue()); }
            @Override public Number fromString(String value) { return 0; }
        });

        BarChart<String, Number> chart = new BarChart<>(dates, amounts);
        chart.setId("owner-revenue-chart");
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setPrefHeight(260);

        Runnable refresh = () -> {
            heading.setText("Penerimaan " + period.getValue() + " Hari Terakhir");
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            for (LocalDate day : days(data, period.getValue())) {
                long amount = receipts(data, day);
                XYChart.Data<String, Number> point = new XYChart.Data<>(DAY.format(day), amount);
                point.nodeProperty().addListener((obs, previous, node) -> {
                    if (node instanceof Region bar) {
                        bar.setBackground(fill(TEAL, 4));
                        Tooltip.install(bar, new Tooltip(DAY.format(day) + " · " + money(amount)));
                    }
                });
                series.getData().add(point);
            }
            chart.getData().setAll(List.of(series));
        };
        period.valueProperty().addListener((obs, old, value) -> refresh.run());
        refresh.run();

        Button download = button("owner-dashboard-export", "Unduh Laporan", false, () ->
                export(app.owner(), "Penerimaan " + period.getValue() + " Hari Terakhir", true,
                        List.of("Tanggal", "Penerimaan"),
                        days(data, period.getValue()).stream().<List<?>>map(day -> List.of(day, money(receipts(data, day)))).toList(),
                        feedback::setText));
        return card(row(heading, spacer(), caption("Jumlah hari"), period, download), chart, feedback);
    }

    private static VBox rooms(HotelOperations app) {
        HotelData data = app.data();
        VBox totals = new VBox(8);
        int total = data.rooms().size();
        for (Status status : Status.values()) {
            long count = data.inventory().units().stream().filter(unit -> data.roomStatus(unit) == status).count();
            Pane bar = new Pane();
            bar.setBackground(fill(PALE, 3));
            bar.setMinHeight(6);
            bar.setMaxHeight(6);

            Region indicator = new Region();
            indicator.setBackground(fill(TEAL, 3));
            indicator.setPrefHeight(6);
            indicator.prefWidthProperty().bind(bar.widthProperty().multiply(total == 0 ? 0 : count / (double) total));
            bar.getChildren().add(indicator);
            totals.getChildren().addAll(row(caption(status.toString()), spacer(), caption(Long.toString(count))), bar);
        }
        return card(title("Status Kamar"), totals,
                button("owner-dashboard-floor", "Lihat Denah Kamar", false, () -> app.open(HotelRoute.FLOOR_PLAN)));
    }

    private static VBox reservations(HotelOperations app) {
        TextField query = text("owner-recent-search", "", "Cari kode, tamu, atau nomor kamar...");
        String all = "Semua status";
        List<String> choices = new ArrayList<>(List.of(all));
        Arrays.stream(HotelData.Status.values()).map(HotelData.Status::label).forEach(choices::add);
        ComboBox<String> status = select("owner-recent-status", choices, all);
        TableView<Booking> table = table("owner-recent-bookings", "Belum ada reservasi untuk filter ini.");
        VBox navigation = new VBox();
        SimpleIntegerProperty pageIndex = new SimpleIntegerProperty();

        table.getColumns().addAll(List.of(
                column("Kode", Booking::code),
                column("Tamu", Booking::guest),
                column("Kamar", item -> item.room().number()),
                column("Check-in", item -> DAY.format(item.arrival())),
                column("Status", item -> item.status().label()),
                actions(item -> button("owner-booking-detail-" + item.code(), "Detail", false,
                        () -> ReservationPage.detail(app, item)))
        ));

        Runnable refresh = () -> {
            String key = query.getText().strip().toLowerCase(Locale.ROOT);
            List<Booking> visible = app.data().bookings().stream()
                    .filter(item -> status.getValue().equals(all) || status.getValue().equals(item.status().label()))
                    .filter(item -> (item.code() + " " + item.guest() + " " + item.room().number()).toLowerCase(Locale.ROOT).contains(key))
                    .toList();
            int page = Math.min(pageIndex.get(), Math.max(0, (visible.size() - 1) / PAGE_SIZE));
            int start = page * PAGE_SIZE;
            table.getItems().setAll(visible.subList(start, Math.min(start + PAGE_SIZE, visible.size())));
            sizeTable(table, table.getItems().size());
            navigation.getChildren().setAll(pageControls("owner-recent", page, visible.size(), pageIndex::set));
        };

        pageIndex.addListener((obs, old, value) -> refresh.run());
        Runnable filterChanged = () -> {
            if (pageIndex.get() == 0) {
                refresh.run();
            } else {
                pageIndex.set(0);
            }
        };
        query.textProperty().addListener((obs, old, value) -> filterChanged.run());
        status.valueProperty().addListener((obs, old, value) -> filterChanged.run());
        HBox.setHgrow(query, Priority.ALWAYS);
        refresh.run();

        return card(
                row(title("Reservasi Terbaru"), spacer(),
                        button("owner-dashboard-bookings", "Lihat semua", false, () -> app.open(HotelRoute.RESERVATIONS))),
                row(query, status), table, navigation
        );
    }

    private static List<LocalDate> days(HotelData data, int count) {
        LocalDate first = data.today().minusDays(count - 1);
        return IntStream.range(0, count).mapToObj(first::plusDays).toList();
    }

    private static long receipts(HotelData data, LocalDate day) {
        return data.receipts().stream().filter(item -> item.date().equals(day)).mapToLong(HotelData.Receipt::amount).sum();
    }
}