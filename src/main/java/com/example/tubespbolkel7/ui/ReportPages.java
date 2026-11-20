package com.example.tubespbolkel7.ui;

import com.example.tubespbolkel7.report.ReportData;
import com.example.tubespbolkel7.report.ReportExport;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

import java.io.IOException;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import static com.example.tubespbolkel7.report.ReportData.Day;
import static com.example.tubespbolkel7.report.ReportData.Filter;
import static com.example.tubespbolkel7.report.ReportData.Report;
import static com.example.tubespbolkel7.report.ReportData.percent;

final class ReportPages {
    private static final Color NAVY = Color.web("#102A4B");
    private static final Color TEAL = Color.web("#006A60");
    private static final Color MUTED = Color.web("#596779");
    private static final Color PALE = Color.web("#EDF2FE");
    private static final Color BG = Color.web("#F8F9FF");
    private static final Color RED = Color.web("#B42318");
    private static final Locale INDONESIAN = Locale.forLanguageTag("id-ID");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", INDONESIAN);
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("dd MMM", INDONESIAN);
    private static final int PAGE_SIZE = 7;

    private final HotelOperations app;
    private Filter filter;

    ReportPages(HotelOperations app, LocalDate today) {
        this.app = app;
        filter = Filter.currentMonth(today);
    }

    Node view(HotelRoute route) {
        return new Page(route == HotelRoute.REVENUE).root;
    }

    private final class Page {
        private final boolean revenue;
        private final VBox root = new VBox(19);
        private final VBox reportBody = new VBox(18);
        private final Label message = label("", 13, FontWeight.NORMAL, MUTED);
        private final DatePicker from = datePicker("report-from", filter.start());
        private final DatePicker until = datePicker("report-to", filter.end());
        private final ComboBox<String> type = new ComboBox<>();
        private final Button excel = button("Export Excel", false, () -> export(false));
        private final Button pdf = button("Export PDF", false, () -> export(true));
        private Report report;
        private List<Day> rows;
        private int pageIndex;

        Page(boolean revenue) {
            this.revenue = revenue;
            root.setPadding(new Insets(25));
            root.setMinHeight(Region.USE_PREF_SIZE);
            root.setBackground(fill(BG, 0));
            VBox heading = new VBox(4, label("Laporan", 30, FontWeight.BOLD, NAVY),
                    label("Okupansi, tamu, dan pendapatan hotel", 14, FontWeight.NORMAL, MUTED));
            HBox tabs = new HBox(6,
                    identified(button("Okupansi & Tamu", !revenue, () -> app.open(HotelRoute.OCCUPANCY)), "report-occupancy"),
                    identified(button("Pendapatan", revenue, () -> app.open(HotelRoute.REVENUE)), "report-revenue"));
            tabs.setPadding(new Insets(5));
            tabs.setBackground(fill(PALE, 10));
            tabs.setMaxWidth(Region.USE_PREF_SIZE);

            type.setId("report-room-type");
            type.getItems().add(ReportData.ALL_TYPES);
            app.data().inventory().types().stream().map(item -> item.label()).forEach(type.getItems()::add);
            if (!type.getItems().contains(filter.roomType())) {
                filter = new Filter(filter.start(), filter.end(), ReportData.ALL_TYPES);
            }
            type.setValue(filter.roomType());
            type.setMinHeight(36);
            type.setBackground(fill(PALE, 8));
            excel.setId("report-export-excel");
            pdf.setId("report-export-pdf");
            message.setId("report-message");
            message.setWrapText(true);
            message.managedProperty().bind(message.visibleProperty());
            message.setVisible(false);
            FlowPane filters = new FlowPane(10, 9, field("Tanggal mulai", from), field("Tanggal selesai", until),
                    field("Tipe kamar", type), identified(button("Terapkan", true, this::apply), "report-apply"), excel, pdf);
            filters.setAlignment(Pos.BOTTOM_LEFT);
            root.getChildren().addAll(heading, tabs, card(filters, message), reportBody);
            refresh();
        }

        private void apply() {
            try {
                Filter selection = new Filter(from.getValue(), until.getValue(), type.getValue());
                selection.validate();
                filter = selection;
                pageIndex = 0;
                message.setVisible(false);
                refresh();
            } catch (IllegalArgumentException invalid) {
                showMessage(invalid.getMessage(), true);
            }
        }

        private void refresh() {
            report = ReportData.calculate(app.data(), filter);
            rows = report.days().reversed().stream().filter(day -> revenue
                    ? !day.transactions().isEmpty() : !day.activity().isEmpty()).toList();
            excel.setDisable(rows.isEmpty());
            pdf.setDisable(rows.isEmpty());
            HBox metrics = revenue ? new HBox(12,
                    metric("TOTAL PENDAPATAN", money(report.total()), "Penerimaan tercatat"),
                    metric("UANG MUKA", money(report.deposits()), "Termasuk penyesuaian uang muka"),
                    metric("PEMBAYARAN", money(report.payments()), "Transaksi yang dicatat"),
                    metric("RATA-RATA PER HARI", money(report.days().isEmpty() ? 0 : Math.round(report.total() / (double) report.days().size())),
                            report.days().size() + " hari tercakup"))
                    : new HBox(12,
                    metric("RATA-RATA OKUPANSI", percentage(report.occupancy()), "Kamar-malam terisi"),
                    metric("TOTAL TAMU", Long.toString(report.stays()), "Dihitung per reservasi check-in"),
                    metric("RATA-RATA LAMA INAP", decimal(report.nightsPerStay()) + " malam", "Malam dalam periode"),
                    metric("TINGKAT PEMBATALAN", percentage(percent(report.cancellations(), report.reservations())),
                            "Per jadwal kedatangan"));
            metrics.getChildren().forEach(node -> HBox.setHgrow(node, Priority.ALWAYS));
            VBox chart = card(label(revenue ? "Pendapatan per Hari" : "Okupansi Harian", 18, FontWeight.BOLD, NAVY),
                    label(period(), 12, FontWeight.NORMAL, MUTED), chart());
            chart.setMinWidth(0);
            HBox.setHgrow(chart, Priority.ALWAYS);
            VBox breakdown = breakdown();
            breakdown.setPrefWidth(275);
            breakdown.setMinWidth(188);
            HBox charts = new HBox(14, chart, breakdown);
            VBox detail = card();
            renderTable(detail);
            reportBody.getChildren().setAll(metrics, charts, detail);
        }

        private Node chart() {
            if (rows.isEmpty()) {
                Label empty = label(revenue ? "Belum ada penerimaan pada periode dan tipe kamar ini."
                        : "Belum ada aktivitas check-in atau check-out untuk filter ini.", 14, FontWeight.NORMAL, MUTED);
                empty.setWrapText(true);
                empty.setMinHeight(172);
                return empty;
            }
            List<Day> days = report.days();
            int offset = days.size() == 1 ? 1 : 0;
            NumberAxis x = days.size() == 1 ? new NumberAxis(0, 2, 1)
                    : new NumberAxis(0, days.size() - 1, Math.max(1, (days.size() - 1) / 5));
            x.setMinorTickVisible(false);
            x.setTickLabelFormatter(new StringConverter<>() {
                @Override public String toString(Number index) {
                    int i = index.intValue() - offset;
                    return i >= 0 && i < days.size() ? SHORT_DATE.format(days.get(i).date()) : "";
                }
                @Override public Number fromString(String text) { return 0; }
            });
            NumberAxis y = revenue ? new NumberAxis() : new NumberAxis(0, 100, 25);
            y.setLabel(revenue ? "Rp" : "%");
            y.setMinorTickVisible(false);
            LineChart<Number, Number> chart = new LineChart<>(x, y);
            chart.setAnimated(false);
            chart.setLegendVisible(false);
            chart.setVerticalZeroLineVisible(false);
            chart.setCreateSymbols(days.size() <= 45);
            chart.setMinWidth(0);
            chart.setPrefHeight(235);
            XYChart.Series<Number, Number> series = new XYChart.Series<>();
            for (int index = 0; index < days.size(); index++) {
                Day day = days.get(index);
                XYChart.Data<Number, Number> point = new XYChart.Data<>(index + offset, revenue ? day.total() : day.occupancy());
                point.nodeProperty().addListener((observable, old, node) -> {
                    if (node == null) return;
                    if (node instanceof Region marker) marker.setBackground(fill(TEAL, 8));
                    Tooltip.install(node, new Tooltip(DATE.format(day.date()) + " • "
                            + (revenue ? money(day.total()) : percentage(day.occupancy()))));
                    if (rows.contains(day)) {
                        node.setCursor(Cursor.HAND);
                        node.setOnMouseClicked(event -> details(day));
                    }
                });
                series.getData().add(point);
            }
            series.nodeProperty().addListener((observable, old, node) -> {
                if (node instanceof javafx.scene.shape.Path line) line.setStroke(TEAL);
            });
            chart.getData().add(series);
            return chart;
        }

        private VBox breakdown() {
            VBox panel = card(label(revenue ? "Sumber Pendapatan" : "Okupansi per Tipe Kamar", 17, FontWeight.BOLD, NAVY),
                    label(revenue ? "Berdasarkan setoran yang dicatat" : "Kapasitas dan hunian aktual", 12, FontWeight.NORMAL, MUTED));
            if (revenue) {
                long baseline = Math.abs(report.deposits()) + Math.abs(report.payments());
                panel.getChildren().addAll(progress("Uang muka", money(report.deposits()), percent(Math.abs(report.deposits()), baseline)),
                        progress("Pembayaran", money(report.payments()), percent(Math.abs(report.payments()), baseline)));
            } else {
                for (ReportData.RoomType room : report.roomTypes()) {
                    double occupancy = percent(room.occupiedNights(), room.rooms() * report.days().size());
                    panel.getChildren().add(progress(room.name() + "  ·  " + room.rooms() + " kamar", percentage(occupancy), occupancy));
                }
            }
            return panel;
        }

        private void renderTable(VBox container) {
            TableView<Day> table = new TableView<>();
            table.setId("report-daily-table");
            table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
            table.setFixedCellSize(43);
            table.setPlaceholder(label(revenue ? "Catat pembayaran untuk mengisi laporan." : "Proses check-in untuk mengisi laporan.",
                    13, FontWeight.NORMAL, MUTED));
            table.getColumns().add(column("Tanggal", day -> DATE.format(day.date())));
            if (revenue) {
                table.getColumns().addAll(List.of(column("Uang muka", day -> money(day.deposits())),
                        column("Pembayaran", day -> money(day.payments())), column("Total pendapatan", day -> money(day.total())),
                        column("Transaksi", day -> Integer.toString(day.transactions().size()))));
            } else {
                table.getColumns().addAll(List.of(column("Kamar terisi", day -> day.occupied() + " / " + day.capacity()),
                        column("Okupansi", day -> percentage(day.occupancy())), column("Check-in", day -> Long.toString(day.arrivals())),
                        column("Check-out", day -> Long.toString(day.departures()))));
            }
            TableColumn<Day, Day> actions = new TableColumn<>("Aksi");
            actions.setSortable(false);
            actions.setCellValueFactory(value -> new ReadOnlyObjectWrapper<>(value.getValue()));
            actions.setCellFactory(column -> new TableCell<>() {
                @Override protected void updateItem(Day day, boolean empty) {
                    super.updateItem(day, empty);
                    setGraphic(empty || day == null ? null
                            : identified(button("Detail", false, () -> details(day)), "report-detail-" + day.date()));
                }
            });
            table.getColumns().add(actions);
            int start = pageIndex * PAGE_SIZE;
            int end = Math.min(start + PAGE_SIZE, rows.size());
            table.setItems(FXCollections.observableArrayList(rows.subList(start, end)));
            table.setPrefHeight(38 + Math.max(2, end - start) * 43);
            table.setMinHeight(Region.USE_PREF_SIZE);
            Button source = identified(button(revenue ? "Buka Pembayaran" : "Buka Reservasi", false,
                    () -> app.open(revenue ? HotelRoute.PAYMENTS : HotelRoute.RESERVATIONS)), "report-source");
            HBox title = row(label(revenue ? "Rincian Pendapatan" : "Rincian Harian", 18, FontWeight.BOLD, NAVY), spacer(), source);
            container.getChildren().setAll(title, table);
            if (!rows.isEmpty()) container.getChildren().add(pagination(container, start, end));
        }

        private HBox pagination(VBox container, int start, int end) {
            int count = (rows.size() + PAGE_SIZE - 1) / PAGE_SIZE;
            Button previous = identified(button("‹", false, () -> changePage(container, pageIndex - 1)), "report-previous");
            Button next = identified(button("›", false, () -> changePage(container, pageIndex + 1)), "report-next");
            previous.setDisable(pageIndex == 0);
            next.setDisable(pageIndex + 1 >= count);
            HBox bar = row(label((start + 1) + "–" + end + " dari " + rows.size() + " hari", 12, FontWeight.NORMAL, MUTED),
                    spacer(), previous);
            for (int index = Math.max(0, pageIndex - 2); index < Math.min(count, pageIndex + 3); index++) {
                int target = index;
                bar.getChildren().add(identified(button(Integer.toString(index + 1), index == pageIndex,
                        () -> changePage(container, target)), "report-page-" + (index + 1)));
            }
            bar.getChildren().add(next);
            return bar;
        }

        private void changePage(VBox container, int page) {
            pageIndex = page;
            renderTable(container);
        }

        private void details(Day day) {
            Dialog<ButtonType> dialog = new Dialog<>();
            if (app.owner() != null) dialog.initOwner(app.owner());
            dialog.setTitle(revenue ? "Detail Pendapatan" : "Detail Okupansi & Tamu");
            dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
            dialog.getDialogPane().setPrefWidth(710);
            TableView<List<String>> table = new TableView<>();
            table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
            List<String> headings = revenue ? List.of("Reservasi", "Tamu", "Metode", "Bukti", "Nominal")
                    : List.of("Reservasi", "Tamu", "Kamar", "Aktivitas");
            for (int i = 0; i < headings.size(); i++) {
                int index = i;
                table.getColumns().add(column(headings.get(i), item -> item.get(index)));
            }
            List<List<String>> values = revenue ? day.transactions().stream().map(entry -> List.of(
                    entry.booking().code(), entry.booking().guest(), entry.receipt().method(), entry.receipt().reference(),
                    money(entry.receipt().amount()))).toList()
                    : day.activity().stream().map(entry -> List.of(entry.booking().code(), entry.booking().guest(),
                    entry.booking().room().number(), activity(entry))).toList();
            table.setItems(FXCollections.observableArrayList(values));
            table.setPrefHeight(Math.min(380, 65 + values.size() * 34));
            String summary = revenue ? "Total pendapatan " + money(day.total())
                    : day.occupied() + " kamar terisi  •  " + day.arrivals() + " check-in  •  " + day.departures() + " check-out";
            dialog.getDialogPane().setContent(card(label(DATE.format(day.date()), 21, FontWeight.BOLD, NAVY),
                    label(summary, 14, FontWeight.SEMI_BOLD, TEAL), table));
            dialog.showAndWait();
        }

        private void export(boolean asPdf) {
            String extension = asPdf ? ".pdf" : ".xlsx";
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Simpan laporan");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(asPdf ? "Dokumen PDF" : "Buku kerja Excel", "*" + extension));
            chooser.setInitialFileName("laporan-" + (revenue ? "pendapatan" : "okupansi") + "-" + filter.start() + extension);
            var file = chooser.showSaveDialog(app.owner());
            if (file == null) return;
            Path target = file.toPath();
            if (!target.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(extension)) {
                target = target.resolveSibling(target.getFileName() + extension);
            }
            try {
                ReportExport.Document document = document(asPdf);
                if (asPdf) ReportExport.pdf(target, document);
                else ReportExport.excel(target, document);
                showMessage("Laporan disimpan sebagai " + target.getFileName() + ".", false);
            } catch (IOException problem) {
                showMessage("Laporan tidak bisa disimpan. " + problem.getMessage(), true);
            }
        }

        private ReportExport.Document document(boolean asPdf) {
            List<String> headers = revenue ? List.of("Tanggal", "Uang muka", "Pembayaran", "Total pendapatan", "Transaksi")
                    : List.of("Tanggal", "Kamar terisi", "Kapasitas", "Okupansi", "Check-in", "Check-out");
            List<List<?>> output = new ArrayList<>();
            for (Day day : rows) {
                String date = DATE.format(day.date());
                output.add(revenue ? List.of(date, asPdf ? money(day.deposits()) : day.deposits(),
                        asPdf ? money(day.payments()) : day.payments(), asPdf ? money(day.total()) : day.total(), day.transactions().size())
                        : List.of(date, day.occupied(), day.capacity(), percentage(day.occupancy()), day.arrivals(), day.departures()));
            }
            return new ReportExport.Document(revenue ? "Laporan Pendapatan" : "Laporan Okupansi & Tamu", period(), headers, output);
        }

        private String period() {
            return DATE.format(filter.start()) + " – " + DATE.format(filter.end()) + "  •  " + filter.roomType();
        }

        private void showMessage(String value, boolean error) {
            message.setText(value);
            message.setTextFill(error ? RED : TEAL);
            message.setVisible(true);
        }
    }

    private static String activity(ReportData.Activity entry) {
        if (entry.checkIn() && entry.checkOut()) return "Check-in dan check-out";
        if (entry.checkOut()) return "Check-out";
        return entry.checkIn() ? "Check-in" : "Menginap";
    }

    private static VBox metric(String title, String value, String caption) {
        VBox card = card(label(title, 12, FontWeight.BOLD, MUTED), label(value, 23, FontWeight.BOLD, NAVY),
                label(caption, 12, FontWeight.NORMAL, MUTED));
        card.setMinWidth(0);
        card.setPrefWidth(210);
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private static VBox progress(String name, String value, double percentage) {
        Rectangle filled = new Rectangle(0, 8, TEAL);
        filled.setArcWidth(8);
        filled.setArcHeight(8);
        filled.setManaged(false);
        StackPane bar = new StackPane(filled);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setMinHeight(8);
        bar.setPrefHeight(8);
        bar.setBackground(fill(PALE, 4));
        filled.widthProperty().bind(bar.widthProperty().multiply(Math.clamp(percentage / 100, 0, 1)));
        return new VBox(5, row(label(name, 13, FontWeight.SEMI_BOLD, NAVY), spacer(),
                label(value, 12, FontWeight.BOLD, TEAL)), bar);
    }

    private static DatePicker datePicker(String id, LocalDate initial) {
        DatePicker picker = new DatePicker(initial);
        picker.setId(id);
        picker.setEditable(false);
        picker.setConverter(new StringConverter<>() {
            @Override public String toString(LocalDate value) { return value == null ? "" : DATE.format(value); }
            @Override public LocalDate fromString(String value) {
                return value == null || value.isBlank() ? null : LocalDate.parse(value, DATE);
            }
        });
        picker.setPrefWidth(155);
        picker.setMinHeight(36);
        picker.setBackground(fill(PALE, 8));
        return picker;
    }

    private static VBox field(String title, Node control) {
        return new VBox(5, label(title, 12, FontWeight.SEMI_BOLD, MUTED), control);
    }

    private static <T> TableColumn<T, String> column(String heading, Function<T, String> getter) {
        TableColumn<T, String> column = new TableColumn<>(heading);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(getter.apply(cell.getValue())));
        column.setSortable(false);
        return column;
    }

    private static Button button(String text, boolean primary, Runnable action) {
        Button button = new Button(text);
        button.setCursor(Cursor.HAND);
        button.setOnAction(event -> action.run());
        button.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 13));
        button.setTextFill(primary ? Color.WHITE : NAVY);
        button.setBackground(fill(primary ? TEAL : PALE, 8));
        button.setMinHeight(36);
        button.setPadding(new Insets(7, 12, 7, 12));
        return button;
    }

    private static Button identified(Button button, String id) {
        button.setId(id);
        return button;
    }

    private static VBox card(Node... children) {
        VBox box = new VBox(12, children);
        box.setPadding(new Insets(16));
        box.setBackground(fill(Color.WHITE, 12));
        return box;
    }

    private static HBox row(Node... children) {
        HBox row = new HBox(8, children);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static Region spacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    private static Label label(String value, double size, FontWeight weight, Color color) {
        Label label = new Label(value);
        label.setFont(Font.font("Segoe UI", weight, size));
        label.setTextFill(color);
        label.setWrapText(true);
        return label;
    }

    private static Background fill(Color color, double radius) {
        return new Background(new BackgroundFill(color, new CornerRadii(radius), Insets.EMPTY));
    }

    private static String money(long amount) { return "Rp " + NumberFormat.getIntegerInstance(INDONESIAN).format(amount); }
    private static String percentage(double value) { return decimal(value) + "%"; }
    private static String decimal(double value) {
        NumberFormat format = NumberFormat.getNumberInstance(INDONESIAN);
        format.setMaximumFractionDigits(1);
        return format.format(value);
    }
}
