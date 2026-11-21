package com.example.tubespbolkel7.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Akses tabel maintenance_stock. */
public final class StockRepository {
    public record Row(long id, String name, String unit, String location, int quantity, int minimum) { }

    private final Database database;

    public StockRepository(Database database) { this.database = database; }

    public List<Row> loadAll() {
        List<Row> rows = new ArrayList<>();
        try (Connection connection = database.connect();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT id, name, unit, location, quantity, minimum FROM maintenance_stock ORDER BY id")) {
            while (result.next()) {
                rows.add(new Row(result.getLong(1), result.getString(2), result.getString(3), result.getString(4),
                        result.getInt(5), result.getInt(6)));
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Gagal membaca stok dari database: " + failure.getMessage(), failure);
        }
        return rows;
    }

    public void save(Row row) {
        String sql = """
                INSERT INTO maintenance_stock (id, name, unit, location, quantity, minimum) VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET name = excluded.name, unit = excluded.unit, location = excluded.location,
                    quantity = excluded.quantity, minimum = excluded.minimum""";
        try (Connection connection = database.connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, row.id());
            statement.setString(2, row.name());
            statement.setString(3, row.unit());
            statement.setString(4, row.location());
            statement.setInt(5, row.quantity());
            statement.setInt(6, row.minimum());
            statement.executeUpdate();
        } catch (SQLException failure) {
            throw new IllegalStateException("Gagal menyimpan stok ke database: " + failure.getMessage(), failure);
        }
    }
}
