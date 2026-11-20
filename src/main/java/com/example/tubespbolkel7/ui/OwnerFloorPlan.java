package com.example.tubespbolkel7.ui;

import com.example.tubespbolkel7.model.HotelData;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.FontWeight;

import java.util.List;

import static com.example.tubespbolkel7.model.HotelData.Booking;
import static com.example.tubespbolkel7.model.RoomInventory.*;
import static com.example.tubespbolkel7.ui.PageComponents.*;

final class OwnerFloorPlan {
    static Node view(HotelOperations app) {
        List<Integer> floors = app.data().inventory().units().stream().map(Unit::floor).distinct().sorted().toList();
        VBox root = page("Denah Kamar", "Pilih lantai dan unit untuk melihat kondisi atau membuka riwayat.",
                button("owner-floor-calendar", "Kalender Ketersediaan", false, () -> app.open(HotelRoute.AVAILABILITY)));
        if (floors.isEmpty()) {
            root.getChildren().add(card(caption("Belum ada kamar terdaftar."), button("owner-floor-add-room", "Buka Data Kamar",
                    true, () -> app.open(HotelRoute.ROOMS))));
            return root;
        }
        Unit previous = app.selectedRoom() == null ? null : app.data().inventory().find(app.selectedRoom());
        SimpleIntegerProperty floor = new SimpleIntegerProperty(previous == null ? floors.getFirst() : previous.floor());
        FlowPane selector = new FlowPane(8, 8);
        FlowPane tiles = new FlowPane(10, 10);
        VBox selectedRoom = card();
        Runnable refresh = () -> {
            HotelData data = app.data();
            selector.getChildren().setAll(floors.stream().map(level -> button("owner-floor-" + level,
                    "Lantai " + level, level == floor.get(), () -> floor.set(level))).toList());
            tiles.getChildren().clear();
            List<Unit> units = data.inventory().units().stream().filter(unit -> unit.floor() == floor.get()).toList();
            boolean hasSelection = false;
            for (Unit unit : units) {
                String number = unit.room().number();
                Status status = data.roomStatus(unit);
                boolean selected = number.equals(app.selectedRoom());
                hasSelection |= selected;
                Button tile = button("owner-plan-tile-" + number, "", false, () -> {
                    app.selectRoom(number);
                    showUnit(app, selectedRoom, unit, () -> app.open(HotelRoute.FLOOR_PLAN));
                    for (Node node : tiles.getChildren()) {
                        paintSelection((Button) node, node.getId().equals("owner-plan-tile-" + number));
                    }
                });
                tile.setAccessibleText("Kamar " + number + " · " + status);
                if (selected) showUnit(app, selectedRoom, unit, () -> app.open(HotelRoute.FLOOR_PLAN));
                Color color = switch (status) {
                    case READY -> TEAL;
                    case BOOKED -> Color.web("#7546AC");
                    case OCCUPIED -> Color.web("#24589A");
                    case CLEANING -> Color.web("#89520A");
                    case MAINTENANCE -> RED;
                };
                tile.setBackground(fill(color.interpolate(Color.WHITE, .90), 8));
                tile.setGraphic(new VBox(4, title(number), caption(unit.type().label()),
                        label(status.toString(), 12, FontWeight.BOLD, color)));
                tile.setPrefWidth(134);
                tile.setPrefHeight(91);
                paintSelection(tile, selected);
                tiles.getChildren().add(tile);
            }
            if (!hasSelection) selectedRoom.getChildren().setAll(title("Detail Kamar"), caption("Pilih kartu kamar di atas."));
        };
        floor.addListener((obs, old, value) -> refresh.run());
        refresh.run();
        root.getChildren().addAll(card(title("Pilih Lantai"), selector), card(title("Denah Unit"), tiles,
                caption("Tersedia · Dipesan · Terisi · Dibersihkan · Maintenance")), selectedRoom,
                button("owner-floor-refresh", "Segarkan Status", false, refresh));
        return root;
    }

    private static void paintSelection(Button tile, boolean selected) {
        tile.setAccessibleHelp(selected ? "Kamar dipilih" : "");
        tile.setBorder(selected ? javafx.scene.layout.Border.stroke(TEAL) : javafx.scene.layout.Border.EMPTY);
    }

    private static void showUnit(HotelOperations app, VBox body, Unit unit, Runnable refresh) {
        HotelData data = app.data();
        String number = unit.room().number();
        Booking guest = data.occupant(number);
        body.getChildren().setAll(title("Kamar " + number + " · " + unit.type().label()),
                caption("Lantai " + unit.floor() + "  •  " + data.roomStatus(unit) + "  •  " + money(unit.room().rate()) + " per malam"),
                caption(data.inventory().facilities(unit)), caption(unit.notes().isBlank() ? "Belum ada catatan kamar." : unit.notes()));
        if (guest != null) body.getChildren().add(caption("Tamu menginap · " + guest.guest()));
        FlowPane actions = new FlowPane(8, 7,
                button("owner-floor-status", "Ubah Status", true, () -> {
                    ComboBox<Condition> condition = select("owner-floor-condition", List.of(Condition.values()), unit.condition());
                    edit(app.owner(), "Ubah Status Kamar " + number, card(field("Kondisi operasional", condition)),
                            "Simpan status", () -> data.setRoomCondition(number, condition.getValue()), refresh);
                }),
                button("owner-floor-history", "Lihat Riwayat", false, () -> history(app, number)));
        if (data.available(unit.room(), data.today(), data.today().plusDays(1), null)) {
            actions.getChildren().add(button("owner-floor-book", "Pesan Kamar", false,
                    () -> ReservationForm.bookRoom(app, unit.room(), data.today())));
        }
        body.getChildren().add(actions);
    }

    private static void history(HotelOperations app, String number) {
        TableView<Booking> bookings = table("owner-room-reservation-history", "Belum ada reservasi untuk kamar ini.");
        bookings.getColumns().addAll(List.of(column("Kode", Booking::code), column("Tamu", Booking::guest),
                column("Check-in", item -> item.arrival().toString()), column("Status", item -> item.status().label())));
        bookings.getItems().setAll(app.data().bookings().stream().filter(item -> item.room().number().equals(number)).toList());
        sizeTable(bookings, Math.min(3, bookings.getItems().size()));
        TableView<Job> jobs = table("owner-room-maintenance-history", "Belum ada tugas untuk kamar ini.");
        jobs.getColumns().addAll(List.of(column("Tiket", Job::code), column("Kondisi", item -> item.condition().toString()),
                column("Catatan", Job::description), column("Status", item -> item.active() ? "Aktif" : "Selesai")));
        jobs.getItems().setAll(app.data().inventory().jobs().stream().filter(item -> item.room().equals(number)).toList());
        sizeTable(jobs, Math.min(3, jobs.getItems().size()));
        detail(app.owner(), "Riwayat Kamar " + number, card(title("Reservasi"), bookings, title("Housekeeping & Maintenance"), jobs));
    }
}
