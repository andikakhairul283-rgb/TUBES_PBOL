package com.example.tubespbolkel7.ui;

import com.example.tubespbolkel7.model.GuestRegistry;
import com.example.tubespbolkel7.model.HotelData;
import com.example.tubespbolkel7.model.ServiceLedger;
import javafx.event.ActionEvent;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.FontWeight;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import static com.example.tubespbolkel7.model.HotelData.Booking;
import static com.example.tubespbolkel7.model.HotelData.Room;
import static com.example.tubespbolkel7.model.HotelData.Status;
import static com.example.tubespbolkel7.ui.ReservationPage.*;

final class ReservationForm {
    private record Preferences(int adults, int children, String note, List<String> requests) {
        String formatted() {
            String guestCount = "Jumlah tamu: " + adults + " dewasa, " + children + " anak";
            String options = requests.isEmpty() ? "" : "\nPermintaan layanan: " + String.join(" · ", requests);
            return guestCount + options + (note.isBlank() ? "" : "\n" + note);
        }

        static Preferences read(String record) {
            if (record == null || !record.startsWith("Jumlah tamu: ")) {
                return new Preferences(2, 0, record == null ? "" : record, List.of());
            }
            String[] lines = record.split("\n", 3);
            String[] numbers = lines[0].substring("Jumlah tamu: ".length()).split(" dewasa, | anak");
            if (numbers.length != 2) {
                return new Preferences(2, 0, record, List.of());
            }
            int adults;
            int children;
            try {
                adults = Integer.parseInt(numbers[0]);
                children = Integer.parseInt(numbers[1]);
            } catch (NumberFormatException exception) {
                return new Preferences(2, 0, record, List.of());
            }
            if (lines.length > 1 && lines[1].startsWith("Permintaan layanan: ")) {
                return new Preferences(adults, children, lines.length > 2 ? lines[2] : "",
                        List.of(lines[1].substring("Permintaan layanan: ".length()).split(" · ")));
            }
            return new Preferences(adults, children,
                    lines.length == 3 ? lines[1] + "\n" + lines[2] : lines.length == 2 ? lines[1] : "",
                    List.of());
        }
    }

    private final HotelOperations app;
    private final HotelData data;
    private final Booking original;
    private final String code;
    private final ComboBox<String> guest;
    private final ComboBox<String> type;
    private final ComboBox<Room> room;
    private final DatePicker arrival;
    private final DatePicker departure;
    private final ComboBox<Integer> adults;
    private final ComboBox<Integer> children;
    private final TextArea note;
    private final TextField deposit;
    private final VBox availabilityMessage = new VBox();
    private final Label rate = text("", 13, FontWeight.SEMI_BOLD, NAVY);
    private final Label nights = text("", 13, FontWeight.SEMI_BOLD, NAVY);
    private final Label subtotal = text("", 13, FontWeight.SEMI_BOLD, NAVY);
    private final Label tax = text("", 13, FontWeight.SEMI_BOLD, NAVY);
    private final Label total = text("", 19, FontWeight.BOLD, NAVY);
    private final Label due = text("", 21, FontWeight.BOLD, Color.WHITE);
    private final Label error = text("", 13, FontWeight.SEMI_BOLD, ERROR);
    private final List<String> requests;
    private final FlowPane requestSummary = new FlowPane(6, 6);
    private final CheckBox breakfast = new CheckBox("Sarapan Prasmanan");
    private final CheckBox pickup = new CheckBox("Antar / Jemput Bandara");
    private final CheckBox extraBed = new CheckBox("Kasur Tambahan");

    private ReservationForm(HotelOperations app, Booking original, Room initialRoom, LocalDate day) {
        this.app = app;
        this.data = app.data();
        this.original = original;
        this.code = (original == null) ? data.nextCode() : original.code();

        Preferences preferences = Preferences.read(original == null ? null : original.notes());
        this.requests = new ArrayList<>(preferences.requests());

        this.guest = combo(data.guests().all().stream().map(GuestRegistry.Guest::choice)
                .sorted(String.CASE_INSENSITIVE_ORDER).toList(), Function.identity());
        guest.setEditable(true);
        guest.setPromptText("Pilih atau ketik nama tamu");
        guest.setId("booking-guest");
        if (original != null) {
            var registered = data.guestFor(original.code());
            guest.setValue(registered == null ? original.guest() : registered.choice());
        }

        String originalType = (original == null)
                ? (initialRoom == null ? null : initialRoom.type())
                : data.inventory().find(original.room().number()).type().label();

        this.type = combo(roomTypes(data, false), Function.identity());
        type.setId("booking-type");
        type.setPromptText("Pilih tipe kamar");
        type.setValue(originalType);

        this.room = combo(List.<Room>of(), item -> "Kamar " + item.number() + " · " + item.type());
        room.setPromptText("Pilih kamar tersedia");
        room.setId("booking-room");

        LocalDate initialDay = (day == null) ? data.today() : day;
        this.arrival = datePicker(original == null ? initialDay : original.arrival());
        arrival.setId("booking-arrival");
        this.departure = datePicker(original == null ? initialDay.plusDays(1) : original.departure());
        departure.setId("booking-departure");

        this.adults = combo(List.of(1, 2, 3, 4, 5, 6), String::valueOf);
        adults.setId("booking-adults");
        adults.setValue(preferences.adults());

        this.children = combo(List.of(0, 1, 2, 3, 4), String::valueOf);
        children.setId("booking-children");
        children.setValue(preferences.children());

        this.note = new TextArea(preferences.note());
        note.setId("booking-notes");
        note.setPromptText("Contoh: kamar bebas asap rokok atau bantal tambahan");
        note.setPrefRowCount(3);
        note.setWrapText(true);

        this.deposit = new TextField(original == null ? "0" : Long.toString(original.deposit()));
        deposit.setId("booking-deposit");
        deposit.setTextFormatter(new TextFormatter<>(change ->
                change.getControlNewText().matches("[0-9.]{0,16}") ? change : null));

        error.setId("booking-error");
        error.setWrapText(true);
        error.managedProperty().bind(error.textProperty().isNotEmpty());

        boolean occupied = original != null && original.status() == Status.IN_HOUSE;
        guest.setDisable(occupied);
        arrival.setDisable(occupied);
        type.setDisable(occupied);
        room.setDisable(occupied);
        deposit.setDisable(occupied);

        applyRoomList(original == null ? initialRoom : original.room());
        if (room.getValue() == null && type.getValue() != null && !room.getItems().isEmpty()) {
            room.setValue(room.getItems().getFirst());
        }
        type.valueProperty().addListener((property, old, value) -> applyRoomList(room.getValue()));
        arrival.valueProperty().addListener((property, old, value) -> applyRoomList(room.getValue()));
        departure.valueProperty().addListener((property, old, value) -> applyRoomList(room.getValue()));
        room.valueProperty().addListener((property, old, value) -> updateSummary());
        deposit.textProperty().addListener((property, old, value) -> updateSummary());

        for (CheckBox option : List.of(breakfast, pickup, extraBed)) {
            option.setCursor(Cursor.HAND);
            option.setSelected(requests.remove(option.getText()));
        }
        updateRequestSummary();
        updateSummary();
    }

    static Node view(HotelOperations app) {
        Booking original = (app.selected() == null) ? null : app.data().find(app.selected());
        if (original != null && !editable(original)) {
            original = null;
        }
        return new ReservationForm(app, original, null, null).pageContents(false);
    }

    static Node walkIn(HotelOperations app) {
        ReservationForm form = new ReservationForm(app, null, null, app.data().today());
        form.arrival.setDisable(true);
        return form.pageContents(true);
    }

    static void bookRoom(HotelOperations app, Room room, LocalDate day) {
        ReservationForm form = new ReservationForm(app, null, room, day);
        Dialog<Void> dialog = styledDialog(app, "Reservasi Baru · Kamar " + room.number());
        dialog.getDialogPane().setPrefWidth(820);
        ButtonType save = new ButtonType("Simpan Reservasi", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, save);

        VBox controls = new VBox(12, form.formColumns(), form.error);
        controls.setPadding(new Insets(14));
        ScrollPane view = new ScrollPane(controls);
        view.setFitToWidth(true);
        view.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        view.setPrefViewportHeight(550);
        dialog.getDialogPane().setContent(view);

        dialog.getDialogPane().lookupButton(save).addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            if (form.save()) {
                dialog.close();
                app.open(HotelRoute.RESERVATIONS);
            }
        });
        dialog.showAndWait();
    }

    private Node pageContents(boolean walkIn) {
        HotelRoute destination = walkIn ? HotelRoute.CHECK_IN : HotelRoute.RESERVATIONS;
        Runnable leave = () -> {
            app.select(null);
            app.open(destination);
        };

        Button back = button("‹ Kembali", false, leave);
        back.setId("booking-back");
        Button cancel = button("Batal", false, leave);
        cancel.setId("booking-cancel");
        Button save = button(original == null ? "Simpan Reservasi" : "Simpan Perubahan", true, () -> {
            if (save()) {
                app.open(destination);
            }
        });
        save.setId("booking-save");

        HBox controls = horizontal(10, secondary(code), spacer(), cancel, save);
        String heading = walkIn ? "Tamu Walk-in" : original == null ? "Reservasi Baru" : "Ubah Reservasi";
        return page(
                heading(heading, "Atur tamu, jadwal menginap, kamar, dan pembayaran reservasi.", back),
                formColumns(), error, card(0, controls));
    }

    private VBox formColumns() {
        Button addGuest = button("+ Tamu baru", false, this::openGuest);
        addGuest.setId("booking-new-guest");
        addGuest.setDisable(guest.isDisable());

        VBox guestInput = new VBox(6,
                horizontal(10, text("Tamu *", 13, FontWeight.SEMI_BOLD, NAVY), spacer(), addGuest), guest);
        guest.setMaxWidth(Double.MAX_VALUE);

        GridPane inputs = fieldsGrid();
        inputs.add(field("Tipe kamar *", type), 0, 0);
        inputs.add(field("Kamar tersedia *", room), 1, 0);
        inputs.add(field("Tanggal check-in *", arrival), 0, 1);
        inputs.add(field("Tanggal check-out *", departure), 1, 1);
        inputs.add(field("Tamu dewasa", adults), 0, 2);
        inputs.add(field("Anak", children), 1, 2);

        VBox booking = card(16, text("Informasi Pemesanan", 19, FontWeight.BOLD, NAVY),
                guestInput, inputs, availabilityMessage, field("Permintaan Khusus", note));

        Button addService = button("+ Tambah layanan", false, this::openService);
        addService.setId("booking-add-service");
        FlowPane options = new FlowPane(12, 10, breakfast, pickup, extraBed);
        VBox extras = card(10,
                horizontal(8, text("Fasilitas Tambahan", 16, FontWeight.BOLD, NAVY), spacer(), addService),
                secondary("Permintaan dicatat pada reservasi. Biaya layanan dicatat pada folio tamu."),
                options, requestSummary);

        VBox left = new VBox(14, booking, extras);
        VBox right = costSummary();
        right.setMinWidth(0);
        right.setMaxWidth(Double.MAX_VALUE);
        left.setMinWidth(0);

        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(15);
        gridColumns(grid, 67, 33);
        grid.add(left, 0, 0);
        grid.add(right, 1, 0);
        GridPane.setValignment(right, VPos.TOP);
        GridPane.setVgrow(left, Priority.ALWAYS);

        grid.widthProperty().addListener((observable, previous, width) -> {
            boolean stacked = width.doubleValue() < 760;
            GridPane.setColumnSpan(left, stacked ? 2 : 1);
            GridPane.setConstraints(right, stacked ? 0 : 1, stacked ? 1 : 0, stacked ? 2 : 1, 1,
                    HPos.LEFT, VPos.TOP);
        });
        return new VBox(grid);
    }

    private VBox costSummary() {
        Label preamble = text("RINGKASAN HARGA", 11, FontWeight.BOLD, MUTED);
        VBox remainder = new VBox(3, text("SISA PEMBAYARAN", 11, FontWeight.BOLD, Color.web("#D6E4F6")), due);
        remainder.setPadding(new Insets(14));
        remainder.setBackground(background(Color.web("#1E3A5F"), 9));
        remainder.setEffect(new DropShadow(8, 0, 3, Color.web("#1E3A5F", 0.22)));

        return card(13, preamble, text("Estimasi Tagihan", 18, FontWeight.BOLD, NAVY),
                summaryLine("Harga per malam", rate),
                summaryLine("Jumlah malam", nights),
                summaryLine("Subtotal kamar", subtotal),
                summaryLine("Pajak & layanan (" + data.taxPercent(code) + "%)", tax),
                summaryLine("Total tagihan", total),
                field("DP / Uang Muka (Rp)", deposit),
                remainder);
    }

    private HBox summaryLine(String label, Label value) {
        return horizontal(8, secondary(label), spacer(), value);
    }

    private void applyRoomList(Room preferred) {
        LocalDate start = arrival.getValue();
        LocalDate end = departure.getValue();
        String selectedType = type.getValue();

        List<Room> available = (start == null || end == null || !end.isAfter(start))
                ? List.of()
                : data.rooms().stream()
                        .filter(value -> selectedType == null || value.type().equals(selectedType))
                        .filter(value -> data.available(value, start, end, original == null ? null : original.code()))
                        .toList();

        room.getItems().setAll(available);
        Room current = (preferred == null) ? null : available.stream()
                .filter(value -> value.number().equals(preferred.number()))
                .findFirst().orElse(null);
        room.setValue(room.isDisable() ? preferred : current);

        String message = available.isEmpty()
                ? "Tidak ada kamar tersedia untuk tanggal dan tipe yang dipilih."
                : available.size() + " kamar tersedia selama periode ini.";
        availabilityMessage.getChildren().setAll(pill(message,
                available.isEmpty() ? ERROR : TEAL,
                available.isEmpty() ? Color.web("#FDF0EF") : Color.web("#E4FAEF")));
        updateSummary();
    }

    private void updateSummary() {
        Room item = room.getValue();
        if (item != null && original != null && item.number().equals(original.room().number())) {
            item = original.room();
        }
        LocalDate checkIn = arrival.getValue();
        LocalDate checkOut = departure.getValue();
        long stay = (checkIn == null || checkOut == null) ? 0 : Math.max(0, ChronoUnit.DAYS.between(checkIn, checkOut));
        long roomRate = (item == null) ? 0 : item.rate();

        rate.setText(money(roomRate));
        nights.setText(stay + " malam");
        if (item == null || stay <= 0) {
            subtotal.setText(money(0));
            tax.setText(money(0));
            total.setText(money(0));
            due.setText(money(0));
            return;
        }
        long downPayment = depositAmount();
        Booking estimate = new Booking(code, "", item, checkIn, checkOut,
                original == null ? Status.BOOKED : original.status(), downPayment, "");
        subtotal.setText(money(data.subtotal(estimate)));
        tax.setText(money(data.tax(estimate)));
        total.setText(money(data.total(estimate)));
        due.setText(money(data.remaining(estimate)));
    }

    private boolean save() {
        try {
            String person = guest.getEditor().getText().strip();
            if (person.isEmpty()) {
                throw new IllegalArgumentException("Isi nama tamu terlebih dahulu.");
            }
            Room selectedRoom = room.getValue();
            if (selectedRoom == null) {
                throw new IllegalArgumentException("Pilih kamar tersedia terlebih dahulu.");
            }
            LocalDate checkIn = committed(arrival);
            LocalDate checkOut = committed(departure);
            if (checkIn == null || checkOut == null || !checkOut.isAfter(checkIn)) {
                throw new IllegalArgumentException("Tanggal check-out harus setelah tanggal check-in.");
            }
            long downPayment = depositAmount();
            List<String> selectedRequests = new ArrayList<>(requests);
            for (CheckBox option : List.of(breakfast, pickup, extraBed)) {
                if (option.isSelected()) {
                    selectedRequests.add(option.getText());
                }
            }
            Preferences preferences = new Preferences(adults.getValue(), children.getValue(),
                    note.getText().strip(), selectedRequests);
            Booking booking = new Booking(code, person, selectedRoom, checkIn, checkOut,
                    original == null ? Status.BOOKED : original.status(), downPayment, preferences.formatted());
            data.save(booking);
            app.select(booking.code());
            error.setText("");
            return true;
        } catch (IllegalArgumentException exception) {
            error.setText(exception.getMessage());
            return false;
        }
    }

    private long depositAmount() {
        String value = deposit.getText().replace(".", "");
        return value.isBlank() ? 0 : Long.parseLong(value);
    }

    private void openGuest() {
        Dialog<String> dialog = styledDialog(app, "Tambah tamu baru");
        ButtonType add = new ButtonType("Gunakan nama tamu", ButtonBar.ButtonData.OK_DONE);
        TextField name = new TextField();
        name.setId("booking-new-guest-name");
        name.setPromptText("Nama lengkap sesuai identitas");

        VBox content = new VBox(10,
                secondary("Nama dipakai pada reservasi dan folio tamu."),
                field("Nama lengkap *", name));
        content.setPadding(new Insets(14));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, add);
        dialog.getDialogPane().lookupButton(add).disableProperty().bind(name.textProperty().map(String::isBlank));
        dialog.setResultConverter(button -> button.equals(add) ? name.getText().strip() : null);

        dialog.showAndWait().ifPresent(person -> {
            if (!guest.getItems().contains(person)) {
                guest.getItems().add(person);
            }
            guest.setValue(person);
        });
    }

    private void openService() {
        Dialog<String> dialog = styledDialog(app, "Permintaan layanan tambahan");
        ButtonType add = new ButtonType("Tambahkan", ButtonBar.ButtonData.OK_DONE);
        ComboBox<String> service = combo(data.services().catalog().stream()
                .filter(ServiceLedger.CatalogItem::available)
                .map(ServiceLedger.CatalogItem::name).toList(), Function.identity());
        service.setId("booking-service-choices");
        service.setPromptText("Pilih layanan");

        VBox content = new VBox(10,
                secondary("Permintaan disimpan pada catatan pemesanan."),
                field("Layanan", service));
        content.setPadding(new Insets(14));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, add);
        dialog.getDialogPane().lookupButton(add).disableProperty().bind(service.valueProperty().isNull());
        dialog.setResultConverter(button -> button.equals(add) ? service.getValue() : null);

        dialog.showAndWait().filter(item -> !requests.contains(item)).ifPresent(item -> {
            requests.add(item);
            updateRequestSummary();
        });
    }

    private void updateRequestSummary() {
        requestSummary.getChildren().setAll(requests.stream().filter(Objects::nonNull).map(item ->
                button(item + "  ×", false, () -> {
                    requests.remove(item);
                    updateRequestSummary();
                })).toList());
    }
}