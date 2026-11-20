package com.example.tubespbolkel7.ui;

import com.example.tubespbolkel7.model.HotelData;

import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.StringConverter;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import static com.example.tubespbolkel7.model.HotelData.Booking;
import static com.example.tubespbolkel7.model.HotelData.Room;
import static com.example.tubespbolkel7.model.HotelData.Status;

final class ReservationPage {
    static final Color NAVY = Color.web("#073055");
    static final Color TEAL = Color.web("#006A60");
    static final Color MUTED = Color.web("#5E6B7C");
    static final Color SOFT = Color.web("#EDF2FD");
    static final Color LINE = Color.web("#E5ECF7");
    static final Color ERROR = Color.web("#A42C26");
    static final Locale INDONESIAN = Locale.forLanguageTag("id-ID");
    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", INDONESIAN);
    private static final int PAGE_SIZE = 8;
    private static final String ALL_STATUS = "Semua status";
    private static final String ALL_TYPES = "Semua tipe kamar";

    private record DateRange(LocalDate from, LocalDate until) {
        boolean includes(Booking booking) {
            return (from == null || !booking.departure().isBefore(from))
                    && (until == null || !booking.arrival().isAfter(until));
        }

        String display() {
            return from == null && until == null ? "Pilih rentang tanggal"
                    : (from == null ? "Awal" : DATE.format(from)) + " – " + (until == null ? "Akhir" : DATE.format(until));
        }
    }

    static Node view(HotelOperations app, HotelRoute route) {
        return switch (route) {
            case RESERVATIONS -> list(app);
            case NEW_RESERVATION -> ReservationForm.view(app);
            case WALK_IN -> ReservationForm.walkIn(app);
            case FLOOR_PLAN, AVAILABILITY -> availability(app);
            default -> throw new IllegalArgumentException("Halaman reservasi tidak dikenal: " + route);
        };
    }

    private static VBox list(HotelOperations app) {
        HotelData data = app.data();
        Button calendar = button("Kalender ketersediaan", false,
                () -> app.open(app.isOwner() ? HotelRoute.AVAILABILITY : HotelRoute.FLOOR_PLAN));
        calendar.setId("reservation-calendar");
        Button create = button("+ Reservasi baru", true, () -> {
            app.select(null);
            app.open(HotelRoute.NEW_RESERVATION);
        });
        create.setId("reservation-new");
        HBox headline = heading("Reservasi", "Kelola semua pemesanan kamar dan status masa inap tamu hotel.", calendar, create);
        HBox metrics = metrics(data);

        TextField search = new TextField();
        search.setId("reservation-search");
        search.setPromptText("Cari kode atau nama tamu...");
        search.setPrefWidth(260);
        styleInput(search);
        List<String> filters = new ArrayList<>(List.of(ALL_STATUS));
        Arrays.stream(Status.values()).map(Status::label).forEach(filters::add);
        ComboBox<String> status = combo(filters, Function.identity());
        status.setId("reservation-status");
        status.setValue(ALL_STATUS);
        ComboBox<String> type = combo(roomTypes(data, true), Function.identity());
        type.setId("reservation-room-type");
        type.setValue(ALL_TYPES);
        SimpleObjectProperty<DateRange> range = new SimpleObjectProperty<>(new DateRange(null, null));
        Button dates = button(range.get().display(), false, () -> editDates(app, range));
        dates.setId("reservation-date-range");
        range.addListener((obsolete, previous, current) -> dates.setText(current.display()));
        FlowPane filtersRow = new FlowPane(10, 10, search, status, dates, type);
        VBox filtersBox = card(0, filtersRow);

        VBox rows = new VBox();
        rows.setId("reservation-table-rows");
        Label count = secondary("");
        HBox pageButtons = horizontal(6);
        HBox navigation = horizontal(10, count, spacer(), pageButtons);
        navigation.setPadding(new Insets(12, 16, 12, 16));
        VBox table = new VBox(columnHeaders(), rows, navigation);
        table.setMinWidth(870);
        table.setBackground(background(Color.WHITE, 12));
        table.setBorder(new Border(new BorderStroke(LINE, BorderStrokeStyle.SOLID, new CornerRadii(12), BorderWidths.DEFAULT)));
        SimpleIntegerProperty pageIndex = new SimpleIntegerProperty();
        Runnable refresh = () -> {
            String query = search.getText().strip().toLowerCase(INDONESIAN);
            List<Booking> list = data.bookings().stream()
                    .filter(booking -> (booking.code() + " " + booking.guest() + " " + booking.room().number()).toLowerCase(INDONESIAN).contains(query))
                    .filter(booking -> ALL_STATUS.equals(status.getValue()) || booking.status().label().equals(status.getValue()))
                    .filter(booking -> ALL_TYPES.equals(type.getValue()) || booking.room().type().equals(type.getValue()))
                    .filter(range.get()::includes)
                    .sorted(Comparator.comparing(Booking::code).reversed())
                    .toList();
            int totalPages = Math.max(1, (list.size() + PAGE_SIZE - 1) / PAGE_SIZE);
            int start = Math.min(pageIndex.get(), totalPages - 1) * PAGE_SIZE;
            int end = Math.min(start + PAGE_SIZE, list.size());
            rows.getChildren().setAll(list.subList(start, end).stream().map(booking -> row(app, booking)).toList());
            if (list.isEmpty()) {
                VBox empty = new VBox(text("Tidak ada reservasi yang cocok.", 14, FontWeight.SEMI_BOLD, NAVY),
                        secondary("Coba kata kunci, status, atau rentang tanggal lain."));
                empty.setPadding(new Insets(24, 18, 24, 18));
                rows.getChildren().add(empty);
            }
            count.setText(list.isEmpty() ? "0 reservasi" : String.format(INDONESIAN,
                    "Menampilkan %d–%d dari %d reservasi", start + 1, end, list.size()));
            pageButtons.getChildren().setAll(pageSelector(pageIndex, totalPages));
        };
        Runnable reset = () -> {
            if (pageIndex.get() == 0) refresh.run();
            else pageIndex.set(0);
        };
        pageIndex.addListener((property, old, value) -> refresh.run());
        search.textProperty().addListener((property, old, value) -> reset.run());
        status.valueProperty().addListener((property, old, value) -> reset.run());
        type.valueProperty().addListener((property, old, value) -> reset.run());
        range.addListener((property, old, value) -> reset.run());
        refresh.run();
        return page(headline, metrics, filtersBox, horizontalScroll(table));
    }

    private static List<Button> pageSelector(SimpleIntegerProperty current, int pages) {
        List<Button> buttons = new ArrayList<>();
        Button previous = button("‹", false, () -> current.set(current.get() - 1));
        previous.setId("reservation-previous-page");
        previous.setAccessibleText("Halaman sebelumnya");
        previous.setDisable(current.get() == 0);
        buttons.add(previous);
        int first = Math.max(0, Math.min(current.get() - 2, pages - 5));
        for (int page = first; page < Math.min(first + 5, pages); page++) {
            int target = page;
            buttons.add(button(Integer.toString(page + 1), page == current.get(), () -> current.set(target)));
        }
        Button next = button("›", false, () -> current.set(current.get() + 1));
        next.setId("reservation-next-page");
        next.setAccessibleText("Halaman berikutnya");
        next.setDisable(current.get() + 1 >= pages);
        buttons.add(next);
        return buttons;
    }

    private static HBox metrics(HotelData data) {
        List<Booking> records = data.bookings();
        long occupied = records.stream().filter(booking -> booking.status() == Status.IN_HOUSE).count();
        long today = records.stream().filter(booking -> booking.status() == Status.BOOKED && booking.arrival().equals(data.today())).count();
        long pending = records.stream().filter(booking -> booking.status() == Status.BOOKED || booking.status() == Status.IN_HOUSE)
                .mapToLong(data::remaining).sum();
        HBox metrics = horizontal(12);
        metrics.setAlignment(Pos.TOP_LEFT);
        for (String[] metric : List.of(new String[]{"TOTAL RESERVASI", Integer.toString(records.size()), "Seluruh catatan pemesanan"},
                new String[]{"TAMU MENGINAP", Long.toString(occupied), "Status check-in aktif"},
                new String[]{"KEDATANGAN HARI INI", Long.toString(today), "Tamu yang menunggu check-in"},
                new String[]{"PENDAPATAN TERTUNDA", money(pending), "Sisa tagihan reservasi aktif"})) {
            Label title = text(metric[0], 11, FontWeight.BOLD, MUTED);
            title.setWrapText(true);
            VBox tile = card(6, title, text(metric[1], 24, FontWeight.BOLD, NAVY), secondary(metric[2]));
            tile.setMinWidth(0);
            tile.setPrefWidth(0);
            tile.setMinHeight(112);
            tile.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(tile, Priority.ALWAYS);
            metrics.getChildren().add(tile);
        }
        return metrics;
    }

    private static GridPane columnHeaders() {
        GridPane header = columns();
        String[] labels = {"KODE", "TAMU", "KAMAR", "CHECK-IN", "CHECK-OUT", "MALAM", "TOTAL (RP)", "STATUS", "AKSI"};
        for (int index = 0; index < labels.length; index++) {
            header.add(text(labels[index], 11, FontWeight.BOLD, NAVY), index, 0);
        }
        header.setBackground(background(SOFT, 9));
        header.setMinHeight(46);
        return header;
    }

    private static GridPane row(HotelOperations app, Booking booking) {
        GridPane row = columns();
        Label code = text(booking.code(), 12, FontWeight.SEMI_BOLD, NAVY);
        String guest = booking.guest();
        Label initials = text(initials(guest), 11, FontWeight.BOLD, NAVY);
        initials.setAlignment(Pos.CENTER);
        initials.setMinSize(30, 30);
        initials.setBackground(background(Color.web("#DFEAFA"), 20));
        Label guestName = text(guest, 13, FontWeight.SEMI_BOLD, NAVY);
        guestName.setWrapText(true);
        HBox person = horizontal(8, initials, guestName);
        VBox room = new VBox(2, text(booking.room().number() + " · " + booking.room().type(), 13, FontWeight.SEMI_BOLD, NAVY),
                secondary("Lantai " + floor(booking.room())));
        Button detail = button("Detail", false, () -> detail(app, booking));
        detail.setId("reservation-detail-" + booking.code());
        Button edit = button("Ubah", false, () -> edit(app, booking));
        edit.setId("reservation-edit-" + booking.code());
        edit.setDisable(!editable(booking));
        List<Node> cells = List.of(code, person, room, secondary(DATE.format(booking.arrival())), secondary(DATE.format(booking.departure())),
                secondary(booking.nights() + " malam"), text(money(app.data().total(booking)), 12, FontWeight.SEMI_BOLD, NAVY),
                badge(booking.status()), horizontal(4, detail, edit));
        for (int col = 0; col < cells.size(); col++) {
            row.add(cells.get(col), col, 0);
        }
        row.setMinHeight(64);
        row.setBorder(new Border(new BorderStroke(LINE, BorderStrokeStyle.SOLID,
                CornerRadii.EMPTY, new BorderWidths(0, 0, 1, 0))));
        return row;
    }

    private static GridPane columns() {
        GridPane grid = new GridPane();
        grid.setPadding(new Insets(9, 15, 9, 15));
        grid.setHgap(6);
        grid.setAlignment(Pos.CENTER_LEFT);
        gridColumns(grid, 9, 16, 13, 10, 10, 6, 10, 13, 13);
        return grid;
    }

    static void detail(HotelOperations app, Booking booking) {
        Dialog<Void> dialog = styledDialog(app, "Detail Reservasi");
        var close = new ButtonType("Tutup", ButtonBar.ButtonData.CANCEL_CLOSE);
        var edit = new ButtonType("Ubah reservasi", ButtonBar.ButtonData.OTHER);
        var cancel = new ButtonType("Batalkan reservasi", ButtonBar.ButtonData.OTHER);
        dialog.getDialogPane().getButtonTypes().add(close);
        if (editable(booking)) {
            dialog.getDialogPane().getButtonTypes().add(edit);
        }
        if (booking.status() == Status.BOOKED) {
            dialog.getDialogPane().getButtonTypes().add(cancel);
            dialog.getDialogPane().lookupButton(cancel).addEventFilter(ActionEvent.ACTION, event -> {
                event.consume();
                PageComponents.edit(app.owner(), "Batalkan Reservasi", PageComponents.card(
                        PageComponents.title(booking.code() + " · " + booking.guest()),
                        PageComponents.caption("Kamar akan kembali tersedia sesuai jadwal. Pembayaran yang tercatat tetap ada di riwayat.")),
                        "Konfirmasi pembatalan", () -> app.data().cancel(booking.code()), () -> {
                            dialog.close();
                            app.open(HotelRoute.RESERVATIONS);
                        });
            });
        }
        GridPane data = fieldsGrid();
        data.add(detailRow("Tamu", booking.guest()), 0, 0);
        data.add(detailRow("Kamar", booking.room().number() + " · " + booking.room().type()), 1, 0);
        data.add(detailRow("Check-in", DATE.format(booking.arrival())), 0, 1);
        data.add(detailRow("Check-out", DATE.format(booking.departure())), 1, 1);
        data.add(detailRow("Total tagihan", money(app.data().total(booking))), 0, 2);
        data.add(detailRow("DP / Deposit", money(booking.deposit())), 1, 2);
        data.add(detailRow("Sisa tagihan", money(app.data().remaining(booking))), 0, 3);
        data.add(detailRow("Jumlah malam", booking.nights() + " malam"), 1, 3);
        VBox content = new VBox(14, horizontal(10, text(booking.code(), 21, FontWeight.BOLD, NAVY), spacer(), badge(booking.status())), data,
                detailRow("Catatan", booking.notes() == null || booking.notes().isBlank() ? "Tidak ada catatan" : booking.notes()));
        String cancellation = app.data().settings().policy().cancellationNote();
        if (!cancellation.isBlank()) content.getChildren().add(detailRow("Kebijakan pembatalan", cancellation));
        content.setPadding(new Insets(15));
        dialog.getDialogPane().setContent(content);
        if (editable(booking)) {
            dialog.getDialogPane().lookupButton(edit).addEventFilter(ActionEvent.ACTION, event -> {
                event.consume();
                dialog.close();
                edit(app, booking);
            });
        }
        dialog.showAndWait();
    }

    static boolean editable(Booking booking) {
        return booking.status() == Status.BOOKED || booking.status() == Status.IN_HOUSE;
    }

    private static void edit(HotelOperations app, Booking booking) {
        app.select(booking.code());
        app.open(HotelRoute.NEW_RESERVATION);
    }

    private static void editDates(HotelOperations app, SimpleObjectProperty<DateRange> range) {
        Dialog<Void> dialog = styledDialog(app, "Filter tanggal reservasi");
        ButtonType apply = new ButtonType("Terapkan", ButtonBar.ButtonData.OK_DONE);
        ButtonType reset = new ButtonType("Hapus filter", ButtonBar.ButtonData.LEFT);
        dialog.getDialogPane().getButtonTypes().addAll(reset, ButtonType.CANCEL, apply);
        DatePicker from = datePicker(range.get().from());
        DatePicker until = datePicker(range.get().until());
        from.setId("reservation-filter-from");
        until.setId("reservation-filter-until");
        GridPane entries = fieldsGrid();
        entries.add(field("Tanggal mulai", from), 0, 0);
        entries.add(field("Tanggal akhir", until), 1, 0);
        Label error = text("", 12, FontWeight.MEDIUM, ERROR);
        VBox content = new VBox(10, secondary("Pilih periode yang bersinggungan dengan masa inap tamu."), entries, error);
        content.setPadding(new Insets(15));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().lookupButton(apply).addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            try {
                LocalDate start = committed(from);
                LocalDate end = committed(until);
                if (start != null && end != null && end.isBefore(start)) {
                    throw new IllegalArgumentException("Tanggal akhir harus sama atau setelah tanggal mulai.");
                }
                range.set(new DateRange(start, end));
                dialog.close();
            } catch (IllegalArgumentException exception) {
                error.setText(exception.getMessage());
            }
        });
        dialog.getDialogPane().lookupButton(reset).addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            range.set(new DateRange(null, null));
            dialog.close();
        });
        dialog.showAndWait();
    }

    private static VBox availability(HotelOperations app) {
        HotelData data = app.data();
        Button back = button("‹ Reservasi", false, () -> app.open(HotelRoute.RESERVATIONS));
        back.setId("reservation-calendar-back");
        YearMonth currentMonth = YearMonth.from(data.today());
        var month = new SimpleObjectProperty<>(currentMonth);
        Button previous = button("‹", false, () -> month.set(month.get().minusMonths(1)));
        previous.setId("calendar-previous-month");
        previous.setAccessibleText("Bulan sebelumnya");
        Button next = button("›", false, () -> month.set(month.get().plusMonths(1)));
        next.setId("calendar-next-month");
        next.setAccessibleText("Bulan berikutnya");
        Button today = button("Bulan ini", false, () -> month.set(currentMonth));
        ComboBox<String> type = combo(roomTypes(data, true), Function.identity());
        type.setId("availability-room-type");
        type.setValue(ALL_TYPES);
        Label period = text("", 17, FontWeight.BOLD, NAVY);
        period.setId("availability-month");
        GridPane days = new GridPane();
        days.setHgap(6);
        days.setVgap(6);
        for (int col = 0; col < 7; col++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(100.0 / 7);
            column.setFillWidth(true);
            days.getColumnConstraints().add(column);
        }
        Label caption = secondary("Klik tanggal untuk melihat kamar yang bisa dipesan. Jumlah dihitung untuk satu malam.");
        Runnable refresh = () -> {
            YearMonth selected = month.get();
            period.setText(selected.format(DateTimeFormatter.ofPattern("MMMM yyyy", INDONESIAN)));
            days.getChildren().clear();
            String[] weekdays = {"Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min"};
            for (int col = 0; col < weekdays.length; col++) {
                Label label = text(weekdays[col], 12, FontWeight.BOLD, NAVY);
                label.setMaxWidth(Double.MAX_VALUE);
                label.setAlignment(Pos.CENTER);
                label.setPadding(new Insets(7));
                days.add(label, col, 0);
            }
            int firstColumn = selected.atDay(1).getDayOfWeek().getValue() - 1;
            List<Room> rooms = roomsByType(data, type.getValue());
            for (int day = 1; day <= selected.lengthOfMonth(); day++) {
                LocalDate date = selected.atDay(day);
                int cell = firstColumn + day - 1;
                long available = rooms.stream().filter(room -> data.available(room, date, date.plusDays(1), null)).count();
                Button box = button(available + " tersedia", false, () -> dayRooms(app, date, rooms));
                box.setId("availability-date-" + date);
                box.setAccessibleText(DATE.format(date) + ", " + available + " kamar tersedia");
                box.setAlignment(Pos.TOP_LEFT);
                box.setContentDisplay(javafx.scene.control.ContentDisplay.TOP);
                box.setGraphic(text(Integer.toString(day), 16, FontWeight.BOLD, NAVY));
                box.setBackground(background(available > 0 ? Color.web("#E9F8F4") : SOFT, 8));
                box.setTextFill(available > 0 ? TEAL : MUTED);
                box.setMaxWidth(Double.MAX_VALUE);
                box.setMinHeight(62);
                days.add(box, cell % 7, 1 + cell / 7);
            }
        };
        month.addListener((property, old, value) -> refresh.run());
        type.valueProperty().addListener((property, old, value) -> refresh.run());
        refresh.run();
        FlowPane toolbar = new FlowPane(8, 8, previous, period, next, today, type);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        return page(heading("Kalender Ketersediaan", "Periksa kamar yang tersedia sesuai tanggal dan tipe kamar.", back),
                card(12, toolbar, caption, days));
    }

    private static void dayRooms(HotelOperations app, LocalDate day, List<Room> options) {
        Dialog<Void> dialog = styledDialog(app, "Ketersediaan · " + DATE.format(day));
        dialog.getDialogPane().getButtonTypes().add(new ButtonType("Tutup", ButtonBar.ButtonData.CANCEL_CLOSE));
        VBox entries = new VBox(8);
        for (Room room : options) {
            boolean free = app.data().available(room, day, day.plusDays(1), null);
            Label state = pill(free ? "Tersedia" : "Terisi / dipesan", free ? TEAL : MUTED, free ? Color.web("#DDF7EF") : SOFT);
            HBox row = horizontal(12, new VBox(3, text(room.number() + " · " + room.type(), 14, FontWeight.BOLD, NAVY),
                    secondary(money(room.rate()) + " / malam")), spacer(), state);
            row.setPadding(new Insets(9, 12, 9, 12));
            row.setBackground(background(SOFT, 7));
            if (free) {
                Button book = button("Pesan", true, () -> {
                    dialog.close();
                    ReservationForm.bookRoom(app, room, day);
                });
                book.setId("availability-book-" + room.number());
                row.getChildren().add(book);
            }
            entries.getChildren().add(row);
        }
        if (options.isEmpty()) entries.getChildren().add(secondary("Tidak ada kamar untuk tipe yang dipilih."));
        ScrollPane roomList = new ScrollPane(entries);
        roomList.setFitToWidth(true);
        roomList.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        roomList.setPrefViewportHeight(Math.min(360, Math.max(80, options.size() * 61)));
        dialog.getDialogPane().setContent(roomList);
        dialog.showAndWait();
    }

    static VBox page(Node... content) {
        VBox page = new VBox(16, content);
        page.setPadding(new Insets(25, 25, 32, 25));
        page.setBackground(background(Color.web("#F6F8FE"), 0));
        return page;
    }

    static HBox heading(String heading, String description, Button... actions) {
        Label title = text(heading, 30, FontWeight.BOLD, NAVY);
        VBox labels = new VBox(5, title, secondary(description));
        FlowPane buttons = new FlowPane(8, 6, actions);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.setMinWidth(Region.USE_PREF_SIZE);
        return horizontal(15, labels, spacer(), buttons);
    }

    static Button button(String caption, boolean primary, Runnable action) {
        Button button = new Button(caption);
        button.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 12));
        button.setTextFill(primary ? Color.WHITE : NAVY);
        button.setBackground(background(primary ? TEAL : SOFT, 7));
        button.setPadding(new Insets(8, 10, 8, 10));
        button.setMinHeight(32);
        button.setOnAction(event -> action.run());
        button.focusedProperty().addListener((property, old, focused) -> button.setBorder(focused
                ? new Border(new BorderStroke(Color.web("#0FA594"), BorderStrokeStyle.SOLID, new CornerRadii(7), BorderWidths.DEFAULT))
                : Border.EMPTY));
        return button;
    }

    static <T> Dialog<T> styledDialog(HotelOperations app, String title) {
        Dialog<T> dialog = new Dialog<>();
        if (app.owner() != null) dialog.initOwner(app.owner());
        dialog.setTitle(title);
        dialog.getDialogPane().setHeader(text(title, 20, FontWeight.BOLD, NAVY));
        dialog.getDialogPane().setPrefWidth(540);
        dialog.getDialogPane().setBackground(background(Color.WHITE, 0));
        return dialog;
    }

    static List<String> roomTypes(HotelData data, boolean includeAll) {
        List<String> types = new ArrayList<>();
        if (includeAll) types.add(ALL_TYPES);
        data.inventory().types().stream().map(type -> type.label()).forEach(types::add);
        return types;
    }

    private static List<Room> roomsByType(HotelData data, String type) {
        return data.rooms().stream().filter(room -> ALL_TYPES.equals(type) || room.type().equals(type)).toList();
    }

    static String money(long amount) {
        return "Rp " + NumberFormat.getIntegerInstance(INDONESIAN).format(amount);
    }

    private static String initials(String name) {
        return Arrays.stream(name.strip().split("\\s+")).filter(part -> !part.isEmpty()).limit(2)
                .map(part -> part.substring(0, 1).toUpperCase(INDONESIAN)).reduce("", String::concat);
    }

    static String floor(Room room) {
        return room.number().replaceFirst("[0-9]{2}$", "");
    }

    static Label badge(Status status) {
        return switch (status) {
            case BOOKED -> pill("• " + status.label(), Color.web("#7931D1"), Color.web("#F3EBFC"));
            case IN_HOUSE -> pill("• " + status.label(), Color.web("#2548BD"), Color.web("#EAF1FC"));
            case COMPLETE -> pill("• " + status.label(), TEAL, Color.web("#E4FAEF"));
            case CANCELLED -> pill("• " + status.label(), ERROR, Color.web("#FDF0EF"));
        };
    }

    static Label pill(String value, Color textColor, Color fill) {
        Label label = text(value, 11, FontWeight.SEMI_BOLD, textColor);
        label.setPadding(new Insets(5, 8, 5, 8));
        label.setWrapText(true);
        label.setBackground(background(fill, 12));
        return label;
    }

    static VBox detailRow(String title, String value) {
        Label content = text(value, 14, FontWeight.SEMI_BOLD, NAVY);
        content.setWrapText(true);
        return new VBox(3, secondary(title), content);
    }

    static Label text(String value, double size, FontWeight weight, Color color) {
        Label label = new Label(value);
        label.setFont(Font.font("Segoe UI", weight, size));
        label.setTextFill(color);
        return label;
    }

    static Label secondary(String value) {
        Label label = text(value, 13, FontWeight.NORMAL, MUTED);
        label.setWrapText(true);
        return label;
    }

    static Background background(Color color, double radius) {
        return new Background(new BackgroundFill(color, new CornerRadii(radius), Insets.EMPTY));
    }

    static VBox card(double spacing, Node... content) {
        VBox box = new VBox(spacing, content);
        box.setPadding(new Insets(18));
        box.setBackground(background(Color.WHITE, 12));
        box.setBorder(new Border(new BorderStroke(LINE, BorderStrokeStyle.SOLID, new CornerRadii(12), BorderWidths.DEFAULT)));
        return box;
    }

    static HBox horizontal(double spacing, Node... nodes) {
        HBox row = new HBox(spacing, nodes);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    static Region spacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    static VBox field(String title, Region input) {
        input.setMinWidth(0);
        input.setMaxWidth(Double.MAX_VALUE);
        if (input instanceof javafx.scene.control.TextInputControl) styleInput(input);
        Label label = text(title, 13, FontWeight.SEMI_BOLD, NAVY);
        label.setLabelFor(input);
        return new VBox(6, label, input);
    }

    static <T> ComboBox<T> combo(List<T> items, Function<T, String> caption) {
        ComboBox<T> combo = new ComboBox<>();
        combo.getItems().setAll(items);
        combo.setConverter(new StringConverter<>() {
            @Override public String toString(T value) { return value == null ? "" : caption.apply(value); }
            @Override public T fromString(String value) { return null; }
        });
        combo.setCellFactory(list -> comboCell(caption));
        combo.setButtonCell(comboCell(caption));
        styleInput(combo);
        combo.getEditor().setFont(Font.font("Segoe UI", 13));
        combo.getEditor().setBackground(Background.EMPTY);
        combo.setMaxWidth(Double.MAX_VALUE);
        return combo;
    }

    private static <T> ListCell<T> comboCell(Function<T, String> caption) {
        return new ListCell<>() {
            @Override protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : caption.apply(item));
                setFont(Font.font("Segoe UI", 13));
            }
        };
    }

    static DatePicker datePicker(LocalDate value) {
        DatePicker date = new DatePicker(value);
        date.setConverter(new StringConverter<>() {
            @Override public String toString(LocalDate day) { return day == null ? "" : DATE.format(day); }
            @Override public LocalDate fromString(String value) {
                String typed = value.strip();
                if (typed.isEmpty()) return null;
                try {
                    return LocalDate.parse(typed, DATE);
                } catch (DateTimeParseException ignored) {
                    return LocalDate.parse(typed);
                }
            }
        });
        styleInput(date);
        date.getEditor().setFont(Font.font("Segoe UI", 13));
        date.getEditor().setBackground(Background.EMPTY);
        date.setMaxWidth(Double.MAX_VALUE);
        return date;
    }

    private static void styleInput(Region control) {
        control.setMinHeight(39);
        control.setBackground(background(Color.web("#F0F4FC"), 8));
        control.setBorder(inputBorder(false));
        control.focusedProperty().addListener((property, old, focused) -> control.setBorder(inputBorder(focused)));
        if (control instanceof javafx.scene.control.TextInputControl text) {
            text.setFont(Font.font("Segoe UI", 13));
            text.setPadding(new Insets(8, 10, 8, 10));
        }
    }

    private static Border inputBorder(boolean focused) {
        return new Border(new BorderStroke(focused ? Color.web("#0FA594") : LINE, BorderStrokeStyle.SOLID,
                new CornerRadii(8), BorderWidths.DEFAULT));
    }

    static LocalDate committed(DatePicker control) {
        try {
            return control.getConverter().fromString(control.getEditor().getText());
        } catch (DateTimeParseException ignored) {
            throw new IllegalArgumentException("Masukkan tanggal yang valid, misalnya 25 Okt 2024.");
        }
    }

    static GridPane fieldsGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(14);
        for (int index = 0; index < 2; index++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(50);
            column.setFillWidth(true);
            grid.getColumnConstraints().add(column);
        }
        return grid;
    }

    static void gridColumns(GridPane grid, double... percents) {
        for (double percent : percents) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(percent);
            column.setFillWidth(true);
            grid.getColumnConstraints().add(column);
        }
    }

    static ScrollPane horizontalScroll(Node content) {
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(false);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setBackground(Background.EMPTY);
        scroll.prefHeightProperty().bind(Bindings.createDoubleBinding(
                () -> content.getLayoutBounds().getHeight() + 15, content.layoutBoundsProperty()));
        scroll.setMinHeight(Region.USE_PREF_SIZE);
        return scroll;
    }
}
