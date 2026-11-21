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

/** Fungsi bantu JDBC yang dipakai bersama oleh repository. */
final class Sql {
    private Sql() { }

    interface Mapper<T> { T map(ResultSet row) throws SQLException; }

    static <T> List<T> query(Connection c, String sql, Mapper<T> mapper) throws SQLException {
        List<T> rows = new ArrayList<>();
        try (Statement statement = c.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            while (result.next()) rows.add(mapper.map(result));
        }
        return rows;
    }

    static void run(Connection c, String sql) throws SQLException {
        try (Statement statement = c.createStatement()) { statement.execute(sql); }
    }

    static void update(Connection c, String sql, Object... values) throws SQLException {
        try (PreparedStatement statement = c.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                if (values[i] == null) statement.setNull(i + 1, Types.NULL);
                else statement.setObject(i + 1, values[i]);
            }
            statement.executeUpdate();
        }
    }

    static <T> Set<String> keys(List<T> rows, Function<T, String> key) {
        Set<String> keys = new HashSet<>();
        for (T row : rows) keys.add(key.apply(row));
        return keys;
    }

    /** Menghapus baris di tabel yang tidak ada lagi dalam daftar kunci yang dipertahankan. */
    static void prune(Connection c, String table, String column, Set<String> keep) throws SQLException {
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
