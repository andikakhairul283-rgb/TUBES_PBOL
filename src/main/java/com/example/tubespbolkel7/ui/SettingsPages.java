package com.example.tubespbolkel7.ui;

import com.example.tubespbolkel7.model.HotelSettings;
import com.example.tubespbolkel7.model.Role;
import com.example.tubespbolkel7.model.RoomInventory;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import static com.example.tubespbolkel7.model.HotelSettings.Hotel;
import static com.example.tubespbolkel7.model.HotelSettings.Login;
import static com.example.tubespbolkel7.model.HotelSettings.Policy;
import static com.example.tubespbolkel7.model.HotelSettings.Pricing;
import static com.example.tubespbolkel7.model.HotelSettings.Profile;
import static com.example.tubespbolkel7.ui.PageComponents.*;

final class SettingsPages {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.forLanguageTag("id-ID"));

    private final HotelOperations app;
    private final HotelSettings state;

    SettingsPages(HotelOperations app) {
        this.app = app;
        this.state = app.data().settings();
    }

    Node view(HotelRoute route) {
        VBox root = page("Pengaturan", "Profil hotel, tarif, kebijakan, dan pengguna.");
        FlowPane tabs = new FlowPane(9, 8);
        for (HotelRoute item : List.of(HotelRoute.PROFILE, HotelRoute.RATES, HotelRoute.POLICIES, HotelRoute.USERS, HotelRoute.MY_PROFILE)) {
            tabs.getChildren().add(button("settings-" + item.name().toLowerCase(Locale.ROOT).replace('_', '-'),
                    item == HotelRoute.PROFILE ? "Profil Hotel" : item.title,
                    item == route,
                    () -> app.open(item)));
        }
        root.getChildren().addAll(tabs, switch (route) {
            case PROFILE -> hotel();
            case RATES -> rates();
            case POLICIES -> policies();
            case USERS -> app.isOwner() ? OwnerAccounts.view(app, users()) : users();
            case MY_PROFILE -> myProfile();
            default -> throw new IllegalArgumentException("Tab pengaturan tidak dikenal.");
        });
        return root;
    }

    private VBox hotel() {
        Hotel initial = state.hotel();
        TextField name = text("hotel-name", initial.name(), "Nama hotel");
        TextField phone = text("hotel-phone", initial.phone(), "Nomor telepon");
        TextField email = text("hotel-email", initial.email(), "Email hotel");
        TextField city = text("hotel-city", initial.city(), "Kota");
        TextField address = text("hotel-address", initial.address(), "Alamat lengkap");
        TextField arrival = text("hotel-check-in", initial.arrival().toString(), "HH:mm");
        TextField departure = text("hotel-check-out", initial.departure().toString(), "HH:mm");

        ImageView logo = new ImageView(initial.logo());
        logo.setPreserveRatio(true);
        logo.setFitWidth(86);
        logo.setFitHeight(56);

        Label feedback = caption("");
        Button changeLogo = button("hotel-logo", "Ubah Logo", false, () -> chooseLogo(logo::setImage, feedback));
        Button removeLogo = button("hotel-logo-remove", "Hapus Logo", false, () -> logo.setImage(null));
        removeLogo.disableProperty().bind(logo.imageProperty().isNull());

        VBox fields = card(title("Informasi Umum Properti"),
                row(logo, new VBox(6, caption("Logo PNG atau JPG hingga 2 MB"), row(changeLogo, removeLogo))),
                field("Nama hotel", name),
                field("Nomor telepon", phone),
                field("Email", email),
                field("Kota", city),
                field("Alamat", address),
                row(field("Jam check-in", arrival), field("Jam check-out", departure)),
                feedback,
                formActions(HotelRoute.PROFILE, feedback, () -> state.saveHotel(new Hotel(
                        name.getText().strip(), phone.getText().strip(), email.getText().strip(),
                        city.getText().strip(), address.getText().strip(),
                        time(arrival.getText()), time(departure.getText()), logo.getImage()))));
        fields.setMaxWidth(810);
        return fields;
    }

    private VBox rates() {
        Pricing pricing = state.pricing();
        TableView<RoomInventory.Type> table = table("settings-rate-table", "Belum ada tarif.");
        table.getColumns().addAll(List.of(
                column("Tipe kamar", RoomInventory.Type::label),
                column("Kapasitas", type -> type.capacity() + " tamu"),
                column("Tarif hari kerja", type -> money(type.rate())),
                column("Tarif akhir pekan", type -> money(state.pricing().weekendRate(type.rate())))
        ));
        if (app.isOwner()) {
            table.getColumns().add(actions(type -> button("settings-edit-rate-" + type.name(), "Ubah Tarif", false,
                    () -> editRate(type, table))));
        }
        table.getItems().setAll(app.data().inventory().types());
        sizeTable(table, table.getItems().size());

        Button types = button("settings-open-room-types", "Buka Tipe Kamar", false, () -> app.open(HotelRoute.ROOM_TYPES));
        VBox rates = card(row(title("Harga Dasar per Tipe Kamar"), spacer(), types), table);

        if (!app.isOwner()) {
            return new VBox(14,
                    card(title("Tarif & Pajak"),
                            caption("Pajak dan biaya layanan " + pricing.combinedPercent() + "% · Perubahan tarif dikelola Owner.")),
                    rates);
        }

        TextField tax = text("settings-hotel-tax", Integer.toString(pricing.hotelTax()), "Pajak (%)");
        TextField charge = text("settings-service-charge", Integer.toString(pricing.serviceCharge()), "Biaya (%)");
        TextField deposit = text("settings-security-deposit", Long.toString(pricing.securityDeposit()), "Nominal rupiah");
        TextField weekend = text("settings-weekend-surcharge", Integer.toString(pricing.weekendSurcharge()), "Kenaikan (%)");

        FlowPane limits = new FlowPane(12, 10,
                field("Pajak hotel (%)", tax),
                field("Biaya layanan (%)", charge),
                field("Jaminan kerusakan (Rp)", deposit),
                field("Tambahan akhir pekan (%)", weekend));

        Label feedback = caption("");
        VBox config = card(title("Konfigurasi Pajak & Biaya Operasional"), limits,
                caption("Tarif akhir pekan berlaku Jumat sampai Minggu. Pengaturan ini berlaku untuk reservasi baru."),
                feedback,
                formActions(HotelRoute.RATES, feedback, () -> {
                    state.savePricing(new Pricing(percent(tax), percent(charge),
                            RoomPages.typeRate(deposit.getText()), percent(weekend)));
                    table.refresh();
                }));
        return new VBox(14, config, rates);
    }

    private void editRate(RoomInventory.Type type, TableView<RoomInventory.Type> table) {
        TextField rate = text("settings-type-rate", Long.toString(type.rate()), "Tarif dalam rupiah");
        edit(app.owner(), "Ubah Tarif " + type.label(),
                card(field("Tarif hari kerja per malam (Rp)", rate),
                        caption("Reservasi yang sudah dibuat tetap memakai tarif yang tercatat.")),
                "Simpan tarif",
                () -> app.data().saveRoomType(type, type.label(), RoomPages.typeRate(rate.getText()),
                        type.capacity(), type.facilities()),
                () -> table.getItems().setAll(app.data().inventory().types()));
    }

    private static int percent(TextField field) {
        String value = field.getText().strip();
        if (!value.matches("[0-9]{1,3}")) {
            throw new IllegalArgumentException("Persentase harus berupa bilangan bulat 0–100.");
        }
        return Integer.parseInt(value);
    }

    private VBox policies() {
        Policy initial = state.policy();
        CheckBox verify = new CheckBox("Wajib verifikasi identitas saat check-in");
        verify.setId("policy-verify");
        verify.setCursor(Cursor.HAND);
        verify.setSelected(initial.verifyIdentity());

        TextArea checkIn = area("policy-check-in", initial.checkInNote());
        TextArea cancellation = area("policy-cancel-note", initial.cancellationNote());
        Label feedback = caption("");

        VBox form = card(title("Kebijakan Reservasi"), verify,
                field("Catatan check-in", checkIn),
                field("Catatan pembatalan", cancellation),
                feedback,
                formActions(HotelRoute.POLICIES, feedback, () -> state.savePolicy(new Policy(
                        verify.isSelected(), checkIn.getText().strip(), cancellation.getText().strip()))));
        form.setMaxWidth(810);
        return form;
    }

    private VBox users() {
        TextField search = text("settings-user-search", "", "Cari username...");
        String all = "Semua peran";
        List<String> roles = new ArrayList<>(List.of(all));
        Arrays.stream(Role.values()).map(Role::label).forEach(roles::add);

        ComboBox<String> role = select("settings-user-role", roles, all);
        TableView<Login> table = table("settings-user-table", "Belum ada sesi login untuk filter ini.");
        table.getColumns().addAll(List.of(
                column("Username", Login::username),
                column("Peran", login -> login.role().label()),
                column("Login terakhir", login -> DATE.format(login.when())),
                actions(login -> button(null, "Detail", false, () -> detail(app.owner(), "Detail Pengguna",
                        card(title(login.username()), caption(login.role().label()),
                                caption("Login terakhir " + DATE.format(login.when()))))))
        ));

        Runnable refresh = () -> {
            List<Login> logins = state.logins().stream()
                    .filter(login -> all.equals(role.getValue()) || login.role().label().equals(role.getValue()))
                    .filter(login -> login.username().toLowerCase(Locale.ROOT).contains(search.getText().strip().toLowerCase(Locale.ROOT)))
                    .toList();
            table.getItems().setAll(logins);
            sizeTable(table, logins.size());
        };
        search.textProperty().addListener((obs, old, value) -> refresh.run());
        role.valueProperty().addListener((obs, old, value) -> refresh.run());
        refresh.run();
        HBox.setHgrow(search, Priority.ALWAYS);

        return new VBox(16,
                card(title(app.isOwner() ? "Riwayat Login" : "Pengguna & Peran"),
                        caption("Daftar pengguna yang masuk selama sesi aplikasi."),
                        row(search, role), table),
                card(title("Hak Akses per Peran"),
                        caption("Owner · Manajemen dan laporan hotel"),
                        caption("Staf Administrasi · Reservasi, layanan, inventaris, dan laporan"),
                        caption("Pengurus Maintenance · Status kamar, housekeeping, dan data kamar tampilan baca")));
    }

    private VBox myProfile() {
        String username = app.username();
        if (username.isBlank()) {
            return card(title("Profil Saya"), caption("Masuk untuk mengubah profil."));
        }
        Profile initial = state.profile(username);
        TextField name = text("profile-display-name", initial.displayName(), "Nama tampilan");
        TextField email = text("profile-email", initial.email(), "Email");
        Label feedback = caption("");

        VBox form = card(title("Profil Saya"),
                caption(username + " · " + app.role().label()),
                field("Nama tampilan", name),
                field("Email", email),
                feedback,
                formActions(HotelRoute.MY_PROFILE, feedback,
                        () -> state.saveProfile(username, new Profile(name.getText().strip(), email.getText().strip()))));
        form.setMaxWidth(810);

        if (state.accounts().find(username) != null) {
            form.getChildren().add(button("profile-password", "Ubah Kata Sandi", false,
                    () -> OwnerAccounts.changePassword(app, () -> {
                        feedback.setTextFill(TEAL);
                        feedback.setText("Kata sandi diperbarui.");
                    })));
        }
        return form;
    }

    private HBox formActions(HotelRoute route, Label feedback, Runnable save) {
        return row(spacer(),
                button("settings-reset", "Batal", false, () -> app.open(route)),
                button("settings-save", "Simpan perubahan", true, () -> {
                    try {
                        save.run();
                        feedback.setTextFill(TEAL);
                        feedback.setText("Perubahan tersimpan.");
                    } catch (IllegalArgumentException | IllegalStateException failure) {
    feedback.setTextFill(RED);
    feedback.setText(
            failure.getMessage() == null
                    ? "Gagal menyimpan perubahan."
                    : failure.getMessage()
    );
}
                }));
    }

    private void chooseLogo(Consumer<Image> preview, Label feedback) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Pilih logo hotel");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG atau JPG", "*.png", "*.jpg", "*.jpeg"));
        var file = chooser.showOpenDialog(app.owner());
        if (file == null) {
            return;
        }
        try {
            if (Files.size(file.toPath()) > 2 * 1024 * 1024) {
                throw new IOException("Ukuran logo melebihi 2 MB.");
            }
            try (InputStream input = Files.newInputStream(file.toPath())) {
                Image image = new Image(input, 256, 256, true, true);
                if (image.isError()) {
                    throw new IOException("Gambar tidak dapat dibuka.");
                }
                preview.accept(image);
            }
            feedback.setTextFill(TEAL);
            feedback.setText("Logo siap disimpan.");
        } catch (IOException problem) {
            feedback.setTextFill(RED);
            feedback.setText(problem.getMessage());
        }
    }

    private static TextArea area(String id, String value) {
        TextArea text = new TextArea(value);
        text.setId(id);
        text.setWrapText(true);
        text.setPrefRowCount(4);
        return text;
    }

    private static LocalTime time(String value) {
        try {
            String text = value.strip();
            if (!text.matches("[0-9]{2}:[0-9]{2}")) {
                throw new IllegalArgumentException("Gunakan format jam HH:mm, misalnya 14:00.");
            }
            return LocalTime.parse(text);
        } catch (DateTimeParseException invalid) {
            throw new IllegalArgumentException("Jam harus antara 00:00 hingga 23:59.");
        }
    }
}