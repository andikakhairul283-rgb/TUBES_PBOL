package com.example.tubespbolkel7.ui;

import com.example.tubespbolkel7.model.FrontDeskNotes;
import com.example.tubespbolkel7.model.HotelData;
import com.example.tubespbolkel7.model.RoomInventory;

import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import static com.example.tubespbolkel7.model.RoomInventory.*;
import static com.example.tubespbolkel7.ui.PageComponents.*;

final class MaintenanceRoomActions {
    private static final String NO_ROOM = "Tanpa nomor kamar";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.forLanguageTag("id-ID"));
    private final HotelOperations app;
    private final RoomInventory inventory;
    private final HousekeepingPage housekeeping;

    MaintenanceRoomActions(HotelOperations app, HousekeepingPage housekeeping) {
        this.app = app;
        this.inventory = app.data().inventory();
        this.housekeeping = housekeeping;
    }

    void scan(Runnable refresh) {
        TextField code = text("maintenance-scan-room", "", "Contoh: 204 atau KAMAR-204");
        edit(app.owner(), "Scan Kamar", card(
                        caption("Gunakan pemindai yang memasukkan kode sebagai ketikan, atau ketik nomor kamar."), field("Kode kamar", code)),
                "Buka kamar", () -> {
                    if (inventory.find(scannedNumber(code.getText())) == null) {
                        throw new IllegalArgumentException("Kode kamar tidak ditemukan dalam inventaris.");
                    }
                }, () -> showRoom(inventory.find(scannedNumber(code.getText())), refresh));
    }

    static String scannedNumber(String code) {
        return code.strip().toUpperCase(Locale.ROOT).replaceFirst("^(KAMAR|ROOM)[- :]*", "");
    }

    void showRoom(Unit unit, Runnable refresh) {
        String number = unit.room().number();
        Job active = inventory.activeJob(number);
        VBox body = card(title(unit.type().label() + " · Lantai " + unit.floor()),
                caption("Status unit · " + app.data().roomStatus(unit)),
                caption("Fasilitas · " + inventory.facilities(unit)),
                caption("Catatan teknis · " + (unit.notes().isBlank() ? "Belum ada catatan." : unit.notes())));
        var occupant = app.data().occupant(number);
        if (occupant != null) body.getChildren().add(caption("Jadwal check-out · " + occupant.departure()
                + " pukul " + app.data().settings().hotel().departure()));
        Dialog<ButtonType> dialog = dialog(app.owner(), "Detail Teknis Kamar " + number, body);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        if (active != null) body.getChildren().addAll(caption(active.code() + " · " + active.description()),
                button("maintenance-room-open-job", "Lihat Tiket WO", true, () -> {
                    dialog.close();
                    housekeeping.openJob(active, refresh);
                }));
        else if (occupant == null) body.getChildren().add(button("maintenance-room-report", "Laporkan Kerusakan", true, () -> {
            dialog.close();
            housekeeping.addJob(refresh, false, number);
        }));
        body.getChildren().add(row(button("maintenance-room-history", "Riwayat Unit", false, () -> {
                    dialog.close();
                    housekeeping.history(number);
                }), button("maintenance-room-contact", "Hubungi Resepsionis", false, () -> {
                    dialog.close();
                    contact(number);
                })));
        dialog.showAndWait();
    }

    void quickStatus(Runnable refresh) {
        List<String> emptyRooms = inventory.units().stream().filter(unit -> app.data().occupant(unit.room().number()) == null)
                .map(unit -> unit.room().number()).toList();
        ComboBox<String> room = select("maintenance-quick-room", emptyRooms, null);
        ComboBox<Condition> condition = select("maintenance-quick-condition", List.of(Condition.values()), null);
        Label current = caption("Pilih nomor kamar.");
        room.valueProperty().addListener((obs, old, value) -> {
            if (value == null) return;
            Unit unit = inventory.find(value);
            current.setText("Kondisi saat ini · " + unit.condition());
            condition.setValue(unit.condition());
        });
        edit(app.owner(), "Perbarui Status Cepat", card(
                        caption("Untuk kamar kosong. Menandai kamar tersedia akan menyelesaikan tugas aktif pada unit itu."),
                        field("Nomor kamar", room), current, field("Kondisi teknis", condition)),
                "Simpan status", () -> {
                    if (room.getValue() == null || condition.getValue() == null) throw new IllegalArgumentException("Pilih kamar dan kondisi.");
                    String number = room.getValue();
                    Condition previous = inventory.find(number).condition();
                    app.data().setRoomCondition(number, condition.getValue());
                    if (previous != condition.getValue()) notifyStatus(number);
                }, refresh);
    }

    void notifyStatus(String number) {
        Unit unit = inventory.find(number);
        app.data().frontDesk().send(app.username(), number, "Status kamar " + number + " diperbarui menjadi " + app.data().roomStatus(unit) + ".");
    }

    void contact(String selectedRoom) {
        var hotel = app.data().settings().hotel();
        List<String> choices = java.util.stream.Stream.concat(java.util.stream.Stream.of(NO_ROOM),
                inventory.rooms().stream().map(HotelData.Room::number)).toList();
        ComboBox<String> room = select("maintenance-contact-room", choices, selectedRoom == null ? NO_ROOM : selectedRoom);
        TextArea message = new TextArea();
        message.setId("maintenance-contact-message");
        message.setWrapText(true);
        message.setPrefRowCount(4);
        VBox body = card(title(hotel.name()), field("Terkait kamar", room), field("Pesan untuk resepsionis", message));
        if (!hotel.phone().isBlank()) body.getChildren().add(1, caption("Telepon properti · " + hotel.phone()));
        if (!hotel.email().isBlank()) body.getChildren().add(1, caption("Email properti · " + hotel.email()));
        edit(app.owner(), "Hubungi Resepsionis", body, "Kirim pesan internal", () -> {
            String number = room.getValue() == null || NO_ROOM.equals(room.getValue()) ? "" : room.getValue();
            app.data().frontDesk().send(app.username(), number, message.getText());
        }, () -> detail(app.owner(), "Pesan Terkirim", card(caption("Pesan tersedia di dasbor Staf Administrasi."))));
    }

    static void showInbox(HotelOperations app) {
        TableView<FrontDeskNotes.Note> inbox = table("maintenance-inbox", "Belum ada pesan dari Pengurus Maintenance.");
        inbox.getColumns().addAll(List.of(column("Dikirim", note -> DATE.format(note.created())),
                column("Pengirim", FrontDeskNotes.Note::author),
                column("Kamar", note -> note.room().isBlank() ? "—" : note.room()),
                actions(note -> button(null, "Baca pesan", false, () -> detail(app.owner(), "Pesan Maintenance",
                        card(title(note.author()), caption(DATE.format(note.created())), caption(note.message())))))));
        inbox.getItems().setAll(app.data().frontDesk().all());
        sizeTable(inbox, Math.min(6, inbox.getItems().size()));
        detail(app.owner(), "Pesan Maintenance", card(inbox));
    }
}
