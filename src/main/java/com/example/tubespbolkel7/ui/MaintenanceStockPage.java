package com.example.tubespbolkel7.ui;

import com.example.tubespbolkel7.model.MaintenanceStock;

import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Locale;

import static com.example.tubespbolkel7.model.MaintenanceStock.Item;
import static com.example.tubespbolkel7.ui.PageComponents.*;

final class MaintenanceStockPage {
    static void show(HotelOperations app, Runnable updated) {
        MaintenanceStock stock = app.data().inventory().stock();
        TextField search = text("stock-search", "", "Cari material atau lokasi...");
        String all = "Semua status";
        ComboBox<String> status = select("stock-status", List.of(all, "Tersedia", "Stok rendah", "Habis"), all);
        TableView<Item> table = table("maintenance-stock-table", "Belum ada material untuk filter ini.");
        Runnable refresh = () -> {
            String key = search.getText().strip().toLowerCase(Locale.ROOT);
            List<Item> rows = stock.items().stream()
                    .filter(item -> all.equals(status.getValue()) || item.status().equals(status.getValue()))
                    .filter(item -> (item.name() + " " + item.location()).toLowerCase(Locale.ROOT).contains(key)).toList();
            table.getItems().setAll(rows);
            sizeTable(table, Math.min(6, rows.size()));
        };
        Runnable afterSave = () -> {
            refresh.run();
            updated.run();
        };
        table.getColumns().addAll(List.of(column("Material", Item::name), column("Lokasi", Item::location),
                column("Stok", item -> item.quantity() + " " + item.unit()), column("Status", Item::status),
                actions(item -> button("stock-edit-" + item.id(), "Ubah Stok", false, () -> form(app, item, afterSave)))));
        search.textProperty().addListener((obs, old, value) -> refresh.run());
        status.valueProperty().addListener((obs, old, value) -> refresh.run());
        HBox.setHgrow(search, Priority.ALWAYS);
        Button add = button("stock-add", "+ Tambah Material", true, () -> form(app, null, afterSave));
        VBox body = card(row(title("Log Peralatan & Sparepart"), spacer(), add), row(search, status), table);
        var dialog = dialog(app.owner(), "Stok Material", body);
        dialog.getDialogPane().setPrefWidth(680);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        refresh.run();
        dialog.showAndWait();
    }

    private static void form(HotelOperations app, Item original, Runnable refresh) {
        TextField name = text("stock-name", original == null ? "" : original.name(), "Nama material");
        TextField unit = text("stock-unit", original == null ? "" : original.unit(), "Contoh: buah, set, liter");
        TextField location = text("stock-location", original == null ? "" : original.location(), "Lokasi penyimpanan");
        TextField quantity = text("stock-quantity", original == null ? "0" : Integer.toString(original.quantity()), "Jumlah fisik");
        TextField minimum = text("stock-minimum", original == null ? "0" : Integer.toString(original.minimum()), "Batas peringatan");
        edit(app.owner(), original == null ? "Tambah Material" : "Ubah Stok Material",
                card(field("Nama", name), field("Satuan", unit), field("Lokasi", location), field("Jumlah stok", quantity),
                        field("Batas stok rendah", minimum)), "Simpan material",
                () -> app.data().inventory().stock().save(original, name.getText().strip(), unit.getText().strip(),
                        location.getText().strip(), number(quantity.getText()), number(minimum.getText())), refresh);
    }

    private static int number(String input) {
        String value = input.strip();
        if (!value.matches("[0-9]{1,6}")) throw new IllegalArgumentException("Isi jumlah antara 0 dan 999.999.");
        return Integer.parseInt(value);
    }
}
