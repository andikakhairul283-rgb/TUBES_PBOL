package com.example.tubespbolkel7.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Akses tabel users. Tidak berisi aturan bisnis; aturan tetap di UserAccounts. */
public final class UserRepository {
    public record Row(String username, String name, String role, boolean enabled, byte[] salt, byte[] hash) { }

    private final Database database;

    public UserRepository(Database database) { this.database = database; }

    public List<Row> loadAll() {
        List<Row> rows = new ArrayList<>();
        String sql = "SELECT username, name, role, enabled, salt, hash FROM users ORDER BY rowid";
        try (Connection connection = database.connect();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            while (result.next()) {
                rows.add(new Row(result.getString(1), result.getString(2), result.getString(3),
                        result.getInt(4) == 1, result.getBytes(5), result.getBytes(6)));
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Gagal membaca akun dari database: " + failure.getMessage(), failure);
        }
        return rows;
    }

    /** Menambah akun baru, atau memperbarui bila username sudah ada. */
    public void save(Row row) {
        String sql = """
                INSERT INTO users (username, name, role, enabled, salt, hash) VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(username) DO UPDATE SET
                    name = excluded.name, role = excluded.role, enabled = excluded.enabled,
                    salt = excluded.salt, hash = excluded.hash""";
        try (Connection connection = database.connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, row.username());
            statement.setString(2, row.name());
            statement.setString(3, row.role());
            statement.setInt(4, row.enabled() ? 1 : 0);
            statement.setBytes(5, row.salt());
            statement.setBytes(6, row.hash());
            statement.executeUpdate();
        } catch (SQLException failure) {
            throw new IllegalStateException("Gagal menyimpan akun ke database: " + failure.getMessage(), failure);
        }
    }
}
