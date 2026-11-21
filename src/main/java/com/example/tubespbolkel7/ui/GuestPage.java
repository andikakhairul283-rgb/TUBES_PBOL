package com.example.tubespbolkel7.ui;

import com.example.tubespbolkel7.model.HotelData;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

import static com.example.tubespbolkel7.model.GuestRegistry.Guest;
import static com.example.tubespbolkel7.ui.PageComponents.*;

final class GuestPage {
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.forLanguageTag("id-ID"));

    private GuestPage() { }

    static Node view(HotelOperations app) {
        TextField search = text("guest-search", "", "Cari nama, kode, atau kontak tamu...");
        ComboBox<String> filter = select("guest-filter",
                List.of("Semua tamu", "Menginap", "Tidak menginap", "VIP", "Reguler"), "Semua tamu");
        TableView<Guest> table = table("guests-table", "Belum ada data tamu untuk filter ini.");
        Label feedback = caption("");
        VBox navigation = new VBox();
        SimpleIntegerProperty pageIndex = new SimpleIntegerProperty();

        Supplier<List<Guest>> matching = () -> {
            String key = search.getText().strip().toLowerCase(Locale.ROOT);
            return app.data().guests().all().stream()
                    .filter(guest -> switch (filter.getValue()) {
                        case "Menginap" -> inHouse(app.data(), guest);
                        case "Tidak menginap" -> !inHouse(app.data(), guest);
                        case "VIP" -> guest.vip();
                        case "Reguler" -> !guest.vip();
                        default -> true;
                    })
                    .filter(guest -> String.join(" ", guest.name(), guest.id(), guest.phone(), guest.email(), guest.identity())
                            .toLowerCase(Locale.ROOT).contains(key))
                    .toList();
        };

        Runnable refresh = () -> {
            List<Guest> visible = matching.get();
            int page = Math.min(pageIndex.get(), Math.max(0, (visible.size() - 1) / PAGE_SIZE));
            int start = page * PAGE_SIZE;
            table.getItems().setAll(visible.subList(start, Math.min(start + PAGE_SIZE, visible.size())));
            sizeTable(table, table.getItems().size());
            navigation.getChildren().setAll(pageControls("guests", page, visible.size(), pageIndex::set));
        };

        table.getColumns().addAll(List.of(
                column("Nama tamu", Guest::name),
                column("Identitas", guest -> display(guest.identity())),
                column("Telepon", guest -> display(guest.phone())),
                column("Email", guest -> display(guest.email())),
                column("Kunjungan", guest -> Long.toString(app.data().bookingsFor(guest).stream()
                        .filter(booking -> app.data().stay(booking.code()) != null)
                        .count())),
                column("Terakhir menginap", guest -> app.data().bookingsFor(guest).stream()
                        .map(booking -> app.data().stay(booking.code()))
                        .filter(Objects::nonNull)
                        .map(HotelData.Stay::checkIn)
                        .max(LocalDate::compareTo)
                        .map(SHORT_DATE::format)
                        .orElse("—")),
                column("Status", guest -> (inHouse(app.data(), guest) ? "Menginap" : "Tidak menginap")
                        + (guest.vip() ? " · VIP" : "")),
                actions(guest -> row(
                        button("guest-detail-" + guest.id(), "Detail", false, () -> showDetail(app, guest)),
                        button("guest-edit-" + guest.id(), "Ubah", true, () -> form(app, guest, refresh))
                ))
        ));
        table.getColumns().getLast().setMinWidth(152);
        HBox.setHgrow(search, Priority.ALWAYS);

        pageIndex.addListener((obs, old, value) -> refresh.run());
        Runnable filterChanged = () -> {
            if (pageIndex.get() == 0) {
                refresh.run();
            } else {
                pageIndex.set(0);
            }
        };
        search.textProperty().addListener((obs, old, value) -> filterChanged.run());
        filter.valueProperty().addListener((obs, old, value) -> filterChanged.run());

        Button add = button("guest-add", "+ Tambah Tamu", true, () -> form(app, null, refresh));
        Button download = button("guest-export", "Ekspor", false, () -> {
            List<List<?>> rows = matching.get().stream().<List<?>>map(guest -> List.of(
                    guest.id(), guest.name(), guest.phone(), guest.email(), guest.vip() ? "VIP" : "Reguler"
            )).toList();
            export(app.owner(), "Data Tamu", false,
                    List.of("Kode", "Nama", "Telepon", "Email", "Kategori"), rows, feedback::setText);
        });

        VBox page = page("Data Tamu", "Kelola profil, kontak, dan riwayat reservasi tamu.", download, add);
        page.getChildren().addAll(card(row(search, filter), table, navigation), feedback);
        refresh.run();
        return page;
    }

    private static void form(HotelOperations app, Guest guest, Runnable refresh) {
        TextField name = text("guest-name", guest == null ? "" : guest.name(), "Nama lengkap");
        TextField phone = text("guest-phone", guest == null ? "" : guest.phone(), "Nomor telepon");
        TextField email = text("guest-email", guest == null ? "" : guest.email(), "alamat@email.com");
        TextField identity = text("guest-identity", guest == null ? "" : guest.identity(), "Nomor identitas");

        CheckBox vip = new CheckBox("Tandai sebagai tamu VIP");
        vip.setId("guest-vip");
        vip.setCursor(Cursor.HAND);
        vip.setSelected(guest != null && guest.vip());

        TextArea notes = new TextArea(guest == null ? "" : guest.notes());
        notes.setId("guest-notes");
        notes.setWrapText(true);
        notes.setPrefRowCount(2);

        VBox body = card(
                title("Informasi Tamu"),
                field("Nama lengkap *", name),
                field("Nomor telepon", phone),
                field("Email", email),
                field("Nomor identitas", identity),
                vip,
                field("Catatan", notes)
        );

        edit(app.owner(), guest == null ? "Tambah Tamu" : "Ubah Tamu " + guest.id(), body, "Simpan",
                () -> app.data().saveGuest(
                        guest,
                        name.getText().strip(),
                        phone.getText().strip(),
                        email.getText().strip(),
                        identity.getText().strip(),
                        vip.isSelected(),
                        notes.getText().strip()
                ), refresh);
    }

    static void showDetail(HotelOperations app, Guest guest) {
        TableView<HotelData.Booking> history = table("guest-history", "Tamu ini belum memiliki reservasi.");
        history.getColumns().addAll(List.of(
                column("Kode", HotelData.Booking::code),
                column("Kamar", booking -> booking.room().number()),
                column("Check-in", booking -> SHORT_DATE.format(booking.arrival())),
                column("Status", booking -> booking.status().label()),
                actions(booking -> button(null, "Detail", false, () -> ReservationPage.detail(app, booking)))
        ));
        history.getItems().setAll(app.data().bookingsFor(guest));
        sizeTable(history, history.getItems().size());
        history.setPrefHeight(Math.min(history.getPrefHeight(), 182));

        detail(app.owner(), "Detail Tamu " + guest.id(), card(
                title(guest.name() + (guest.vip() ? " · VIP" : "")),
                caption("Telepon " + display(guest.phone())),
                caption("Email " + display(guest.email())),
                caption("Identitas " + display(guest.identity())),
                caption("Catatan " + display(guest.notes())),
                title("Riwayat Reservasi"),
                history
        ));
    }

    private static String display(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private static boolean inHouse(HotelData data, Guest guest) {
        return data.bookingsFor(guest).stream()
                .anyMatch(booking -> booking.status() == HotelData.Status.IN_HOUSE);
    }
}