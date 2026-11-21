package com.example.tubespbolkel7.ui;

import com.example.tubespbolkel7.model.HotelData;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import static com.example.tubespbolkel7.model.HotelData.Booking;
import static com.example.tubespbolkel7.model.HotelData.Status;

final class StaffDashboard {
    private static final Color NAVY = Color.web("#102A4B");
    private static final Color MUTED = Color.web("#596779");
    private static final Color TEAL = Color.web("#006A60");
    private static final Color TEAL_HOVER = Color.web("#00554D");
    private static final Color PALE = Color.web("#EDF2FE");
    private static final Color PALE_HOVER = Color.web("#E0E9FC");
    private static final Color HAIRLINE = Color.web("#E4EBF6");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.forLanguageTag("id-ID"));

    private StaffDashboard() { }

    static Node view(HotelOperations app) {
        HotelData data = app.data();
        var today = data.today();
        List<Booking> bookings = List.copyOf(data.bookings());

        long occupied = bookings.stream().filter(b -> b.status() == Status.IN_HOUSE).count();
        long arrivals = bookings.stream().filter(b -> b.status() == Status.BOOKED && b.arrival().equals(today)).count();
        long departures = bookings.stream().filter(b -> b.status() == Status.IN_HOUSE && !b.departure().isAfter(today)).count();
        long available = data.rooms().stream().filter(room -> data.available(room, today, today.plusDays(1), null)
                && bookings.stream().noneMatch(b -> b.room().equals(room) && b.status() == Status.IN_HOUSE)).count();

        var hotel = data.settings().hotel();
        String property = hotel.city().isBlank() ? hotel.name() : hotel.name() + " · " + hotel.city();
        var user = data.settings().profile(app.username());
        String identity = user.displayName().isBlank()
                ? "Staf Administrasi"
                : user.displayName() + " · Staf Administrasi";

        HBox top = row(label(property, 15, FontWeight.BOLD, NAVY), spacer(),
                label(DATE.format(today) + "   •   " + identity, 13, FontWeight.SEMI_BOLD, TEAL));
        if (hotel.logo() != null) {
            ImageView mark = new ImageView(hotel.logo());
            mark.setPreserveRatio(true);
            mark.setFitWidth(42);
            mark.setFitHeight(28);
            top.getChildren().addFirst(mark);
        }
        top.setPadding(new Insets(16, 26, 16, 26));
        top.setBackground(Background.fill(Color.WHITE));
        top.setBorder(new Border(new BorderStroke(HAIRLINE, BorderStrokeStyle.SOLID,
                CornerRadii.EMPTY, new BorderWidths(0, 0, 1, 0))));

        Button newBooking = button("+ Reservasi Baru", true, () -> {
            app.select(null);
            app.open(HotelRoute.NEW_RESERVATION);
        });
        newBooking.setId("dashboard-new-reservation");

        HBox header = row(
                new VBox(4, label("Operasional Meja Depan", 27, FontWeight.BOLD, NAVY),
                        label("Kelola reservasi, kedatangan, dan keberangkatan tamu.", 13, FontWeight.NORMAL, MUTED)),
                spacer(),
                button("Serah Kunci", false, () -> app.open(HotelRoute.CHECK_IN)),
                newBooking);

        Button maintenanceInbox = button("Pesan Maintenance (" + data.frontDesk().all().size() + ")", false,
                () -> MaintenanceRoomActions.showInbox(app));
        maintenanceInbox.setId("dashboard-maintenance-inbox");
        top.getChildren().add(1, maintenanceInbox);

        HBox metrics = row(
                metric("TINGKAT OKUPANSI",
                        data.rooms().isEmpty() ? "0%" : Math.round(occupied * 100.0 / data.rooms().size()) + "%",
                        occupied + " dari " + data.rooms().size() + " kamar terisi",
                        () -> app.open(HotelRoute.IN_HOUSE)),
                metric("CHECK-IN HARI INI", Long.toString(arrivals), "Terjadwal tiba",
                        () -> app.open(HotelRoute.CHECK_IN)),
                metric("CHECK-OUT HARI INI", Long.toString(departures), "Terjadwal berangkat",
                        () -> app.open(HotelRoute.CHECK_OUT)),
                metric("KAMAR TERSEDIA", Long.toString(available), "Siap dipesan",
                        () -> app.open(HotelRoute.FLOOR_PLAN))
        );
        metrics.getChildren().forEach(node -> HBox.setHgrow(node, Priority.ALWAYS));

        TextField search = new TextField();
        search.setId("dashboard-search");
        search.setPromptText("Cari nama tamu, nomor kamar, atau kode reservasi...");
        search.setPrefHeight(36);
        search.setBackground(background(PALE, 8));
        HBox.setHgrow(search, Priority.ALWAYS);

        ComboBox<String> types = new ComboBox<>();
        types.getItems().add("Semua tipe");
        data.rooms().stream().map(HotelData.Room::type).distinct().forEach(types.getItems()::add);
        types.getSelectionModel().selectFirst();
        types.setId("dashboard-room-type");
        types.setPrefHeight(36);
        types.setCursor(Cursor.HAND);
        types.setBackground(background(PALE, 8));

        VBox list = new VBox(8);
        String[] filter = {"Semua"};
        HBox tabs = row();

        Runnable update = () -> {
            String query = search.getText().strip().toLowerCase(Locale.ROOT);
            List<Booking> filtered = bookings.stream()
                    .filter(b -> b.status() != Status.CANCELLED)
                    .filter(b -> switch (filter[0]) {
                        case "Belum tiba" -> b.status() == Status.BOOKED;
                        case "Selesai" -> b.status() != Status.BOOKED;
                        default -> true;
                    })
                    .filter(b -> types.getSelectionModel().getSelectedIndex() == 0 || b.room().type().equals(types.getValue()))
                    .filter(b -> (b.guest() + " " + b.room().number() + " " + b.code()).toLowerCase(Locale.ROOT).contains(query))
                    .toList();

            list.getChildren().clear();
            if (filtered.isEmpty()) {
                Label empty = label(bookings.isEmpty()
                        ? "Belum ada reservasi. Tambahkan reservasi untuk mulai."
                        : "Tidak ada reservasi yang cocok.", 14, FontWeight.NORMAL, MUTED);
                empty.setPadding(new Insets(30, 10, 30, 10));
                list.getChildren().add(empty);
            } else {
                filtered.stream().map(b -> bookingRow(app, b)).forEach(list.getChildren()::add);
            }
        };

        for (String status : List.of("Semua", "Belum tiba", "Selesai")) {
            Button tab = button(status, status.equals(filter[0]), () -> {
                filter[0] = status;
                for (Node node : tabs.getChildren()) {
                    if (node instanceof Button item) {
                        style(item, item.getText().equals(status), false);
                    }
                }
                update.run();
            });
            tabs.getChildren().add(tab);
        }

        search.textProperty().addListener((obs, old, value) -> update.run());
        types.valueProperty().addListener((obs, old, value) -> update.run());
        update.run();

        VBox queue = card(
                row(label("Daftar Kedatangan Tamu", 19, FontWeight.BOLD, NAVY), spacer(), tabs),
                row(search, types), list);
        HBox.setHgrow(queue, Priority.ALWAYS);

        VBox status = card(label("Status Kamar", 19, FontWeight.BOLD, NAVY),
                count("Tersedia", available),
                count("Terisi", occupied),
                count("Dipesan hari ini", arrivals),
                button("Buka Denah Kamar", true, () -> app.open(HotelRoute.FLOOR_PLAN)),
                button("Lihat Jadwal Check-out", false, () -> app.open(HotelRoute.CHECK_OUT)),
                button("Buka Housekeeping", false, () -> app.open(HotelRoute.HOUSEKEEPING)));
        status.setMinWidth(205);
        status.setPrefWidth(264);

        HBox content = row(queue, status);
        content.setAlignment(Pos.TOP_LEFT);

        VBox body = new VBox(20, header, metrics, content);
        body.setPadding(new Insets(24, 26, 28, 26));

        VBox page = new VBox(top, body);
        page.setBackground(Background.fill(Color.web("#F8F9FF")));
        return page;
    }

    private static Node bookingRow(HotelOperations app, Booking item) {
        VBox guest = new VBox(3,
                label(item.guest(), 14, FontWeight.BOLD, NAVY),
                label(item.code() + "  •  Kamar " + item.room().number() + "  •  " + DATE.format(item.arrival()),
                        12, FontWeight.NORMAL, MUTED));

        Button detail = button("Detail", false, () -> ReservationPage.detail(app, item));
        detail.setId("dashboard-detail-" + item.code());

        HBox row = row(guest, spacer(), label(item.status().label(), 12, FontWeight.BOLD, TEAL), detail);
        if (item.status() == Status.BOOKED || item.status() == Status.IN_HOUSE) {
            boolean arrival = (item.status() == Status.BOOKED);
            row.getChildren().add(button(arrival ? "Proses Check-in" : "Lihat Folio", true, () -> {
                app.select(item.code());
                app.open(arrival ? HotelRoute.CHECK_IN : HotelRoute.CHECK_OUT);
            }));
        }
        row.setPadding(new Insets(12));
        row.setBackground(background(PALE, 8));
        row.hoverProperty().addListener((obs, wasHover, isHover) ->
                row.setBackground(background(isHover ? PALE_HOVER : PALE, 8)));
        return row;
    }

    private static Node metric(String text, String value, String hint, Runnable action) {
        VBox graphic = new VBox(7,
                label(text, 12, FontWeight.BOLD, MUTED),
                label(value, 28, FontWeight.BOLD, NAVY),
                label(hint, 12, FontWeight.NORMAL, MUTED));

        Button button = new Button();
        button.setGraphic(graphic);
        button.setAccessibleText(text + " " + value);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setCursor(Cursor.HAND);
        button.setPadding(new Insets(16));
        button.setMaxWidth(Double.MAX_VALUE);
        button.setPrefWidth(180);
        button.setBackground(background(Color.WHITE, 12));
        button.setBorder(new Border(new BorderStroke(HAIRLINE, BorderStrokeStyle.SOLID,
                new CornerRadii(12), BorderWidths.DEFAULT)));
        button.setEffect(new DropShadow(10, 0, 2, Color.web("#102A4B", 0.04)));

        button.hoverProperty().addListener((obs, wasHover, isHover) ->
                button.setBackground(background(isHover ? Color.web("#F7FAFF") : Color.WHITE, 12)));
        button.setOnAction(event -> action.run());
        return button;
    }

    private static HBox count(String name, long value) {
        HBox row = row(label(name, 13, FontWeight.NORMAL, NAVY), spacer(),
                label(Long.toString(value), 14, FontWeight.BOLD, TEAL));
        row.setPadding(new Insets(10));
        row.setBackground(background(PALE, 8));
        return row;
    }

    private static VBox card(Node... content) {
        VBox box = new VBox(12, content);
        box.setPadding(new Insets(18));
        box.setBackground(background(Color.WHITE, 12));
        box.setBorder(new Border(new BorderStroke(HAIRLINE, BorderStrokeStyle.SOLID,
                new CornerRadii(12), BorderWidths.DEFAULT)));
        box.setEffect(new DropShadow(10, 0, 2, Color.web("#102A4B", 0.04)));
        return box;
    }

    private static Button button(String text, boolean primary, Runnable action) {
        Button button = new Button(text);
        button.setPadding(new Insets(8, 12, 8, 12));
        button.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 13));
        button.setCursor(Cursor.HAND);
        button.getProperties().put("primary", primary);

        button.hoverProperty().addListener(obs ->
                style(button, Boolean.TRUE.equals(button.getProperties().get("primary")), button.isHover()));
        style(button, primary, false);
        button.setOnAction(event -> action.run());
        return button;
    }

    private static void style(Button button, boolean primary, boolean hover) {
        button.getProperties().put("primary", primary);
        Color bg = primary ? (hover ? TEAL_HOVER : TEAL) : (hover ? PALE_HOVER : PALE);
        button.setBackground(background(bg, 8));
        button.setTextFill(primary ? Color.WHITE : NAVY);
    }

    private static Label label(String value, double size, FontWeight weight, Color color) {
        Label label = new Label(value);
        label.setFont(Font.font("Segoe UI", weight, size));
        label.setTextFill(color);
        return label;
    }

    private static HBox row(Node... nodes) {
        HBox box = new HBox(9, nodes);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private static Region spacer() {
        Region space = new Region();
        HBox.setHgrow(space, Priority.ALWAYS);
        return space;
    }

    private static Background background(Color color, double radius) {
        return new Background(new BackgroundFill(color, new CornerRadii(radius), Insets.EMPTY));
    }
}