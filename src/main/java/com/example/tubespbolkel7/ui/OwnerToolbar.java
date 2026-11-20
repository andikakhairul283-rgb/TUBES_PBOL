package com.example.tubespbolkel7.ui;

import com.example.tubespbolkel7.model.HotelData;
import com.example.tubespbolkel7.model.RoomInventory;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.example.tubespbolkel7.ui.PageComponents.*;

final class OwnerToolbar {
    private record Result(String name, String context, Runnable open) { }

    static HBox create(HotelOperations app, Runnable logout) {
        Label brand = title(app.data().settings().hotel().name());
        brand.setWrapText(false);
        brand.setMinWidth(0);
        brand.setTooltip(new Tooltip(brand.getText()));
        TextField search = text("owner-global-search", "", "Cari tamu, kamar, atau reservasi…");
        search.setPrefWidth(265);
        search.setMinWidth(120);
        Button find = button("owner-global-submit", "Cari", false, () -> showSearch(app, search.getText()));
        search.setOnAction(event -> find.fire());
        MenuButton identity = new MenuButton("Owner", null, item("Profil Saya", () -> app.open(HotelRoute.MY_PROFILE)),
                item("Pengaturan", () -> app.open(HotelRoute.PROFILE)), item("Keluar", logout));
        identity.setId("owner-profile-menu");
        identity.setMinHeight(36);
        identity.setBackground(fill(PALE, 8));
        var profile = app.data().settings().profile(app.username());
        identity.setTooltip(new Tooltip(profile.displayName().isBlank() ? "Owner" : profile.displayName() + " · Owner"));
        HBox toolbar = row(brand, spacer(), search, find,
                button("owner-notifications", "Notifikasi", false, () -> showNotifications(app)), identity);
        toolbar.setPadding(new Insets(12, 25, 12, 25));
        toolbar.setBackground(fill(Color.WHITE, 0));
        return toolbar;
    }

    private static MenuItem item(String title, Runnable action) {
        MenuItem item = new MenuItem(title);
        item.setOnAction(event -> action.run());
        return item;
    }

    private static void showSearch(HotelOperations app, String initial) {
        TextField query = text("owner-search-query", initial.strip(), "Nama, nomor kamar, atau kode reservasi");
        Label summary = caption("");
        VBox rows = new VBox(7);
        ScrollPane scroll = new ScrollPane(rows);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(300);
        var dialog = dialog(app.owner(), "Cari di CheckIn.id", card(query, summary, scroll));
        dialog.getDialogPane().getButtonTypes().add(javafx.scene.control.ButtonType.CLOSE);
        Runnable refresh = () -> {
            String key = query.getText().strip().toLowerCase(Locale.ROOT);
            List<Result> results = new ArrayList<>();
            HotelData data = app.data();
            if (!key.isBlank()) {
                data.bookings().stream().filter(b -> matches(key, b.code(), b.guest(), b.room().number()))
                        .limit(12).forEach(b -> results.add(new Result(b.code() + " · " + b.guest(), "Reservasi · " + b.status(),
                                () -> ReservationPage.detail(app, b))));
                data.inventory().units().stream().filter(u -> matches(key, u.room().number(), u.type().label()))
                        .limit(12).forEach(u -> results.add(new Result("Kamar " + u.room().number(), u.type().label() + " · " + data.roomStatus(u), () -> {
                            app.selectRoom(u.room().number());
                            app.open(HotelRoute.FLOOR_PLAN);
                        })));
                data.guests().all().stream().filter(g -> matches(key, g.name(), g.id(), g.phone()))
                        .limit(12).forEach(g -> results.add(new Result(g.name(), "Tamu · " + g.id(), () -> GuestPage.showDetail(app, g))));
            }
            summary.setText(key.isBlank() ? "Ketik kata kunci untuk mencari." : results.isEmpty()
                    ? "Tidak ada hasil yang cocok." : "Hasil pencarian");
            rows.getChildren().setAll(results.stream().map(result -> resultRow(result, dialog::close)).toList());
        };
        query.textProperty().addListener((obs, old, value) -> refresh.run());
        refresh.run();
        dialog.showAndWait();
    }

    private static void showNotifications(HotelOperations app) {
        HotelData data = app.data();
        List<Result> alerts = new ArrayList<>();
        int maintenanceMessages = data.frontDesk().all().size();
        if (maintenanceMessages > 0) alerts.add(new Result("Pesan Maintenance", maintenanceMessages + " pesan internal",
                () -> MaintenanceRoomActions.showInbox(app)));
        data.inventory().jobs().stream().filter(RoomInventory.Job::active)
                .forEach(job -> alerts.add(new Result("Kamar " + job.room() + " · " + job.condition(),
                        job.code() + " · " + job.priority(), () -> app.open(HotelRoute.HOUSEKEEPING))));
        data.bookings().stream().filter(b -> b.status() == HotelData.Status.BOOKED && b.arrival().equals(data.today()))
                .forEach(b -> alerts.add(new Result("Kedatangan · " + b.guest(), b.code() + " · Kamar " + b.room().number(), () -> {
                    app.select(b.code());
                    app.open(HotelRoute.CHECK_IN);
                })));
        data.bookings().stream().filter(b -> b.status() == HotelData.Status.IN_HOUSE && !b.departure().isAfter(data.today()))
                .forEach(b -> alerts.add(new Result("Keberangkatan · " + b.guest(), b.code() + " · Kamar " + b.room().number(), () -> {
                    app.select(b.code());
                    app.open(HotelRoute.CHECK_OUT);
                })));
        VBox rows = new VBox(7);
        ScrollPane scroll = new ScrollPane(rows);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(Math.clamp(alerts.size() * 54, 80, 360));
        var dialog = dialog(app.owner(), "Notifikasi Operasional", card(scroll));
        dialog.getDialogPane().getButtonTypes().add(javafx.scene.control.ButtonType.CLOSE);
        if (alerts.isEmpty()) rows.getChildren().add(caption("Belum ada notifikasi operasional."));
        else rows.getChildren().addAll(alerts.stream().map(alert -> resultRow(alert, dialog::close)).toList());
        dialog.showAndWait();
    }

    private static HBox resultRow(Result result, Runnable close) {
        VBox info = new VBox(3, title(result.name()), caption(result.context()));
        HBox.setHgrow(info, Priority.ALWAYS);
        return row(info, spacer(), button(null, "Buka", false, () -> {
            close.run();
            result.open().run();
        }));
    }

    private static boolean matches(String query, String... values) {
        return String.join(" ", values).toLowerCase(Locale.ROOT).contains(query);
    }
}
