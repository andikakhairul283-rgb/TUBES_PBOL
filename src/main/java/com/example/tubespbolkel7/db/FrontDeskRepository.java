package com.example.tubespbolkel7.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/** Akses tabel front_desk_notes (pesan Maintenance ke resepsionis). Pesan hanya bertambah, tidak diubah. */
public final class FrontDeskRepository {
    public record Row(String author, String room, String message, String created) { }

    private final Database database;

    public FrontDeskRepository(Database database) { this.database = database; }

    public List<Row> loadNewestFirst() {
        try (Connection c = database.connect()) {
            return Sql.query(c, "SELECT author, room, message, created FROM front_desk_notes ORDER BY id DESC",
                    r -> new Row(r.getString(1), r.getString(2), r.getString(3), r.getString(4)));
        } catch (SQLException failure) {
            throw new IllegalStateException("Gagal membaca pesan dari database: " + failure.getMessage(), failure);
        }
    }

    public void add(Row row) {
        try (Connection c = database.connect()) {
            Sql.update(c, "INSERT INTO front_desk_notes (author, room, message, created) VALUES (?, ?, ?, ?)",
                    row.author(), row.room(), row.message(), row.created());
        } catch (SQLException failure) {
            throw new IllegalStateException("Gagal menyimpan pesan ke database: " + failure.getMessage(), failure);
        }
    }
}
