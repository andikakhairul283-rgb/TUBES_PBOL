package com.example.tubespbolkel7.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Menyimpan dan memuat seluruh data kamar sebagai satu "snapshot" (tipe, kamar, tugas, material, permintaan keycard).
 * Penyimpanan berjalan dalam satu transaksi: berhasil semua atau batal semua.
 */
public final class RoomRepository {
    public record TypeRow(String code, String label, long rate, int capacity, String facilities) { }
    public record RoomRow(String number, int floor, String typeCode, String condition, String notes, String customFacilities) { }
    public record JobRow(String code, String room, String condition, String priority, String description,
                         String assignee, String opened, String finished, int estimatedMinutes) { }
    public record MaterialRow(String job, String room, String name, int quantity, String requested) { }
    public record CardRow(String room, String identifier, String requested) { }
    public record Snapshot(List<TypeRow> types, List<RoomRow> rooms, List<JobRow> jobs,
                           List<MaterialRow> materials, List<CardRow> cards) { }

    private interface Mapper<T> { T map(ResultSet row) throws SQLException; }

    private final Database database;

    public RoomRepository(Database database) { this.database = database; }

    public Snapshot load() {
        try (Connection c = database.connect()) {
            return new Snapshot(
                query(c, "SELECT code, label, rate, capacity, facilities FROM room_types ORDER BY rowid",
                        r -> new TypeRow(r.getString(1), r.getString(2), r.getLong(3), r.getInt(4), r.getString(5))),
                query(c, "SELECT number, floor, type_code, condition, notes, custom_facilities FROM rooms ORDER BY rowid",
                        r -> new RoomRow(r.getString(1), r.getInt(2), r.getString(3), r.getString(4), r.getString(5), r.getString(6))),
                query(c, "SELECT code, room_number, condition, priority, description, assignee, opened, finished, estimated_minutes "
                                + "FROM maintenance_jobs ORDER BY code DESC",
                        r -> new JobRow(r.getString(1), r.getString(2), r.getString(3), r.getString(4), r.getString(5),
                                r.getString(6), r.getString(7), r.getString(8), r.getInt(9))),
                query(c, "SELECT job_code, room_number, name, quantity, requested FROM maintenance_materials ORDER BY id DESC",
                        r -> new MaterialRow(r.getString(1), r.getString(2), r.getString(3), r.getInt(4), r.getString(5))),
                query(c, "SELECT room_number, identifier, requested FROM card_requests ORDER BY rowid",
                        r -> new CardRow(r.getString(1), r.getString(2), r.getString(3))));
        } catch (SQLException failure) {
            throw new IllegalStateException("Gagal membaca data kamar dari database: " + failure.getMessage(), failure);
        }
    }

    /** Menyamakan isi database dengan snapshot. Daftar material harus berurutan dari yang terlama ke terbaru. */
    public void save(Snapshot snapshot) {
        try (Connection c = database.connect()) {
            c.setAutoCommit(false);
            try {
                run(c, "DELETE FROM maintenance_materials");
                run(c, "DELETE FROM card_requests");

                for (TypeRow t : snapshot.types()) {
                    update(c, """
                            INSERT INTO room_types (code, label, rate, capacity, facilities) VALUES (?, ?, ?, ?, ?)
                            ON CONFLICT(code) DO UPDATE SET label = excluded.label, rate = excluded.rate,
                                capacity = excluded.capacity, facilities = excluded.facilities""",
                            t.code(), t.label(), t.rate(), t.capacity(), t.facilities());
                }
                for (RoomRow room : snapshot.rooms()) {
                    update(c, """
                            INSERT INTO rooms (number, floor, type_code, condition, notes, custom_facilities) VALUES (?, ?, ?, ?, ?, ?)
                            ON CONFLICT(number) DO UPDATE SET floor = excluded.floor, type_code = excluded.type_code,
                                condition = excluded.condition, notes = excluded.notes,
                                custom_facilities = excluded.custom_facilities""",
                            room.number(), room.floor(), room.typeCode(), room.condition(), room.notes(), room.customFacilities());
                }
                for (JobRow job : snapshot.jobs()) {
                    update(c, """
                            INSERT INTO maintenance_jobs (code, room_number, condition, priority, description, assignee,
                                opened, finished, estimated_minutes) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                            ON CONFLICT(code) DO UPDATE SET room_number = excluded.room_number, condition = excluded.condition,
                                priority = excluded.priority, description = excluded.description, assignee = excluded.assignee,
                                opened = excluded.opened, finished = excluded.finished,
                                estimated_minutes = excluded.estimated_minutes""",
                            job.code(), job.room(), job.condition(), job.priority(), job.description(), job.assignee(),
                            job.opened(), job.finished(), job.estimatedMinutes());
                }

                prune(c, "maintenance_jobs", "code", keys(snapshot.jobs(), JobRow::code));
                prune(c, "rooms", "number", keys(snapshot.rooms(), RoomRow::number));
                prune(c, "room_types", "code", keys(snapshot.types(), TypeRow::code));

                for (MaterialRow m : snapshot.materials()) {
                    update(c, "INSERT INTO maintenance_materials (job_code, room_number, name, quantity, requested) VALUES (?, ?, ?, ?, ?)",
                            m.job(), m.room(), m.name(), m.quantity(), m.requested());
                }
                for (CardRow card : snapshot.cards()) {
                    update(c, "INSERT INTO card_requests (room_number, identifier, requested) VALUES (?, ?, ?)",
                            card.room(), card.identifier(), card.requested());
                }
                c.commit();
            } catch (SQLException | RuntimeException failure) {
                c.rollback();
                throw failure;
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Gagal menyimpan data kamar ke database: " + failure.getMessage(), failure);
        }
    }

    private static <T> List<T> query(Connection c, String sql, Mapper<T> mapper) throws SQLException {
        List<T> rows = new ArrayList<>();
        try (Statement statement = c.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            while (result.next()) rows.add(mapper.map(result));
        }
        return rows;
    }

    private static void run(Connection c, String sql) throws SQLException {
        try (Statement statement = c.createStatement()) { statement.execute(sql); }
    }

    private static void update(Connection c, String sql, Object... values) throws SQLException {
        try (PreparedStatement statement = c.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                if (values[i] == null) statement.setNull(i + 1, Types.NULL);
                else statement.setObject(i + 1, values[i]);
            }
            statement.executeUpdate();
        }
    }

    private static <T> Set<String> keys(List<T> rows, Function<T, String> key) {
        Set<String> keys = new HashSet<>();
        for (T row : rows) keys.add(key.apply(row));
        return keys;
    }

    /** Menghapus baris di tabel yang tidak ada lagi di snapshot. */
    private static void prune(Connection c, String table, String column, Set<String> keep) throws SQLException {
        List<String> existing = query(c, "SELECT " + column + " FROM " + table, r -> r.getString(1));
        try (PreparedStatement statement = c.prepareStatement("DELETE FROM " + table + " WHERE " + column + " = ?")) {
            for (String key : existing) {
                if (keep.contains(key)) continue;
                statement.setString(1, key);
                statement.executeUpdate();
            }
        }
    }
}
