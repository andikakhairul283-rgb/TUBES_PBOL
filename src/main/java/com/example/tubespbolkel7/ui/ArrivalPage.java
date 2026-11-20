package com.example.tubespbolkel7.ui;

import com.example.tubespbolkel7.model.HotelData.Booking;
import com.example.tubespbolkel7.model.HotelData.Status;
import com.example.tubespbolkel7.model.HotelData;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.print.PrinterJob;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

final class ArrivalPage {
    private static final Locale INDONESIAN = Locale.forLanguageTag("id-ID");
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", INDONESIAN);
    private static final Color NAVY = Color.web("#112D4F");
    private static final Color MUTED = Color.web("#536176");
    private static final Color TEAL = Color.web("#006A60");
    private static final Color PALE = Color.web("#EDF2FA");
    private static final Color HAIRLINE = Color.web("#E4EBF6");
    private static final Color WHITE = Color.WHITE;
    private static final Color DANGER = Color.web("#A42A2F");
    private static final CornerRadii CORNERS = new CornerRadii(10);
    private static final List<String> PAYMENT_METHODS = List.of("Tunai", "Transfer Bank", "Kartu Debit / Kredit", "QRIS");
    private static final int ROWS_PER_PAGE = 5;
    private static final String ALL_DATES = "Semua jadwal";
    private static final String TODAY = "Jadwal hari ini";
    private static final String NEXT_WEEK = "7 hari ke depan";
    private static final String ALL_BILLS = "Semua tagihan";
    private static final String OPEN_BILLS = "Belum lunas";
    private static final String PAID_BILLS = "Lunas";

    static Node view(HotelOperations app, HotelRoute route) {
        return new Desk(app, Mode.of(route));
    }

    private enum Mode {
        IN(HotelRoute.CHECK_IN, "Check-in", "Tamu dijadwalkan tiba"),
        OUT(HotelRoute.CHECK_OUT, "Check-out", "Tamu selesai masa inap"),
        HOUSE(HotelRoute.IN_HOUSE, "Tamu In-house", "Tamu sedang menginap"),
        PAY(HotelRoute.PAYMENTS, "Pembayaran", "Daftar tagihan");

        final HotelRoute route;
        final String label;
        final String listing;

        Mode(HotelRoute route, String label, String listing) {
            this.route = route;
            this.label = label;
            this.listing = listing;
        }

        static Mode of(HotelRoute route) {
            return switch (route) {
                case CHECK_IN -> IN;
                case CHECK_OUT -> OUT;
                case IN_HOUSE -> HOUSE;
                case PAYMENTS -> PAY;
                default -> throw new IllegalArgumentException("Rute tamu tidak dikenal: " + route);
            };
        }
    }

    private static final class Desk extends VBox {
        private final HotelOperations app;
        private final HotelData data;
        private final Mode mode;
        private final TextField search = new TextField();
        private final ComboBox<String> schedule = select(List.of(ALL_DATES, TODAY, NEXT_WEEK), ALL_DATES, "arrival-schedule");
        private final Button filter;
        private final VBox metrics = new VBox();
        private final VBox queue = card();
        private final VBox folio = card();
        private final VBox workArea = new VBox(16);
        private final HBox split = new HBox(16, queue, folio);
        private final Label feedback = muted("");
        private String billFilter = ALL_BILLS;
        private String selected;
        private int page;
        private boolean stacked;

        Desk(HotelOperations app, Mode mode) {
            this.app = app;
            this.data = app.data();
            this.mode = mode;
            this.selected = app.selected();
            setSpacing(17);
            setPadding(new Insets(22, 24, 26, 24));
            setMinWidth(0);
            fill(this, Color.web("#F8F9FF"));

            search.setId("arrivals-search");
            search.setPromptText("Cari nomor kamar, kode, atau nama tamu…");
            search.setPrefHeight(38);
            search.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(search, Priority.ALWAYS);
            search.textProperty().addListener((observable, previous, value) -> resetFilters());
            schedule.valueProperty().addListener((observable, previous, value) -> resetFilters());

            filter = button("☷  Filter", "arrivals-filter", false, this::showFilters);
            feedback.setId("arrivals-feedback");
            feedback.managedProperty().bind(feedback.visibleProperty());
            feedback.setVisible(false);

            queue.setMinWidth(0);
            folio.setMinWidth(0);
            folio.setPrefWidth(365);
            folio.setMaxWidth(420);
            split.setAlignment(Pos.TOP_LEFT);
            HBox.setHgrow(queue, Priority.ALWAYS);
            workArea.getChildren().add(split);
            widthProperty().addListener((observable, previous, width) -> {
                boolean narrow = width.doubleValue() > 0 && width.doubleValue() < 850;
                if (narrow != stacked) {
                    stacked = narrow;
                    arrangePanels();
                }
            });

            Label heading = label(mode == Mode.PAY ? "Pembayaran" : "Tamu & Check-in", 29, FontWeight.BOLD, NAVY);
            Label subtitle = muted(mode == Mode.PAY
                    ? "Ringkasan tagihan, uang muka, dan pelunasan tamu."
                    : "Proses kedatangan, keberangkatan, dan tamu yang sedang menginap.");
            HBox header = line(new VBox(4, heading, subtitle), spacer(), badge(dateText(data.today()), TEAL, PALE));
            HBox toolbar = line(search, filter);
            if (mode == Mode.IN || mode == Mode.OUT) {
                toolbar.getChildren().add(1, schedule);
            }
            if (mode == Mode.IN) {
                toolbar.getChildren().add(button(app.isOwner() ? "+ Tamu Walk-in" : "+ Reservasi", "arrival-new-reservation", true, () -> {
                    app.select(null);
                    app.open(app.isOwner() ? HotelRoute.WALK_IN : HotelRoute.NEW_RESERVATION);
                }));
            }
            getChildren().addAll(header, metrics, tabs(), card(toolbar), feedback, workArea);
            List<Booking> initial = filteredBookings();
            for (int index = 0; index < initial.size(); index++) {
                if (initial.get(index).code().equals(selected)) {
                    page = index / ROWS_PER_PAGE;
                    break;
                }
            }
            refresh(true);
        }

        private Node tabs() {
            FlowPane tabs = new FlowPane(10, 8);
            for (Mode target : Mode.values()) {
                tabs.getChildren().add(button(target.label, "arrival-tab-" + target.name().toLowerCase(Locale.ROOT),
                        target == mode, () -> app.open(target.route)));
            }
            return tabs;
        }

        private void arrangePanels() {
            workArea.getChildren().clear();
            split.getChildren().clear();
            if (stacked) {
                folio.setMaxWidth(Double.MAX_VALUE);
                workArea.getChildren().addAll(queue, folio);
            } else {
                folio.setMaxWidth(420);
                split.getChildren().addAll(queue, folio);
                workArea.getChildren().add(split);
            }
        }

        private void resetFilters() {
            page = 0;
            refresh(false);
        }

        private void showFilters() {
            ContextMenu menu = new ContextMenu();
            ToggleGroup group = new ToggleGroup();
            for (String choice : List.of(ALL_BILLS, OPEN_BILLS, PAID_BILLS)) {
                RadioMenuItem item = new RadioMenuItem(choice);
                item.setToggleGroup(group);
                item.setSelected(choice.equals(billFilter));
                item.setOnAction(event -> {
                    billFilter = choice;
                    filter.setText(choice.equals(ALL_BILLS) ? "☷  Filter" : "☷  " + choice);
                    resetFilters();
                });
                menu.getItems().add(item);
            }
            menu.show(filter, Side.BOTTOM, 0, 5);
        }

        private void refresh(boolean refreshFolio) {
            List<Booking> filtered = filteredBookings();
            int lastPage = Math.max(0, (filtered.size() - 1) / ROWS_PER_PAGE);
            page = Math.min(page, lastPage);
            Booking selection = filtered.stream().filter(booking -> Objects.equals(booking.code(), selected))
                    .findFirst().orElse(null);
            int first = page * ROWS_PER_PAGE;
            int end = Math.min(filtered.size(), first + ROWS_PER_PAGE);
            List<Booking> visible = filtered.subList(first, end);
            if (selection == null || !visible.contains(selection)) {
                selection = visible.isEmpty() ? null : visible.getFirst();
            }
            String code = selection == null ? null : selection.code();
            if (refreshFolio || !Objects.equals(code, selected) || folio.getChildren().isEmpty()) {
                showFolio(selection);
            }
            selected = code;
            showMetrics();
            showQueue(visible, filtered.size(), first, end, lastPage);
        }

        private List<Booking> filteredBookings() {
            String query = search.getText().strip().toLowerCase(INDONESIAN);
            return data.bookings().stream()
                    .filter(this::matchesMode)
                    .filter(this::matchesSchedule)
                    .filter(booking -> switch (billFilter) {
                        case OPEN_BILLS -> data.remaining(booking) > 0;
                        case PAID_BILLS -> data.remaining(booking) == 0;
                        default -> true;
                    })
                    .filter(booking -> query.isEmpty() || booking.code().toLowerCase(INDONESIAN).contains(query)
                            || booking.guest().toLowerCase(INDONESIAN).contains(query)
                            || booking.room().number().toLowerCase(INDONESIAN).contains(query))
                    .sorted(java.util.Comparator.comparing(mode == Mode.OUT ? Booking::departure : Booking::arrival)
                            .thenComparing(Booking::code))
                    .toList();
        }

        private boolean matchesMode(Booking booking) {
            return switch (mode) {
                case IN -> booking.status() == Status.BOOKED;
                case OUT, HOUSE -> booking.status() == Status.IN_HOUSE;
                case PAY -> booking.status() != Status.CANCELLED;
            };
        }

        private boolean matchesSchedule(Booking booking) {
            if (mode != Mode.IN && mode != Mode.OUT || ALL_DATES.equals(schedule.getValue())) {
                return true;
            }
            LocalDate date = mode == Mode.IN ? booking.arrival() : booking.departure();
            LocalDate today = data.today();
            return TODAY.equals(schedule.getValue()) ? date.equals(today)
                    : !date.isBefore(today) && date.isBefore(today.plusDays(7));
        }

        private void showMetrics() {
            List<Booking> all = data.bookings();
            HBox row = line();
            if (mode == Mode.PAY) {
                List<Booking> billable = all.stream().filter(this::matchesMode).toList();
                long pending = billable.stream().filter(item -> data.remaining(item) > 0).count();
                long due = billable.stream().mapToLong(data::remaining).sum();
                long paid = billable.stream().mapToLong(item -> data.total(item) - data.remaining(item)).sum();
                row.getChildren().setAll(metric("TAGIHAN BELUM LUNAS", pending + " reservasi"),
                        metric("SISA TAGIHAN", rupiah(due)), metric("PEMBAYARAN TERCATAT", rupiah(paid)));
            } else {
                long arrivals = all.stream().filter(item -> item.status() == Status.BOOKED
                        && item.arrival().equals(data.today())).count();
                long leaving = all.stream().filter(item -> item.status() == Status.IN_HOUSE
                        && !item.departure().isAfter(data.today())).count();
                long housed = all.stream().filter(item -> item.status() == Status.IN_HOUSE).count();
                row.getChildren().setAll(metric("CHECK-IN HARI INI", Long.toString(arrivals)),
                        metric("CHECK-OUT HARI INI", Long.toString(leaving)), metric("TAMU IN-HOUSE", Long.toString(housed)));
            }
            metrics.getChildren().setAll(row);
        }

        private void showQueue(List<Booking> visible, int total, int first, int end, int lastPage) {
            Label count = badge(total + " reservasi", TEAL, PALE);
            HBox header = line(title(mode.listing), spacer(), count);
            GridPane fields = columns(caption("KAMAR"), caption("TAMU"), caption("CHECK-IN"),
                    caption("CHECK-OUT"), caption(mode == Mode.IN ? "STATUS" : "STATUS BILL"), caption("AKSI"));
            fields.setPadding(new Insets(13, 8, 13, 8));
            fill(fields, PALE);
            VBox results = new VBox();
            if (visible.isEmpty()) {
                Label empty = muted(emptyQueue());
                empty.setPadding(new Insets(18, 8, 18, 8));
                results.getChildren().add(empty);
            }
            for (Booking item : visible) {
                boolean current = item.code().equals(selected);
                Button choose = button(current ? "Dipilih" : "Pilih", "arrival-select-" + item.code(), current, () -> {
                    selected = item.code();
                    app.select(item.code());
                    feedback.setVisible(false);
                    refresh(true);
                });
                Label status = mode == Mode.IN
                        ? badge(item.status().label(), Color.web("#6B3CB6"), Color.web("#F2ECFC"))
                        : billBadge(item);
                GridPane row = columns(new VBox(3, label(item.room().number()), muted(item.room().type())),
                        new VBox(3, label(item.guest()), muted(item.code())), date(item.arrival()), date(item.departure()), status, choose);
                row.setPadding(new Insets(15, 8, 15, 8));
                if (current) {
                    fill(row, Color.web("#F3F7FE"));
                }
                results.getChildren().addAll(row, rule());
            }
            Button previous = button("‹", "arrivals-previous-page", false, () -> {
                page--;
                refresh(false);
            });
            previous.setAccessibleText("Halaman sebelumnya");
            previous.setDisable(page == 0);
            Button next = button("›", "arrivals-next-page", false, () -> {
                page++;
                refresh(false);
            });
            next.setAccessibleText("Halaman berikutnya");
            next.setDisable(page >= lastPage);
            String visibleCount = total == 0 ? "0 hasil" : (first + 1) + "–" + end + " dari " + total + " reservasi";
            HBox pagination = line(muted(visibleCount), spacer(), previous, caption((page + 1) + " / " + (lastPage + 1)), next);
            queue.getChildren().setAll(header, new VBox(0, fields, results), pagination);
        }

        private String emptyQueue() {
            if (data.bookings().stream().anyMatch(this::matchesMode)) {
                return "Tidak ada reservasi yang cocok dengan pencarian dan filter.";
            }
            return switch (mode) {
                case IN -> "Belum ada tamu yang menunggu check-in. Tambahkan reservasi untuk membuat jadwal kedatangan.";
                case OUT, HOUSE -> "Belum ada tamu yang sedang menginap. Selesaikan check-in terlebih dahulu.";
                case PAY -> "Belum ada tagihan. Tagihan muncul setelah reservasi dibuat.";
            };
        }

        private Label billBadge(Booking item) {
            return data.remaining(item) == 0
                    ? badge("Lunas", TEAL, Color.web("#DDF8EF"))
                    : badge("Belum lunas", DANGER, Color.web("#FCE6E4"));
        }

        private void showFolio(Booking booking) {
            folio.getChildren().clear();
            if (booking == null) {
                folio.getChildren().addAll(title("Folio tamu"), muted("Pilih reservasi untuk melihat rinciannya."));
                return;
            }
            folio.getChildren().addAll(caption("FOLIO TAMU"), title(mode == Mode.IN ? "Rincian kedatangan" : "Ringkasan Tagihan"),
                    badge("Kamar " + booking.room().number() + " · " + booking.guest(), TEAL, PALE),
                    rule());
            if (mode == Mode.IN) {
                checkInForm(booking);
                return;
            }
            folio.getChildren().add(charges(booking));
            if (mode == Mode.HOUSE) {
                Button services = button("+ Tambah layanan", "arrival-add-service", true, () -> enter(booking, HotelRoute.SERVICES));
                Button checkout = button("Proses check-out", "arrival-to-checkout", false, () -> enter(booking, HotelRoute.CHECK_OUT));
                folio.getChildren().addAll(wide(services), wide(checkout));
            } else {
                paymentForm(booking);
                if (mode == Mode.OUT) {
                    checkOutForm(booking);
                }
            }
            Button detail = button("Lihat detail reservasi", "arrival-detail", false,
                    () -> ReservationPage.detail(app, booking));
            folio.getChildren().add(wide(detail));
            if (mode != Mode.HOUSE) {
                folio.getChildren().add(wide(button("Cetak tagihan sementara", "arrival-print", false, () -> print(booking))));
            }
        }

        private void checkInForm(Booking booking) {
            var hotel = data.settings().hotel();
            var policy = data.settings().policy();
            folio.getChildren().addAll(info("Kode reservasi", booking.code()), info("Tipe kamar", booking.room().type()),
                    info("Jadwal check-in", dateText(booking.arrival()) + " · " + hotel.arrival()),
                    info("Jadwal check-out", dateText(booking.departure()) + " · " + hotel.departure()),
                    info("Sisa tagihan", rupiah(data.remaining(booking))));
            if (!policy.checkInNote().isBlank()) {
                folio.getChildren().add(muted("Kebijakan · " + policy.checkInNote()));
            }
            if (booking.notes() != null && !booking.notes().isBlank()) {
                folio.getChildren().add(muted("Catatan · " + booking.notes()));
            }
            CheckBox identity = new CheckBox("Identitas tamu sudah diverifikasi");
            identity.setId("arrival-verify-identity");
            CheckBox key = new CheckBox("Kunci kamar siap diserahkan");
            key.setId("arrival-verify-key");
            TextField keycard = new TextField();
            keycard.setId("arrival-keycard-id");
            keycard.setPromptText("ID keycard (opsional)");
            Button submit = button("Konfirmasi check-in", "arrival-confirm-check-in", true,
                    () -> commit(() -> data.checkIn(booking.code(), keycard.getText().strip()), "Check-in " + booking.guest() + " berhasil."));
            submit.disableProperty().bind(policy.verifyIdentity()
                    ? identity.selectedProperty().not().or(key.selectedProperty().not()) : key.selectedProperty().not());
            folio.getChildren().addAll(rule(), title("Verifikasi kedatangan"));
            if (policy.verifyIdentity()) folio.getChildren().add(identity);
            if (app.isOwner()) {
                var guest = data.guestFor(booking.code());
                if (guest != null && !guest.identity().isBlank()) {
                    String id = guest.identity();
                    folio.getChildren().add(info("Identitas terdaftar", "•••• " + id.substring(Math.max(0, id.length() - 4))));
                }
                HBox scan = line(keycard, button("Pindai", "arrival-scan-keycard", false, () -> scanKeycard(keycard)));
                HBox.setHgrow(keycard, Priority.ALWAYS);
                folio.getChildren().addAll(caption("Kunci Kartu RFID"), scan);
            }
            folio.getChildren().addAll(key, wide(submit),
                    wide(button("Lihat detail reservasi", "arrival-detail", false, () -> ReservationPage.detail(app, booking))),
                    wide(button("Buka pembayaran", "arrival-to-payment", false, () -> enter(booking, HotelRoute.PAYMENTS))));
            securityDepositForm(booking, false);
            if (app.isOwner()) {
                folio.getChildren().addAll(wide(button("Cetak Kartu Registrasi", "arrival-registration-card", false,
                                () -> printRegistration(booking))),
                        wide(button("Batalkan reservasi", "arrival-cancel-booking", false, () -> cancelBooking(booking))));
            }
        }

        private void scanKeycard(TextField target) {
            TextField scanned = PageComponents.text("arrival-scan-input", target.getText(), "Pindai kartu atau ketik ID");
            PageComponents.edit(app.owner(), "Pindai Keycard RFID", PageComponents.card(
                            muted("Gunakan pembaca yang memasukkan ID sebagai ketikan, atau isi ID kartu secara manual."), scanned),
                    "Gunakan ID", () -> {
                        String id = scanned.getText().strip();
                        if (!id.matches("[A-Za-z0-9-]{3,40}")) throw new IllegalArgumentException("Isi ID kartu yang valid.");
                        target.setText(id);
                    }, target::requestFocus);
        }

        private void cancelBooking(Booking booking) {
            String policy = data.settings().policy().cancellationNote();
            PageComponents.edit(app.owner(), "Batalkan Reservasi", PageComponents.card(title(booking.code()),
                            muted(policy.isBlank() ? "Pembayaran yang tercatat tetap ada dalam riwayat." : policy)),
                    "Konfirmasi pembatalan", () -> data.cancel(booking.code()), () -> refresh(true));
        }

        private VBox charges(Booking booking) {
            Label heading = label("SISA TAGIHAN", 11, FontWeight.BOLD, Color.web("#A6C2E4"));
            Label amount = label(rupiah(data.remaining(booking)), 22, FontWeight.BOLD, WHITE);
            VBox balance = new VBox(4, heading, amount);
            fill(balance, Color.web("#1E3A5F"));
            balance.setPadding(new Insets(13, 15, 13, 15));
            return new VBox(10,
                    info("Kamar (" + booking.nights() + " malam)", rupiah(data.subtotal(booking))),
                    info("Layanan tambahan", rupiah(data.servicesTotal(booking))),
                    info("Pajak & layanan", rupiah(data.tax(booking))),
                    rule(), info("Total tagihan", rupiah(data.total(booking))),
                    info("DP dan pembayaran masuk", rupiah(data.total(booking) - data.remaining(booking))),
                    balance);
        }

        private void paymentForm(Booking booking) {
            if (data.remaining(booking) == 0) {
                folio.getChildren().add(billBadge(booking));
                return;
            }
            ComboBox<String> method = select(PAYMENT_METHODS, PAYMENT_METHODS.getFirst(), "arrival-payment-method");
            TextField reference = new TextField();
            reference.setId("arrival-payment-reference");
            reference.setPromptText("Nomor bukti atau ID transaksi");
            reference.setPrefHeight(36);
            TextField amount = new TextField(Long.toString(data.remaining(booking)));
            amount.setId("arrival-payment-amount");
            amount.setPrefHeight(36);
            Label error = label("", 12, FontWeight.SEMI_BOLD, DANGER);
            error.setId("arrival-payment-error");
            error.managedProperty().bind(error.visibleProperty());
            error.setVisible(false);
            Button pay = button(mode == Mode.OUT ? "Lanjut ke pembayaran" : "Catat pembayaran",
                    "arrival-submit-payment", true, () -> {
                        try {
                            long value = Long.parseLong(amount.getText().strip().replace(".", ""));
                            if (value <= 0 || value > data.remaining(booking)) {
                                throw new IllegalArgumentException("Nominal harus lebih dari Rp 0 dan tidak melebihi sisa tagihan.");
                            }
                            String proof = reference.getText().strip();
                            String paymentMethod = method.getValue();
                            if (paymentMethod == null) {
                                throw new IllegalArgumentException("Pilih metode pembayaran.");
                            }
                            if (!paymentMethod.equals("Tunai") && proof.isBlank()) {
                                throw new IllegalArgumentException("Isi nomor bukti atau ID transaksi untuk pembayaran nontunai.");
                            }
                            confirmPayment(booking, value, paymentMethod, proof);
                        } catch (NumberFormatException exception) {
                            paymentError(error, "Masukkan nominal dalam rupiah, misalnya 50000.");
                        } catch (IllegalArgumentException exception) {
                            paymentError(error, exception.getMessage());
                        }
                    });
            folio.getChildren().addAll(new VBox(5, caption("Metode pembayaran"), method),
                    new VBox(5, caption("Nomor bukti / ID transaksi"), reference),
                    new VBox(5, caption("Nominal pembayaran"), amount), error, wide(pay));
        }

        private void confirmPayment(Booking booking, long amount, String method, String reference) {
            ButtonType accept = new ButtonType("Konfirmasi pembayaran", ButtonBar.ButtonData.OK_DONE);
            ButtonType cancel = new ButtonType("Batal", ButtonBar.ButtonData.CANCEL_CLOSE);
            Dialog<ButtonType> dialog = dialog("Konfirmasi Pembayaran", title(booking.guest()),
                    info("Reservasi", booking.code()), info("Metode", method), info("Nominal", rupiah(amount)));
            dialog.getDialogPane().getButtonTypes().setAll(cancel, accept);
            dialog.getDialogPane().lookupButton(accept).setId("arrival-confirm-payment");
            dialog.showAndWait().filter(accept::equals).ifPresent(result -> commit(
                    () -> data.pay(booking.code(), amount, method, reference), "Pembayaran " + rupiah(amount) + " tercatat."));
        }

        private void checkOutForm(Booking booking) {
            CheckBox inspected = new CheckBox("Kamar diperiksa dan kunci diterima");
            inspected.setId("arrival-verify-inspection");
            Button checkout = button("Selesaikan check-out", "arrival-confirm-check-out", true, () -> commit(
                    () -> data.checkOut(booking.code()), "Check-out " + booking.guest() + " berhasil."));
            checkout.disableProperty().bind(inspected.selectedProperty().not());
            folio.getChildren().addAll(rule(), title("Inspeksi kamar"), inspected);
            securityDepositForm(booking, true);
            if (data.remaining(booking) > 0) {
                checkout.disableProperty().unbind();
                checkout.setDisable(true);
                folio.getChildren().add(muted("Pelunasan diperlukan untuk menyelesaikan check-out."));
            }
            folio.getChildren().add(wide(checkout));
        }

        private void securityDepositForm(Booking booking, boolean departure) {
            String code = booking.code();
            var deposit = data.securityDeposit(code);
            if (deposit != null) {
                folio.getChildren().add(info(deposit.returned() ? "Jaminan dikembalikan" : "Jaminan tercatat", rupiah(deposit.amount())));
                if (!deposit.returned()) {
                    folio.getChildren().add(wide(button("Catat pengembalian jaminan", "arrival-return-deposit", false, () ->
                            PageComponents.edit(app.owner(), "Pengembalian Jaminan",
                                    PageComponents.card(PageComponents.caption("Konfirmasi pengembalian " + rupiah(deposit.amount())
                                            + " untuk " + booking.guest() + ".")), "Catat pengembalian",
                                    () -> data.returnSecurityDeposit(code), () -> refresh(true)))));
                }
                return;
            }
            long suggested = data.suggestedSecurityDeposit(code);
            if (departure || suggested == 0) return;
            TextField amount = new TextField(Long.toString(suggested));
            amount.setId("arrival-security-deposit-amount");
            folio.getChildren().addAll(info("Jaminan default", rupiah(suggested)), amount,
                    wide(button("Catat jaminan", "arrival-collect-deposit", false, () -> commit(
                            () -> data.collectSecurityDeposit(code, RoomPages.typeRate(amount.getText())), "Jaminan dicatat."))));
        }

        private void enter(Booking booking, HotelRoute route) {
            app.select(booking.code());
            app.open(route);
        }

        private void commit(Runnable action, String message) {
            try {
                action.run();
                refresh(true);
                feedback.setTextFill(TEAL);
                feedback.setText(message);
            } catch (IllegalArgumentException exception) {
                feedback.setTextFill(DANGER);
                feedback.setText(exception.getMessage());
            }
            feedback.setVisible(true);
        }

        private void print(Booking booking) {
            VBox receipt = new VBox(12, title("CheckIn.id"), caption("TAGIHAN SEMENTARA"),
                    info("Reservasi", booking.code()), info("Tamu", booking.guest()),
                    info("Kamar", booking.room().number()), charges(booking));
            printDocument("Pratinjau Cetak Tagihan", receipt);
        }

        private void printRegistration(Booking booking) {
            var hotel = data.settings().hotel();
            VBox registration = new VBox(12, title(hotel.name()), caption("KARTU REGISTRASI TAMU"),
                    info("Reservasi", booking.code()), info("Nama tamu", booking.guest()),
                    info("Unit kamar", booking.room().number() + " · " + booking.room().type()),
                    info("Check-in", dateText(booking.arrival()) + " · " + hotel.arrival()),
                    info("Check-out", dateText(booking.departure()) + " · " + hotel.departure()),
                    info("Uang muka", rupiah(booking.deposit())), muted("Tanda tangan tamu ____________________"));
            printDocument("Pratinjau Kartu Registrasi", registration);
        }

        private void printDocument(String heading, VBox document) {
            ButtonType print = new ButtonType("Cetak", ButtonBar.ButtonData.OK_DONE);
            ButtonType close = new ButtonType("Tutup", ButtonBar.ButtonData.CANCEL_CLOSE);
            document.setPadding(new Insets(15));
            Dialog<ButtonType> preview = dialog(heading, document);
            preview.getDialogPane().getButtonTypes().setAll(close, print);
            preview.showAndWait().filter(print::equals).ifPresent(result -> {
                PrinterJob printer = PrinterJob.createPrinterJob();
                if (printer == null) {
                    showInfo("Printer belum tersedia di perangkat ini.");
                } else if (printer.showPrintDialog(app.owner())) {
                    if (printer.printPage(document)) {
                        printer.endJob();
                    } else {
                        printer.cancelJob();
                        showInfo("Tagihan belum berhasil dicetak.");
                    }
                }
            });
        }

        private void showInfo(String message) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION, message);
            alert.setTitle("Cetak tagihan");
            if (app.owner() != null) {
                alert.initOwner(app.owner());
            }
            alert.showAndWait();
        }

        private Dialog<ButtonType> dialog(String title, Node... content) {
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle(title);
            if (app.owner() != null) {
                dialog.initOwner(app.owner());
            }
            dialog.getDialogPane().setContent(card(content));
            dialog.getDialogPane().setPrefWidth(480);
            return dialog;
        }
    }

    private static Label label(String text, double size, FontWeight weight, Color color) {
        Label label = new Label(text);
        label.setTextFill(color);
        label.setFont(Font.font("Segoe UI", weight, size));
        label.setWrapText(true);
        return label;
    }

    private static Label label(String text) {
        return label(text, 14, FontWeight.NORMAL, NAVY);
    }

    private static Label title(String text) {
        return label(text, 19, FontWeight.BOLD, NAVY);
    }

    private static Label muted(String text) {
        return label(text, 13, FontWeight.NORMAL, MUTED);
    }

    private static Label caption(String text) {
        return label(text, 12, FontWeight.SEMI_BOLD, MUTED);
    }

    private static Button button(String text, String id, boolean strong, Runnable action) {
        Button button = new Button(text);
        button.setId(id);
        button.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 13));
        button.setCursor(Cursor.HAND);
        button.setTextFill(strong ? WHITE : NAVY);
        button.setPadding(new Insets(10, 14, 10, 14));
        button.setBackground(new Background(new BackgroundFill(strong ? TEAL : PALE, new CornerRadii(8), Insets.EMPTY)));
        button.focusedProperty().addListener((observable, previous, focused) -> button.setBorder(focused
                ? new Border(new BorderStroke(Color.web("#2C9F8F"), BorderStrokeStyle.SOLID, new CornerRadii(8), BorderWidths.DEFAULT))
                : Border.EMPTY));
        button.setOnAction(event -> action.run());
        return button;
    }

    private static void fill(Region region, Color color) {
        region.setBackground(new Background(new BackgroundFill(color, CORNERS, Insets.EMPTY)));
    }

    private static VBox card(Node... children) {
        VBox box = new VBox(14, children);
        box.setPadding(new Insets(20));
        fill(box, WHITE);
        box.setBorder(new Border(new BorderStroke(HAIRLINE, BorderStrokeStyle.SOLID, CORNERS, BorderWidths.DEFAULT)));
        return box;
    }

    private static Label badge(String text, Color textColor, Color fillColor) {
        Label badge = label(text, 12, FontWeight.SEMI_BOLD, textColor);
        fill(badge, fillColor);
        badge.setPadding(new Insets(5, 9, 5, 9));
        return badge;
    }

    private static Region spacer() {
        Region region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        return region;
    }

    private static HBox line(Node... children) {
        HBox box = new HBox(10, children);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private static Region rule() {
        Region rule = new Region();
        rule.setMinHeight(1);
        rule.setBackground(Background.fill(HAIRLINE));
        return rule;
    }

    private static Label date(LocalDate value) {
        return label(dateText(value));
    }

    private static String dateText(LocalDate value) { return SHORT_DATE.format(value); }

    private static String rupiah(long amount) {
        return "Rp " + java.text.NumberFormat.getIntegerInstance(INDONESIAN).format(amount);
    }

    private static HBox info(String key, String value) {
        return line(muted(key), spacer(), label(value, 13, FontWeight.SEMI_BOLD, NAVY));
    }

    private static VBox metric(String name, String value) {
        VBox metric = card(caption(name), label(value, 26, FontWeight.BOLD, NAVY));
        metric.setMinWidth(0);
        metric.setPrefWidth(0);
        metric.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(metric, Priority.ALWAYS);
        return metric;
    }

    private static Button wide(Button button) {
        button.setMaxWidth(Double.MAX_VALUE);
        return button;
    }

    private static void paymentError(Label label, String message) {
        label.setText(message);
        label.setVisible(true);
    }

    private static <T> ComboBox<T> select(List<T> values, T selected, String id) {
        ComboBox<T> combo = new ComboBox<>();
        combo.getItems().setAll(values);
        combo.setValue(selected);
        combo.setId(id);
        combo.setPrefHeight(36);
        combo.setMaxWidth(Double.MAX_VALUE);
        return combo;
    }

    private static GridPane columns(Node... cells) {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        double[] widths = {13, 24, 14, 16, 19, 14};
        for (int i = 0; i < cells.length; i++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(widths[i]);
            grid.getColumnConstraints().add(column);
            grid.add(cells[i], i, 0);
        }
        return grid;
    }

    private ArrivalPage() { }
}
