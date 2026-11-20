package com.example.tubespbolkel7.ui;

import com.example.tubespbolkel7.report.ReportExport;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.IOException;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;

final class PageComponents {
    static final int PAGE_SIZE = 8;
    static final Color NAVY = Color.web("#102A4B");
    static final Color TEAL = Color.web("#006A60");
    static final Color MUTED = Color.web("#596779");
    static final Color PALE = Color.web("#EDF2FE");
    static final Color RED = Color.web("#B42318");

    static VBox page(String title, String caption, Node... actions) {
        VBox heading = new VBox(5, label(title, 28, FontWeight.BOLD, NAVY), caption(caption));
        HBox.setHgrow(heading, Priority.ALWAYS);
        FlowPane toolbar = new FlowPane(8, 6, actions);
        toolbar.setAlignment(Pos.CENTER_RIGHT);
        toolbar.setMaxWidth(Region.USE_PREF_SIZE);
        HBox header = actions.length == 0 ? row(heading) : row(heading, toolbar);
        VBox root = new VBox(17, header);
        root.setPadding(new Insets(25));
        root.setMinHeight(Region.USE_PREF_SIZE);
        root.setBackground(fill(Color.web("#F8F9FF"), 0));
        return root;
    }

    static HBox row(Node... items) {
        HBox row = new HBox(9, items);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    static Region spacer() {
        Region empty = new Region();
        HBox.setHgrow(empty, Priority.ALWAYS);
        return empty;
    }

    static VBox card(Node... items) {
        VBox box = new VBox(12, items);
        box.setPadding(new Insets(17));
        box.setBackground(fill(Color.WHITE, 12));
        return box;
    }

    static Label title(String value) { return label(value, 18, FontWeight.BOLD, NAVY); }
    static Label caption(String value) { return label(value, 13, FontWeight.NORMAL, MUTED); }

    static Label label(String value, double size, FontWeight weight, Color color) {
        Label text = new Label(value);
        text.setFont(Font.font("Segoe UI", weight, size));
        text.setTextFill(color);
        text.setWrapText(true);
        return text;
    }

    static Background fill(Color color, double radius) {
        return new Background(new BackgroundFill(color, new CornerRadii(radius), Insets.EMPTY));
    }

    static Button button(String id, String label, boolean primary, Runnable action) {
        Button button = new Button(label);
        button.setId(id);
        button.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 13));
        button.setTextFill(primary ? Color.WHITE : NAVY);
        button.setBackground(fill(primary ? TEAL : PALE, 8));
        button.setPadding(new Insets(8, 12, 8, 12));
        button.setCursor(Cursor.HAND);
        button.setOnAction(event -> action.run());
        return button;
    }

    static TextField text(String id, String initial, String prompt) {
        TextField input = new TextField(initial);
        input.setId(id);
        input.setPromptText(prompt);
        input.setMinHeight(36);
        input.setBackground(fill(PALE, 8));
        return input;
    }

    static <T> ComboBox<T> select(String id, List<T> items, T value) {
        ComboBox<T> box = new ComboBox<>();
        box.setId(id);
        box.getItems().setAll(items);
        box.setValue(value);
        box.setMinHeight(36);
        box.setBackground(fill(PALE, 8));
        return box;
    }

    static VBox field(String caption, Node control) {
        if (control instanceof Region region) region.setMaxWidth(Double.MAX_VALUE);
        return new VBox(5, label(caption, 12, FontWeight.SEMI_BOLD, MUTED), control);
    }

    static <T> TableView<T> table(String id, String empty) {
        TableView<T> table = new TableView<>();
        table.setId(id);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setFixedCellSize(48);
        table.setPlaceholder(caption(empty));
        return table;
    }

    static void sizeTable(TableView<?> table, int count) {
        table.setPrefHeight(34 + Math.clamp(count, 2, 9) * 48);
        table.setMinHeight(Region.USE_PREF_SIZE);
    }

    static HBox pageControls(String id, int index, int count, IntConsumer select) {
        int pages = Math.max(1, (count + PAGE_SIZE - 1) / PAGE_SIZE);
        int first = count == 0 ? 0 : index * PAGE_SIZE + 1;
        Button previous = button(id + "-previous", "‹ Sebelumnya", false, () -> select.accept(index - 1));
        Button next = button(id + "-next", "Selanjutnya ›", false, () -> select.accept(index + 1));
        previous.setDisable(index == 0);
        next.setDisable(index == pages - 1);
        HBox controls = row(caption(first + "–" + Math.min((index + 1) * PAGE_SIZE, count) + " dari " + count),
                spacer(), previous);
        for (int i = Math.max(0, index - 1); i < Math.min(index + 2, pages); i++) {
            int destination = i;
            controls.getChildren().add(button(id + "-page-" + (i + 1), Integer.toString(i + 1), i == index,
                    () -> select.accept(destination)));
        }
        controls.getChildren().add(next);
        return controls;
    }

    static <T> TableColumn<T, String> column(String title, Function<T, String> value) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        return column;
    }

    static <T> TableColumn<T, T> actions(Function<T, Node> graphic) {
        TableColumn<T, T> column = new TableColumn<>("Aksi");
        column.setSortable(false);
        column.setMinWidth(138);
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        column.setCellFactory(unused -> new TableCell<>() {
            @Override protected void updateItem(T value, boolean empty) {
                super.updateItem(value, empty);
                setGraphic(empty || value == null ? null : graphic.apply(value));
            }
        });
        return column;
    }

    static Dialog<ButtonType> dialog(Window window, String title, Node body) {
        Dialog<ButtonType> dialog = new Dialog<>();
        if (window != null) dialog.initOwner(window);
        dialog.setTitle(title);
        dialog.getDialogPane().setPrefWidth(590);
        dialog.getDialogPane().setContent(body);
        return dialog;
    }

    static void detail(Window window, String title, Node body) {
        Dialog<ButtonType> dialog = dialog(window, title, body);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    static void edit(Window owner, String title, VBox body, String submit, Runnable save, Runnable afterSave) {
        Label error = label("", 13, FontWeight.NORMAL, RED);
        error.setVisible(false);
        error.managedProperty().bind(error.visibleProperty());
        body.getChildren().add(error);
        Dialog<ButtonType> dialog = dialog(owner, title, body);
        ButtonType confirm = new ButtonType(submit, ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, confirm);
        dialog.getDialogPane().lookupButton(confirm).addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            try {
                save.run();
            } catch (IllegalArgumentException invalid) {
                error.setText(invalid.getMessage());
                error.setVisible(true);
                event.consume();
            }
        });
        dialog.showAndWait().filter(confirm::equals).ifPresent(result -> afterSave.run());
    }

    static void export(Window owner, String name, boolean pdf, List<String> headers, List<List<?>> rows, Consumer<String> feedback) {
        String extension = pdf ? ".pdf" : ".xlsx";
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Simpan " + name);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(pdf ? "Dokumen PDF" : "Buku kerja Excel", "*" + extension));
        chooser.setInitialFileName(name.toLowerCase(Locale.ROOT).replace(' ', '-') + extension);
        var chosen = chooser.showSaveDialog(owner);
        if (chosen == null) return;
        Path path = chosen.toPath();
        if (!path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(extension)) {
            path = path.resolveSibling(path.getFileName() + extension);
        }
        try {
            ReportExport.Document document = new ReportExport.Document(name, "CheckIn.id", headers, rows);
            if (pdf) ReportExport.pdf(path, document);
            else ReportExport.excel(path, document);
            feedback.accept("Berkas disimpan sebagai " + path.getFileName() + ".");
        } catch (IOException problem) {
            feedback.accept("Berkas gagal disimpan. " + problem.getMessage());
        }
    }

    static String money(long value) {
        return "Rp " + NumberFormat.getIntegerInstance(Locale.forLanguageTag("id-ID")).format(value);
    }
}
