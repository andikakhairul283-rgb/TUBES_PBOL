package com.example.tubespbolkel7.db;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/** Uji cepat: jalankan main() untuk memastikan database dan semua tabel terbentuk. */
public final class DatabaseCheck {
    public static void main(String[] args) throws Exception {
        Database database = new Database();
        database.init();
        System.out.println("File database: " + database.file());
        try (Connection connection = database.connect();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name")) {
            int count = 0;
            while (rows.next()) {
                System.out.println(" - " + rows.getString(1));
                count++;
            }
            System.out.println(count == 11 ? "OK: 11 tabel terbentuk." : "PERIKSA: tabel terbentuk " + count + ", seharusnya 11.");
        }
    }
}
