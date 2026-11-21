package com.example.tubespbolkel7.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/** Akses tabel guests. Nomor identitas kosong disimpan sebagai NULL agar tidak bentrok dengan aturan UNIQUE. */
public final class GuestRepository {
    public record Row(String id, String name, String phone, String email, String identity, boolean vip, String notes) { }

    private final Database database;

    public GuestRepository(Database database) { this.database = database; }

    public List<Row> loadAll() {
        try (Connection c = database.connect()) {
            return Sql.query(c, "SELECT id, name, phone, email, identity, vip, notes FROM guests ORDER BY rowid",
                    r -> new Row(r.getString(1), r.getString(2), r.getString(3), r.getString(4),
                            r.getString(5) == null ? "" : r.getString(5), r.getInt(6) == 1, r.getString(7)));
        } catch (SQLException failure) {
            throw new IllegalStateException("Gagal membaca data tamu dari database: " + failure.getMessage(), failure);
        }
    }

    public void save(Row row) {
        try (Connection c = database.connect()) {
            Sql.update(c, """
                    INSERT INTO guests (id, name, phone, email, identity, vip, notes) VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(id) DO UPDATE SET name = excluded.name, phone = excluded.phone, email = excluded.email,
                        identity = excluded.identity, vip = excluded.vip, notes = excluded.notes""",
                    row.id(), row.name(), row.phone(), row.email(), row.identity().isBlank() ? null : row.identity(),
                    row.vip() ? 1 : 0, row.notes());
        } catch (SQLException failure) {
            throw new IllegalStateException("Gagal menyimpan data tamu ke database: " + failure.getMessage(), failure);
        }
    }
}
