package com.example.tubespbolkel7.ui;

import com.example.tubespbolkel7.model.Role;
import com.example.tubespbolkel7.model.UserAccounts;

import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static com.example.tubespbolkel7.model.UserAccounts.Account;
import static com.example.tubespbolkel7.ui.PageComponents.*;

final class OwnerAccounts {
    static VBox view(HotelOperations app, VBox loginHistory) {
        UserAccounts accounts = app.data().settings().accounts();
        TextField search = text("owner-user-search", "", "Cari nama atau username...");
        String all = "Semua peran";
        List<String> roles = new java.util.ArrayList<>(List.of(all));
        Arrays.stream(Role.values()).map(Role::toString).forEach(roles::add);
        ComboBox<String> role = select("owner-user-role", roles, all);
        ComboBox<String> status = select("owner-user-status", List.of("Semua status", "Aktif", "Nonaktif"), "Semua status");
        TableView<Account> table = table("owner-accounts-table", "Belum ada akun terdaftar untuk filter ini.");
        Runnable refresh = () -> {
            String query = search.getText().strip().toLowerCase(Locale.ROOT);
            List<Account> visible = accounts.all().stream()
                    .filter(account -> all.equals(role.getValue()) || account.role().label().equals(role.getValue()))
                    .filter(account -> status.getValue().equals("Semua status") || account.enabled() == status.getValue().equals("Aktif"))
                    .filter(account -> (account.username() + " " + account.name()).toLowerCase(Locale.ROOT).contains(query)).toList();
            table.getItems().setAll(visible);
            sizeTable(table, visible.size());
        };
        table.getColumns().addAll(List.of(column("Nama tampilan", Account::name), column("Username", Account::username),
                column("Peran", account -> account.role().label()), column("Status", account -> account.enabled() ? "Aktif" : "Nonaktif"),
                actions(account -> row(button("owner-user-edit-" + account.username(), "Ubah", false,
                                () -> editAccount(app, account, refresh)),
                        button("owner-user-reset-" + account.username(), "Reset Sandi", false, () -> reset(app, account))))));
        table.getColumns().getLast().setMinWidth(183);
        HBox.setHgrow(search, Priority.ALWAYS);
        search.textProperty().addListener((obs, old, value) -> refresh.run());
        role.valueProperty().addListener((obs, old, value) -> refresh.run());
        status.valueProperty().addListener((obs, old, value) -> refresh.run());
        refresh.run();
        VBox registered = card(row(title("Pengguna & Peran"), spacer(),
                button("owner-user-add", "+ Tambah Pengguna", true, () -> editAccount(app, null, refresh))),
                caption("Kelola akun yang terdaftar di sesi aplikasi ini."), row(search, role, status), table);
        return new VBox(16, registered, loginHistory);
    }

    private static void editAccount(HotelOperations app, Account account, Runnable refresh) {
        boolean create = account == null;
        TextField username = text("owner-account-username", create ? "" : account.username(), "Username");
        username.setEditable(create);
        TextField name = text("owner-account-name", create ? "" : account.name(), "Nama lengkap");
        ComboBox<Role> role = select("owner-account-role", List.of(Role.values()), create ? Role.STAFF : account.role());
        CheckBox enabled = new CheckBox("Akun aktif");
        enabled.setId("owner-account-enabled");
        enabled.setSelected(create || account.enabled());
        PasswordField password = password("owner-account-password");
        VBox body = card(field("Username", username), field("Nama tampilan", name), field("Peran", role), enabled);
        if (create) body.getChildren().add(field("Kata sandi awal", password));
        UserAccounts accounts = app.data().settings().accounts();
        edit(app.owner(), create ? "Tambah Pengguna" : "Ubah Pengguna", body, "Simpan", () -> {
            if (create) accounts.create(username.getText().strip(), name.getText().strip(), role.getValue(),
                    enabled.isSelected(), password.getText());
            else accounts.update(account.username(), name.getText().strip(), role.getValue(), enabled.isSelected(), app.username());
        }, refresh);
    }

    private static void reset(HotelOperations app, Account account) {
        PasswordField next = password("owner-account-new-password");
        PasswordField repeat = password("owner-account-confirm-password");
        edit(app.owner(), "Reset Sandi", card(title(account.username()), field("Kata sandi baru", next),
                field("Ulangi kata sandi", repeat)), "Reset sandi", () -> {
            checkConfirmation(next, repeat);
            app.data().settings().accounts().resetPassword(account.username(), next.getText());
        }, () -> { });
    }

    static void changePassword(HotelOperations app, Runnable done) {
        PasswordField old = password("owner-old-password");
        PasswordField next = password("owner-new-password");
        PasswordField repeat = password("owner-confirm-password");
        edit(app.owner(), "Ubah Kata Sandi", card(field("Kata sandi lama", old), field("Kata sandi baru", next),
                field("Ulangi kata sandi", repeat)), "Simpan", () -> {
            checkConfirmation(next, repeat);
            app.data().settings().accounts().changePassword(app.username(), old.getText(), next.getText());
        }, done);
    }

    private static PasswordField password(String id) {
        PasswordField field = new PasswordField();
        field.setId(id);
        field.setPromptText("8–128 karakter");
        field.setMinHeight(36);
        field.setBackground(fill(PALE, 8));
        return field;
    }

    private static void checkConfirmation(PasswordField next, PasswordField repeat) {
        if (!next.getText().equals(repeat.getText())) throw new IllegalArgumentException("Konfirmasi kata sandi tidak cocok.");
    }
}
