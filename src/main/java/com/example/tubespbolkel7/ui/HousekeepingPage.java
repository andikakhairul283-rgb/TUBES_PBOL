package com.example.tubespbolkel7.ui;

import com.example.tubespbolkel7.model.HotelData;
import com.example.tubespbolkel7.model.MaintenanceStock;
import com.example.tubespbolkel7.model.RoomInventory;
import javafx.collections.FXCollections;
import javafx.print.PrinterJob;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.example.tubespbolkel7.model.RoomInventory.Condition;
import static com.example.tubespbolkel7.model.RoomInventory.Job;
import static com.example.tubespbolkel7.model.RoomInventory.Material;
import static com.example.tubespbolkel7.model.RoomInventory.Priority;
import static com.example.tubespbolkel7.ui.PageComponents.*;

final class HousekeepingPage {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.forLanguageTag("id-ID"));
    private final HotelOperations app;
    private final RoomInventory inventory;

    HousekeepingPage(HotelOperations app) {
        this.app = app;
        this.inventory = app.data().inventory();
    }

    Node view() {
        TextField search = text("housekeeping-search", "", "Cari nomor kamar, tiket, kendala, atau petugas...");
        ComboBox<String> category = select("housekeeping-category", List.of("Semua jenis", "Pembersihan", "Maintenance"), "Semua jenis");
        ComboBox<String> state = select("housekeeping-status", List.of("Tugas aktif", "Selesai", "Semua tugas"), "Tugas aktif");
        ComboBox<String> priority = select("housekeeping-priority", List.of("Semua prioritas", "Normal", "Tinggi", "Darurat"), "Semua prioritas");
        TableView<Job> tasks = table("housekeeping-table", "Belum ada tugas untuk filter ini.");
        Label summary = caption("");
        Label feedback = caption("");

        Supplier<List<Job>> matching = () -> inventory.jobs().stream()
                .filter(job -> "Semua jenis".equals(category.getValue()) || category(job).equals(category.getValue()))
                .filter(job -> "Semua tugas".equals(state.getValue()) || job.active() == "Tugas aktif".equals(state.getValue()))
                .filter(job -> "Semua prioritas".equals(priority.getValue()) || job.priority().toString().equals(priority.getValue()))
                .filter(job -> (job.code() + " " + job.room() + " " + job.description() + " " + job.assignee())
                        .toLowerCase(Locale.ROOT).contains(search.getText().strip().toLowerCase(Locale.ROOT)))
                .toList();

        Runnable refresh = () -> {
            List<Job> visible = matching.get();
            tasks.setItems(FXCollections.observableArrayList(visible));
            sizeTable(tasks, visible.size());
            long cleaning = inventory.jobs().stream().filter(job -> job.active() && job.condition() == Condition.CLEANING).count();
            long maintenance = inventory.jobs().stream().filter(job -> job.active() && job.condition() == Condition.MAINTENANCE).count();
            summary.setText(cleaning + " pembersihan aktif  •  " + maintenance + " perbaikan aktif  •  " + visible.size() + " tugas terlihat");
        };

        tasks.getColumns().addAll(List.of(
                column("Tiket", Job::code),
                column("Kamar", Job::room),
                column("Jenis", HousekeepingPage::category),
                column("Prioritas", job -> job.priority().toString()),
                column("Petugas", job -> job.assignee().isBlank() ? "Belum ditugaskan" : job.assignee()),
                column("Status", job -> job.active() ? "Aktif" : "Selesai"),
                actions(job -> row(
                        button("housekeeping-detail-" + job.code(), "Detail", false, () -> openJob(job, refresh)),
                        button("housekeeping-action-" + job.code(), job.active() ? "Selesaikan" : "Riwayat", job.active(),
                                () -> {
                                    if (job.active()) {
                                        finish(job, refresh);
                                    } else {
                                        openJob(job, refresh);
                                    }
                                })
                ))
        ));
        tasks.getColumns().getLast().setMinWidth(168);

        if (app.isMaintenance()) {
            tasks.getColumns().add(2, column("Kendala", Job::description));
            tasks.getColumns().add(5, column("Estimasi",
                    job -> job.estimatedMinutes() == 0 ? "—" : job.estimatedMinutes() + " menit"));
        }

        search.setPrefWidth(290);
        search.textProperty().addListener((obs, old, value) -> refresh.run());
        for (ComboBox<String> filter : List.of(category, state, priority)) {
            filter.valueProperty().addListener((obs, old, value) -> refresh.run());
        }

        Button export = button("housekeeping-export", "Ekspor Lembar Kerja", false, () -> {
            List<List<?>> rows = matching.get().stream().<List<?>>map(job -> List.of(
                    job.code(), job.room(), category(job),
                    job.priority().toString(), job.assignee(), job.active() ? "Aktif" : "Selesai", job.description()
            )).toList();
            if (rows.isEmpty()) {
                feedback.setText("Belum ada tugas untuk diekspor.");
            } else {
                export(app.owner(), "Lembar Kerja Housekeeping", false,
                        List.of("Tiket", "Kamar", "Jenis", "Prioritas", "Petugas", "Status", "Catatan"), rows, feedback::setText);
            }
        });

        Button create = button("housekeeping-new", "+ Buat Tugas", true, () -> addJob(refresh, false, null));
        String heading = "Housekeeping & Maintenance";
        String description = "Kelola antrean pembersihan, tiket perbaikan, dan permintaan material.";

        VBox root = (app.isOwner() || app.isMaintenance())
                ? page(heading, description, export,
                        button("housekeeping-print", "Cetak Lembar Kerja", false, () -> printSheet(matching.get())),
                        button("housekeeping-critical", "Lapor Kerusakan Kritis", false, () -> addJob(refresh, true, null)),
                        create)
                : page(heading, description, export, create);

        FlowPane filters = new FlowPane(9, 8, search, category, state, priority);
        VBox list = card(title("Tugas Kamar"), summary, filters, tasks);

        if (app.isMaintenance()) {
            FlowPane tabs = new FlowPane(8, 8);
            Runnable updateTabs = () -> tabs.getChildren().setAll(
                    categoryTab("all", "Semua jenis", "Semua", category),
                    categoryTab("cleaning", "Pembersihan", "Pembersihan", category),
                    categoryTab("repair", "Maintenance", "Maintenance", category)
            );
            category.valueProperty().addListener((obs, old, value) -> updateTabs.run());
            list.getChildren().add(2, tabs);
            list.getChildren().add(button("housekeeping-refresh", "Perbarui Data", false, () -> {
                refresh.run();
                updateTabs.run();
            }));
            tasks.itemsProperty().addListener((obs, old, value) -> updateTabs.run());
            updateTabs.run();
        }

        FlowPane tools = new FlowPane(9, 8,
                button("housekeeping-history", "Lihat Riwayat Unit", false, () -> history(null)),
                button("housekeeping-materials", "Permintaan Material", false, this::materials));
        if (app.isMaintenance()) {
            tools.getChildren().addAll(
                    button("housekeeping-request-material", "Ajukan Permintaan Material", false, this::chooseMaterialJob),
                    button("housekeeping-stock", "Suku Cadang Siaga", false, () -> MaintenanceStockPage.show(app, () -> { }))
            );
        }

        list.getChildren().add(tools);
        root.getChildren().addAll(list, feedback);
        refresh.run();
        return root;
    }

    private Button categoryTab(String id, String value, String label, ComboBox<String> selected) {
        long count = inventory.jobs().stream().filter(Job::active)
                .filter(job -> value.equals("Semua jenis") || value.equals(category(job))).count();
        return button("housekeeping-tab-" + id, label + " (" + count + ")", value.equals(selected.getValue()),
                () -> selected.setValue(value));
    }

    void addJob(Runnable refresh, boolean critical, String initialRoom) {
        ComboBox<String> room = select("job-room", inventory.rooms().stream().map(HotelData.Room::number).toList(), initialRoom);
        ComboBox<Condition> type = select("job-type", List.of(Condition.CLEANING, Condition.MAINTENANCE), Condition.MAINTENANCE);
        ComboBox<Priority> priority = select("job-priority", List.of(Priority.values()), critical ? Priority.URGENT : Priority.NORMAL);
        type.setDisable(critical);
        priority.setDisable(critical);

        TextField assignee = text("job-assignee", app.isMaintenance() ? app.username() : "", "Nama petugas (opsional)");
        TextArea description = notes("job-description", "");
        TextField estimate = estimate(0);

        edit(app.owner(), critical ? "Lapor Kerusakan Kritis" : "Buat Tugas Kamar",
                card(field("Kamar", room), field("Jenis tugas", type), field("Prioritas", priority),
                        field("Petugas", assignee), field("Estimasi pengerjaan (menit)", estimate), field("Deskripsi", description)),
                "Simpan tugas",
                () -> {
                    if (room.getValue() == null) {
                        throw new IllegalArgumentException("Pilih kamar.");
                    }
                    app.data().reportRoom(room.getValue(), type.getValue(), priority.getValue(),
                            description.getText(), assignee.getText(), minutes(estimate));
                    if (app.isMaintenance()) {
                        app.maintenance().notifyStatus(room.getValue());
                    }
                }, refresh);
    }

    private void printSheet(List<Job> jobs) {
        if (jobs.isEmpty()) {
            detail(app.owner(), "Cetak Lembar Kerja", card(caption("Belum ada tugas untuk filter ini.")));
            return;
        }
        VBox preview = card(title("Lembar Kerja Housekeeping"), caption(jobs.size() + " tugas pada filter aktif"));
        VBox entries = new VBox(8);
        jobs.forEach(job -> entries.getChildren().add(
                caption(job.code() + " · Kamar " + job.room() + " · " + job.description())));

        ScrollPane list = new ScrollPane(entries);
        list.setFitToWidth(true);
        list.setPrefViewportHeight(240);
        preview.getChildren().add(list);

        Dialog<ButtonType> dialog = dialog(app.owner(), "Pratinjau Lembar Kerja", preview);
        ButtonType print = new ButtonType("Cetak", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, print);
        dialog.showAndWait().filter(print::equals).ifPresent(result -> printJobs(jobs));
    }

    private void printJobs(List<Job> jobs) {
        PrinterJob printer = PrinterJob.createPrinterJob();
        if (printer == null) {
            detail(app.owner(), "Cetak Lembar Kerja", card(caption("Printer belum tersedia di perangkat ini.")));
            return;
        }
        if (!printer.showPrintDialog(app.owner())) {
            printer.cancelJob();
            return;
        }
        double width = printer.getJobSettings().getPageLayout().getPrintableWidth();
        double height = printer.getJobSettings().getPageLayout().getPrintableHeight();

        for (int first = 0; first < jobs.size(); first += 6) {
            VBox sheet = card(title("Lembar Kerja Housekeeping"), caption(app.data().settings().hotel().name()));
            for (Job job : jobs.subList(first, Math.min(first + 6, jobs.size()))) {
                sheet.getChildren().add(caption(job.code() + " · Kamar " + job.room() + " · " + category(job) + " · " + job.priority()));
                sheet.getChildren().add(caption(job.description()));
            }
            new Scene(sheet, width, height);
            sheet.applyCss();
            sheet.layout();
            if (!printer.printPage(sheet)) {
                printer.cancelJob();
                detail(app.owner(), "Cetak Lembar Kerja", card(caption("Pencetakan belum berhasil.")));
                return;
            }
        }
        printer.endJob();
    }

    void openJob(Job job, Runnable refresh) {
        if (!job.active()) {
            detail(app.owner(), "Riwayat " + job.code(), card(
                    title("Kamar " + job.room() + " · " + category(job)),
                    caption(job.description()),
                    caption("Dibuka " + DATE.format(job.opened())),
                    caption("Selesai " + DATE.format(job.finished())),
                    caption(job.assignee().isBlank() ? "Belum ada petugas tercatat." : "Petugas · " + job.assignee())
            ));
            return;
        }
        ComboBox<Priority> priority = select("job-priority", List.of(Priority.values()), job.priority());
        TextField assignee = text("job-assignee", job.assignee(), "Nama petugas (opsional)");
        TextArea description = notes("job-description", job.description());
        TextField estimate = estimate(job.estimatedMinutes());
        Button materials = button("job-request-material", "Ajukan Permintaan Material", false, () -> request(job));

        edit(app.owner(), "Detail " + job.code(),
                card(title("Kamar " + job.room() + " · " + category(job)),
                        caption("Dibuka " + DATE.format(job.opened())),
                        field("Prioritas", priority),
                        field("Petugas", assignee),
                        field("Estimasi pengerjaan (menit)", estimate),
                        field("Deskripsi", description),
                        materials),
                "Simpan perubahan",
                () -> inventory.editJob(job, priority.getValue(), description.getText(), assignee.getText(), minutes(estimate)),
                refresh);
    }

    private void finish(Job job, Runnable refresh) {
        edit(app.owner(), "Selesaikan Tugas",
                card(title(job.code() + " · Kamar " + job.room()),
                        caption("Konfirmasi setelah kamar selesai ditangani dan siap digunakan.")),
                "Tandai selesai",
                () -> {
                    inventory.finish(job);
                    if (app.isMaintenance()) {
                        app.maintenance().notifyStatus(job.room());
                    }
                }, refresh);
    }

    private void chooseMaterialJob() {
        List<Job> active = inventory.jobs().stream().filter(Job::active).toList();
        if (active.isEmpty()) {
            detail(app.owner(), "Permintaan Material", card(caption("Buat tugas aktif untuk mengajukan material.")));
            return;
        }
        ComboBox<Job> ticket = select("job-material-ticket", active, active.getFirst());
        ticket.setConverter(new StringConverter<>() {
            @Override
            public String toString(Job job) {
                return job == null ? "" : job.code() + " · Kamar " + job.room();
            }

            @Override
            public Job fromString(String text) {
                return null;
            }
        });
        edit(app.owner(), "Pilih Tiket", card(field("Tiket aktif", ticket)), "Lanjut", () -> {
            if (ticket.getValue() == null) {
                throw new IllegalArgumentException("Pilih tiket aktif.");
            }
        }, () -> request(ticket.getValue()));
    }

    private void request(Job job) {
        ComboBox<String> name = select("job-material-name",
                inventory.stock().items().stream().map(MaintenanceStock.Item::name).toList(), null);
        name.setEditable(true);
        name.setPromptText("Pilih atau ketik nama material");

        Spinner<Integer> quantity = new Spinner<>(1, 1000, 1);
        quantity.setId("job-material-quantity");
        Label note = caption("Tiket " + job.code() + " · Kamar " + job.room());

        edit(app.owner(), "Permintaan Material",
                card(note, field("Nama material", name), field("Jumlah", quantity)),
                "Ajukan",
                () -> inventory.requestMaterial(job, name.getEditor().getText(), quantity.getValue()),
                () -> detail(app.owner(), "Permintaan Material", card(caption("Permintaan material telah tersimpan."))));
    }

    void history(String initialRoom) {
        List<String> choices = Stream.concat(
                Stream.of("Semua kamar"),
                inventory.rooms().stream().map(HotelData.Room::number)
        ).toList();
        ComboBox<String> room = select("housekeeping-history-room", choices,
                initialRoom == null ? "Semua kamar" : initialRoom);

        TableView<Job> history = table("housekeeping-history-table", "Belum ada riwayat tugas untuk kamar ini.");
        history.getColumns().addAll(List.of(
                column("Tiket", Job::code),
                column("Kamar", Job::room),
                column("Jenis", HousekeepingPage::category),
                column("Selesai", job -> DATE.format(job.finished()))
        ));

        Runnable filter = () -> {
            List<Job> jobs = inventory.jobs().stream()
                    .filter(job -> !job.active())
                    .filter(job -> "Semua kamar".equals(room.getValue()) || job.room().equals(room.getValue()))
                    .toList();
            history.getItems().setAll(jobs);
            sizeTable(history, Math.min(5, jobs.size()));
        };
        room.valueProperty().addListener((obs, old, value) -> filter.run());
        filter.run();
        detail(app.owner(), "Riwayat Unit", card(field("Kamar", room), history));
    }

    private void materials() {
        TableView<Material> table = table("housekeeping-materials-table", "Belum ada permintaan material.");
        table.getColumns().addAll(List.of(
                column("Tiket", Material::job),
                column("Kamar", Material::room),
                column("Material", Material::name),
                column("Jumlah", material -> Integer.toString(material.quantity())),
                column("Diajukan", material -> DATE.format(material.requested()))
        ));
        table.getItems().setAll(inventory.materials());
        sizeTable(table, Math.min(5, inventory.materials().size()));
        detail(app.owner(), "Permintaan Material", card(table));
    }

    private static String category(Job job) {
        return job.condition() == Condition.CLEANING ? "Pembersihan" : "Maintenance";
    }

    private static TextArea notes(String id, String value) {
        TextArea area = new TextArea(value);
        area.setId(id);
        area.setPrefRowCount(3);
        area.setWrapText(true);
        return area;
    }

    private static TextField estimate(int minutes) {
        return text("job-estimate", minutes == 0 ? "" : Integer.toString(minutes), "Opsional");
    }

    private static int minutes(TextField field) {
        String value = field.getText().strip();
        if (value.isEmpty()) {
            return 0;
        }
        if (!value.matches("[0-9]{1,5}")) {
            throw new IllegalArgumentException("Estimasi harus berupa jumlah menit.");
        }
        return Integer.parseInt(value);
    }
}