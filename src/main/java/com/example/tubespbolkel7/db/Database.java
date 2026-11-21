package com.example.tubespbolkel7.db;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/** Pintu masuk ke database SQLite. Satu objek = satu file database. */
public final class Database {
    private static final Path DEFAULT_FILE = Path.of(System.getProperty("user.home"), ".checkin-id", "hotel.db");

    private static Database shared;

    private final Path file;
    private final String url;

    /** Database tunggal yang dipakai seluruh aplikasi. Tabel dibuat otomatis saat pertama dipanggil. */
    public static synchronized Database shared() {
        if (shared == null) {
            shared = new Database();
            shared.init();
        }
        return shared;
    }

    public Database() { this(DEFAULT_FILE); }

    public Database(Path file) {
        this.file = file.toAbsolutePath();
        this.url = "jdbc:sqlite:" + this.file;
    }

    public Path file() { return file; }

    /** Membuka koneksi baru. Wajib ditutup oleh pemanggil (pakai try-with-resources). */
    public Connection connect() throws SQLException {
        Connection connection = DriverManager.getConnection(url);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        }
        return connection;
    }

    /** Membuat folder dan seluruh tabel jika belum ada. Aman dipanggil berulang kali. */
    public void init() {
        try {
            Files.createDirectories(file.getParent());
            try (Connection connection = connect(); Statement statement = connection.createStatement()) {
                for (String sql : Schema.TABLES) statement.execute(sql);
            }
        } catch (IOException | SQLException failure) {
            throw new IllegalStateException("Gagal menyiapkan database: " + failure.getMessage(), failure);
        }
    }
}
