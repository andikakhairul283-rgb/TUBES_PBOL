package com.example.tubespbolkel7.ui;

import com.example.tubespbolkel7.model.HotelData;
import com.example.tubespbolkel7.model.RoomInventory;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.FontWeight;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static com.example.tubespbolkel7.model.RoomInventory.CardRequest;
import static com.example.tubespbolkel7.model.RoomInventory.Condition;
import static com.example.tubespbolkel7.model.RoomInventory.Job;
import static com.example.tubespbolkel7.model.RoomInventory.Status;
import static com.example.tubespbolkel7.model.RoomInventory.Type;
import static com.example.tubespbolkel7.model.RoomInventory.Unit;
import static com.example.tubespbolkel7.ui.PageComponents.*;

final class RoomPages {
    private static final String ALL_FLOORS = "Semua lantai";
    private static final String ALL_TYPES = "Semua tipe";
    private static final String ALL_STATUSES = "Semua status";

    private final HotelOperations app;
    private final HotelData data;
    private final RoomInventory inventory;

    RoomPages(HotelOperations app) {
        this.app = app;
        this.data = app.data();
        this.inventory = data.inventory();
    }

    Node view(HotelRoute route) {
        return switch (route) {
            case ROOM_TYPES -> types();
            case ROOMS -> inventory(false);
            case ROOM_GRID -> inventory(true);
            case ROOM_FORM -> form();
            default -> throw new IllegalArgumentException("Halaman kamar tidak dikenal.");
        };
    }

    private VBox types() {
        Label feedback = caption("");
        Button brochure = button("room-type-export", "Simpan Brosur Tarif (PDF)", false,
                () -> export(app.owner(), "Brosur Tarif Kamar", true,
                        List.of("Tipe", "Tarif per malam", "Kapasitas"),
                        inventory.types().stream().<List<?>>map(type ->
                                List.of(type.label(), money(type.rate()), type.capacity() + " tamu")).toList(),
                        feedback::setText));

        Runnable reload = () -> app.open(HotelRoute.ROOM_TYPES);
        String description = app.isOwner()
                ? "Kelola tipe kamar, tarif dasar, kapasitas, dan fasilitas."
                : "Daftar tarif dasar dan fasilitas. Perubahan tipe master dikelola Owner.";

        VBox page = app.isOwner()
                ? page("Tipe Kamar", description, brochure,
                        button("room-type-add", "+ Tambah Tipe", true, () -> editType(app, null, reload)))
                : page("Tipe Kamar", description, brochure);

        TextField search = text("room-type-search", "", "Cari tipe kamar atau fasilitas...");
        ComboBox<String> filter = select("room-type-filter", typeChoices(), ALL_TYPES);
        TableView<Type> table = table("room-types-table", "Tidak ada tipe kamar yang cocok.");

        table.getColumns().addAll(List.of(
                column("Tipe kamar", Type::label),
                column("Tarif / malam", type -> money(type.rate())),
                column("Kapasitas", type -> type.capacity() + " tamu"),
                column("Fasilitas standar", Type::facilities),
                actions(type -> typeActions(type, reload))
        ));
        if (app.isOwner()) {
            table.getColumns().getLast().setMinWidth(200);
        }

        Runnable refresh = () -> {
            List<Type> visible = inventory.types().stream()
                    .filter(type -> matches(filter.getValue(), ALL_TYPES, type.label()))
                    .filter(type -> contains(type.label() + " " + type.facilities(), search.getText()))
                    .toList();
            table.setItems(FXCollections.observableArrayList(visible));
            sizeTable(table, visible.size());
        };
        search.textProperty().addListener((obs, old, value) -> refresh.run());
        filter.valueProperty().addListener((obs, old, value) -> refresh.run());
        refresh.run();

        HBox.setHgrow(search, Priority.ALWAYS);
        page.getChildren().addAll(card(row(search, filter), table), feedback);
        return page;
    }

    private Node typeActions(Type type, Runnable reload) {
        Button details = button("room-type-detail-" + type.name(), "Detail", false, () ->
                detail(app.owner(), "Tipe Kamar " + type.label(), card(
                        title(type.label()),
                        caption(money(type.rate()) + " per malam"),
                        caption("Kapasitas " + type.capacity() + " tamu"),
                        caption(type.facilities()))));
        if (!app.isOwner()) {
            return details;
        }
        return row(details,
                button("room-type-edit-" + type.name(), "Ubah", true, () -> editType(app, type, reload)),
                button("room-type-delete-" + type.name(), "Hapus", false, () -> edit(app.owner(), "Hapus Tipe Kamar",
                        card(title(type.label()), caption("Konfirmasi untuk menghapus tipe kamar ini.")),
                        "Hapus tipe", () -> data.removeRoomType(type), reload)));
    }

    static void editType(HotelOperations app, Type type, Runnable refresh) {
        boolean create = (type == null);
        TextField name = text("room-type-name", create ? "" : type.label(), "Nama tipe kamar");
        TextField rate = text("room-type-rate", create ? "" : Long.toString(type.rate()), "Tarif dalam rupiah");
        ComboBox<Integer> capacity = select("room-type-capacity",
                IntStream.rangeClosed(1, 20).boxed().toList(), create ? 2 : type.capacity());
        TextArea facilities = new TextArea(create ? "" : type.facilities());
        facilities.setId("room-type-facilities");
        facilities.setWrapText(true);
        facilities.setPrefRowCount(3);

        VBox body = card(field("Nama tipe *", name), field("Tarif per malam (Rp) *", rate),
                field("Kapasitas tamu", capacity), field("Fasilitas standar", facilities));
        edit(app.owner(), create ? "Tambah Tipe Kamar" : "Ubah Tipe " + type.label(), body, "Simpan",
                () -> app.data().saveRoomType(type, name.getText().strip(), typeRate(rate.getText()),
                        capacity.getValue(), facilities.getText().strip()),
                refresh);
    }

    static long typeRate(String input) {
        String value = input.strip();
        if (!value.matches("[0-9]{1,9}|[0-9]{1,3}(\\.[0-9]{3}){1,2}")) {
            throw new IllegalArgumentException("Masukkan tarif dalam rupiah, misalnya 550000 atau 550.000.");
        }
        return Long.parseLong(value.replace(".", ""));
    }

    private VBox inventory(boolean grid) {
        VBox listing = new VBox(12);
        boolean maintenance = app.isMaintenance();
        TextField search = text("rooms-search", "", maintenance
                ? "Cari nomor, tipe, lantai, atau tiket..."
                : "Cari nomor, tipe, lantai, atau tamu...");
        ComboBox<String> floors = select("rooms-floor", floorChoices(), ALL_FLOORS);
        ComboBox<String> types = select("rooms-type", typeChoices(), ALL_TYPES);

        List<String> statuses = new ArrayList<>(List.of(ALL_STATUSES));
        Arrays.stream(Status.values()).map(Status::toString).forEach(statuses::add);
        Status initialStatus = app.consumeRoomFilter();
        ComboBox<String> status = select("rooms-status", statuses,
                initialStatus == null ? ALL_STATUSES : initialStatus.toString());
        ComboBox<String> density = select("rooms-density", List.of("Normal", "Padat"), "Normal");
        SimpleBooleanProperty technical = new SimpleBooleanProperty();

        Supplier<List<Unit>> matching = () -> inventory.units().stream()
                .filter(unit -> matches(floors.getValue(), ALL_FLOORS, "Lantai " + unit.floor()))
                .filter(unit -> matches(types.getValue(), ALL_TYPES, unit.type().label()))
                .filter(unit -> matches(status.getValue(), ALL_STATUSES, data.roomStatus(unit).toString()))
                .filter(unit -> !technical.get() || unit.condition() != Condition.READY)
                .filter(unit -> contains(searchKey(unit), search.getText()))
                .toList();

        FlowPane cards = new FlowPane(10, 8);
        SimpleIntegerProperty pageIndex = new SimpleIntegerProperty();

        Runnable refresh = () -> {
            cards.getChildren().clear();
            if (maintenance) {
                cards.getChildren().add(button("rooms-show-all", "Semua Unit  " + inventory.units().size(),
                        ALL_STATUSES.equals(status.getValue()) && !technical.get(), () -> {
                            technical.set(false);
                            status.setValue(ALL_STATUSES);
                        }));
            }
            for (Status value : Status.values()) {
                long count = inventory.units().stream().filter(unit -> data.roomStatus(unit) == value).count();
                Button filterButton = button(
                        maintenance ? "rooms-status-" + value.name().toLowerCase(Locale.ROOT) : null,
                        value + "  " + count,
                        value.toString().equals(status.getValue()),
                        () -> {
                            technical.set(false);
                            status.setValue(value.toString().equals(status.getValue()) ? ALL_STATUSES : value.toString());
                        });
                cards.getChildren().add(filterButton);
            }

            List<Unit> visible = matching.get();
            if (grid) {
                listing.getChildren().setAll(grid(visible, density.getValue(), () -> app.open(HotelRoute.ROOM_GRID)));
            } else if (app.isOwner() || maintenance) {
                int page = Math.min(pageIndex.get(), Math.max(0, (visible.size() - 1) / PAGE_SIZE));
                int start = page * PAGE_SIZE;
                List<Unit> rows = visible.subList(start, Math.min(start + PAGE_SIZE, visible.size()));
                listing.getChildren().setAll(inventoryTable(rows, () -> app.open(HotelRoute.ROOMS)),
                        pageControls("rooms", page, visible.size(), pageIndex::set));
            } else {
                listing.getChildren().setAll(inventoryTable(visible, () -> app.open(HotelRoute.ROOMS)));
            }
        };

        pageIndex.addListener((obs, old, value) -> refresh.run());
        Runnable filterChanged = () -> {
            if (pageIndex.get() == 0) {
                refresh.run();
            } else {
                pageIndex.set(0);
            }
        };
        search.textProperty().addListener((obs, old, value) -> filterChanged.run());
        technical.addListener((obs, old, value) -> filterChanged.run());
        for (ComboBox<String> box : List.of(floors, types, status, density)) {
            box.valueProperty().addListener((obs, old, value) -> filterChanged.run());
        }

        Label feedback = caption("");
        Button export = button("rooms-export", "Ekspor Data", false, () -> {
            List<List<?>> rows = matching.get().stream().<List<?>>map(unit -> List.of(
                    unit.room().number(), unit.floor(), unit.type().label(), unit.type().capacity(),
                    data.roomStatus(unit).toString())).toList();
            if (rows.isEmpty()) {
                feedback.setText("Belum ada kamar untuk filter ini.");
            } else {
                export(app.owner(), "Data Kamar", false,
                        List.of("Nomor", "Lantai", "Tipe", "Kapasitas", "Status"), rows, feedback::setText);
            }
        });

        Button add = button("rooms-add", "+ Tambah Kamar", true, () -> editRoom(null));
        Button changeView = button("rooms-change-view", grid ? "Lihat sebagai tabel" : "Lihat grid status", false,
                () -> app.open(grid ? HotelRoute.ROOMS : HotelRoute.ROOM_GRID));

        VBox page;
        if (maintenance && !grid) {
            Button problems = button("maintenance-technical-filter", "Filter Masalah Teknis", false, () -> {
                if (!technical.get()) {
                    status.setValue(ALL_STATUSES);
                }
                technical.set(!technical.get());
            });
            technical.addListener((obs, old, value) ->
                    problems.setText(value ? "Tampilkan Semua Kondisi" : "Filter Masalah Teknis"));
            page = page("Data Kamar", "Data induk hanya untuk dilihat. Kondisi teknis dikelola melalui tugas atau tindakan cepat.",
                    problems, button("maintenance-quick-status", "Perbarui Status Cepat", true,
                            () -> app.maintenance().quickStatus(refresh)));
        } else {
            String gridHint = maintenance
                    ? "Pilih unit untuk melihat kondisi, riwayat, atau tiket pekerjaan."
                    : "Pilih kartu untuk membaca detail dan memperbarui kondisi kamar.";
            page = page(grid ? "Grid Status Kamar" : "Data Kamar",
                    grid ? gridHint : inventory.units().size() + " unit terdaftar. Kelola inventaris kamar dan kondisi operasional.",
                    grid ? changeView : export,
                    grid ? button("rooms-refresh", "Segarkan Status", true, refresh) : add);
        }

        FlowPane filters = new FlowPane(9, 8, search, floors, types, status);
        search.setPrefWidth(270);
        if (grid) {
            filters.getChildren().add(density);
        } else {
            filters.getChildren().add(changeView);
        }

        refresh.run();
        page.getChildren().addAll(cards, card(filters), listing, feedback);
        if (maintenance && !grid) {
            page.getChildren().add(new FlowPane(9, 8,
                    button("maintenance-report-room", "Laporkan Kerusakan Baru", true,
                            () -> app.housekeeping().addJob(refresh, false, null)),
                    button("maintenance-room-desk", "Hubungi Resepsionis", false,
                            () -> app.maintenance().contact(null))));
        }
        return page;
    }

    private TableView<Unit> inventoryTable(List<Unit> units, Runnable refresh) {
        TableView<Unit> table = table("rooms-table", "Tidak ada kamar yang cocok.");
        table.getColumns().addAll(List.of(
                column("Nomor", unit -> unit.room().number()),
                column("Lantai", unit -> Integer.toString(unit.floor())),
                column("Tipe kamar", unit -> unit.type().label()),
                column("Kapasitas", unit -> unit.type().capacity() + " tamu"),
                column("Status operasional", unit -> data.roomStatus(unit).toString()),
                actions(unit -> roomActions(unit, refresh))
        ));
        if (app.isMaintenance()) {
            table.getColumns().set(3, column("Catatan teknis", unit -> {
                Job active = inventory.activeJob(unit.room().number());
                return (active == null) ? (unit.notes().isBlank() ? "—" : unit.notes()) : active.description();
            }));
        }
        table.getColumns().getLast().setMinWidth(app.isOwner() || app.isMaintenance() ? 200 : 145);
        table.getItems().setAll(units);
        sizeTable(table, units.size());
        return table;
    }

    private HBox roomActions(Unit unit, Runnable refresh) {
        String number = unit.room().number();
        HBox buttons = row(button("room-detail-" + number, "Detail", false, () -> showDetail(unit, refresh)));
        if (app.isMaintenance()) {
            Job active = inventory.activeJob(number);
            if (active != null) {
                buttons.getChildren().add(button("maintenance-ticket-" + number, "Lihat Tiket WO", true,
                        () -> app.housekeeping().openJob(active, refresh)));
            }
            return buttons;
        }
        buttons.getChildren().add(button("room-edit-" + number, "Ubah", true, () -> editRoom(unit)));
        if (app.isOwner()) {
            buttons.getChildren().add(button("room-delete-" + number, "Hapus", false,
                    () -> edit(app.owner(), "Hapus Kamar " + number, card(title("Kamar " + number),
                                    caption("Konfirmasi penghapusan unit dari inventaris. Riwayat reservasi dan tugas tetap dilindungi.")),
                            "Hapus kamar", () -> data.removeRoom(number), refresh)));
        }
        return buttons;
    }

    private VBox grid(List<Unit> units, String density, Runnable refresh) {
        VBox result = new VBox(13);
        boolean compact = "Padat".equals(density);

        for (int floor : units.stream().map(Unit::floor).distinct().sorted().toList()) {
            FlowPane tiles = new FlowPane(10, 10);
            for (Unit unit : units.stream().filter(room -> room.floor() == floor).toList()) {
                Status status = data.roomStatus(unit);
                Button tile = button("room-tile-" + unit.room().number(), "", false, () -> showDetail(unit, refresh));
                tile.setAccessibleText("Kamar " + unit.room().number() + ", " + status);

                Color stateColor = switch (status) {
                    case READY -> TEAL;
                    case BOOKED -> Color.web("#7446B3");
                    case OCCUPIED -> Color.web("#2557A3");
                    case CLEANING -> Color.web("#87500A");
                    case MAINTENANCE -> RED;
                };
                Color baseFill = stateColor.interpolate(Color.WHITE, 0.91);
                Color hoverFill = stateColor.interpolate(Color.WHITE, 0.84);

                tile.setBackground(fill(baseFill, 9));
                tile.hoverProperty().addListener((obs, wasHover, isHover) ->
                        tile.setBackground(fill(isHover ? hoverFill : baseFill, 9)));
                tile.setGraphic(new VBox(5, title(unit.room().number()), caption(unit.type().label()),
                        label(status.toString(), 12, FontWeight.BOLD, stateColor)));
                tile.setPrefWidth(compact ? 110 : 152);
                tile.setPrefHeight(compact ? 84 : 100);
                tile.setMaxHeight(Region.USE_PREF_SIZE);
                tile.setPadding(new Insets(compact ? 8 : 13));
                tiles.getChildren().add(tile);
            }
            result.getChildren().add(card(title("Lantai " + floor), tiles));
        }

        if (units.isEmpty()) {
            result.getChildren().add(card(caption("Tidak ada kamar yang cocok.")));
        }
        return result;
    }

    private void showDetail(Unit unit, Runnable refresh) {
        if (app.isMaintenance()) {
            app.maintenance().showRoom(unit, refresh);
            return;
        }
        String number = unit.room().number();
        var occupant = data.occupant(number);
        ComboBox<Condition> condition = select("room-condition-detail", List.of(Condition.values()), unit.condition());

        VBox body = card(
                title(unit.type().label() + " · Lantai " + unit.floor()),
                caption(data.roomStatus(unit) + "  •  " + money(unit.room().rate()) + " per malam  •  " + unit.type().capacity() + " tamu"),
                caption(inventory.facilities(unit)),
                caption(unit.notes().isBlank() ? "Belum ada catatan." : unit.notes()));

        if (occupant == null) {
            body.getChildren().add(field("Kondisi kamar", condition));
            edit(app.owner(), "Detail Kamar " + number, body, "Simpan kondisi",
                    () -> data.setRoomCondition(number, condition.getValue()), refresh);
        } else {
            body.getChildren().add(caption("Tamu menginap: " + occupant.guest() + " · " + occupant.code()));
            detail(app.owner(), "Detail Kamar " + number, body);
        }
    }

    private void editRoom(Unit unit) {
        app.selectRoom(unit == null ? null : unit.room().number());
        app.open(HotelRoute.ROOM_FORM);
    }

    private VBox form() {
        Unit original = (app.selectedRoom() == null) ? null : inventory.find(app.selectedRoom());
        boolean create = (original == null);
        String heading = create ? "Tambah Kamar" : "Ubah Kamar " + original.room().number();
        Runnable back = () -> {
            app.selectRoom(null);
            app.open(HotelRoute.ROOMS);
        };

        VBox page = page(heading, "Atur nomor, lantai, tipe, dan kondisi operasional.",
                button("room-form-back", "Kembali", false, back));

        TextField number = text("room-number", create ? "" : original.room().number(), "Contoh: 106");
        number.setEditable(create);

        ComboBox<Integer> floor = select("room-form-floor",
                IntStream.rangeClosed(1, 50).boxed().toList(), create ? 1 : original.floor());
        List<Type> types = inventory.types();
        Type initialType = create ? (types.isEmpty() ? null : types.getFirst()) : original.type();
        ComboBox<Type> type = select("room-form-type", types, initialType);
        ComboBox<Condition> condition = select("room-form-condition",
                List.of(Condition.values()), create ? Condition.READY : original.condition());

        TextArea notes = new TextArea(create ? "" : original.notes());
        notes.setId("room-notes");
        notes.setPromptText("Masukkan catatan kondisi atau lokasi kamar");
        notes.setPrefRowCount(3);
        notes.setWrapText(true);

        Label preview = caption("");
        Runnable update = () -> preview.setText(type.getValue() == null
                ? "Tambahkan tipe kamar terlebih dahulu."
                : type.getValue().label() + "  •  " + money(type.getValue().rate())
                        + " per malam  •  " + type.getValue().capacity() + " tamu  •  " + type.getValue().facilities());
        type.valueProperty().addListener((obs, old, value) -> update.run());
        update.run();

        Label error = label("", 13, FontWeight.NORMAL, RED);
        error.setId("room-form-error");

        TextArea facilities = new TextArea(create ? "" : inventory.customFacilities(original.room().number()));
        facilities.setId("room-facilities");
        facilities.setPromptText("Kosongkan untuk mengikuti fasilitas standar tipe kamar.");
        facilities.setPrefRowCount(2);
        facilities.setWrapText(true);

        Button save = button("room-form-save", create ? "Simpan Kamar" : "Simpan Perubahan", true, () -> {
            try {
                String custom = facilities.getText().strip();
                if (app.isOwner() && custom.length() > 400) {
                    throw new IllegalArgumentException("Fasilitas maksimal 400 karakter.");
                }
                data.saveRoom(number.getText().strip(), floor.getValue(), type.getValue(),
                        condition.getValue(), notes.getText().strip(), create);
                if (app.isOwner()) {
                    inventory.setFacilities(number.getText().strip(), custom);
                }
                back.run();
            } catch (IllegalArgumentException invalid) {
                error.setText(invalid.getMessage());
            }
        });

        Button reset = button("room-form-reset", "Reset Formulir", false, () -> {
            if (create) {
                number.clear();
            }
            floor.setValue(create ? 1 : original.floor());
            type.setValue(initialType);
            condition.setValue(create ? Condition.READY : original.condition());
            notes.setText(create ? "" : original.notes());
            facilities.setText(create ? "" : inventory.customFacilities(original.room().number()));
            error.setText("");
        });

        VBox fields = card(title("Informasi Unit & Penempatan"),
                field("Nomor kamar", number),
                field("Lantai", floor),
                field("Tipe kamar dan tarif", type),
                preview,
                field("Kondisi operasional", condition),
                field("Catatan (opsional)", notes),
                error,
                row(reset, spacer(), button("room-form-cancel", "Batal", false, back), save));
        fields.setMaxWidth(780);

        if (app.isOwner()) {
            int beforeActions = fields.getChildren().size() - 2;
            VBox facilitiesField = field("Fasilitas khusus unit", facilities);
            facilitiesField.managedProperty().bind(facilitiesField.visibleProperty());
            facilitiesField.setVisible(!facilities.getText().isBlank());
            fields.getChildren().addAll(beforeActions, List.of(
                    button("room-edit-facilities", "Ubah Fasilitas", false, () -> facilitiesField.setVisible(true)),
                    facilitiesField));
            if (!create) {
                page.getChildren().add(keycard(original));
            }
        }
        page.getChildren().add(1, fields);
        return page;
    }

    private VBox keycard(Unit unit) {
        String number = unit.room().number();
        CardRequest current = inventory.cardRequest(number);
        Label feedback = caption(current == null
                ? "Belum ada permintaan untuk unit ini."
                : "Menunggu pemrograman · Keycard " + current.identifier());

        Button program = button("room-program-keycard", "Program Ulang", false, () -> {
            CardRequest previous = inventory.cardRequest(number);
            TextField identifier = text("room-keycard-id", previous == null ? "" : previous.identifier(), "ID pada kartu");
            edit(app.owner(), "Program Ulang Keycard · Kamar " + number,
                    card(caption("Catat permintaan pemrograman untuk perangkat RFID."), field("ID keycard", identifier)),
                    "Ajukan permintaan", () -> inventory.requestCard(number, identifier.getText().strip()),
                    () -> feedback.setText("Menunggu pemrograman · Keycard " + inventory.cardRequest(number).identifier()));
        });

        VBox card = card(row(title("Keycard RFID"), spacer(), program), feedback);
        card.setMaxWidth(780);
        return card;
    }

    private String searchKey(Unit unit) {
        if (app.isMaintenance()) {
            Job active = inventory.activeJob(unit.room().number());
            return unit.room().number() + " " + unit.type().label() + " lantai " + unit.floor() + " " + unit.notes()
                    + (active == null ? "" : " " + active.code() + " " + active.description());
        }
        var occupant = data.occupant(unit.room().number());
        return unit.room().number() + " " + unit.type().label() + " lantai " + unit.floor()
                + (occupant == null ? "" : " " + occupant.guest());
    }

    private List<String> floorChoices() {
        List<String> result = new ArrayList<>(List.of(ALL_FLOORS));
        inventory.units().stream().map(Unit::floor).distinct().sorted()
                .map(floor -> "Lantai " + floor).forEach(result::add);
        return result;
    }

    private List<String> typeChoices() {
        List<String> choices = new ArrayList<>(List.of(ALL_TYPES));
        inventory.types().stream().map(Type::label).forEach(choices::add);
        return choices;
    }

    private static boolean matches(String filter, String all, String value) {
        return all.equals(filter) || value.equals(filter);
    }

    private static boolean contains(String value, String query) {
        return value.toLowerCase(Locale.ROOT).contains(query.strip().toLowerCase(Locale.ROOT));
    }
}