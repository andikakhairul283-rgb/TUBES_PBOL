package com.example.tubespbolkel7.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Menyimpan dan memuat katalog layanan dan pesanan layanan sebagai satu snapshot.
 * Setiap pesanan menyimpan salinan data layanan saat dipesan, jadi perubahan tarif tidak mengubah pesanan lama.
 */
public final class ServiceRepository {
    public record ItemRow(String name, String category, String unit, long price, boolean available) { }
    public record OrderRow(String code, String created, String reservation, String room, String guest,
                           ItemRow item, int quantity, String status, String notes) { }
    public record Snapshot(List<ItemRow> catalog, List<OrderRow> orders) { }

    private final Database database;

    public ServiceRepository(Database database) { this.database = database; }

    /** Katalog dimuat sesuai urutan input, pesanan dari yang terbaru. */
    public Snapshot load() {
        try (Connection c = database.connect()) {
            return new Snapshot(
                Sql.query(c, "SELECT name, category, unit, price, available FROM service_catalog ORDER BY id",
                        r -> new ItemRow(r.getString(1), r.getString(2), r.getString(3), r.getLong(4), r.getInt(5) == 1)),
                Sql.query(c, "SELECT code, created, reservation_code, room_number, guest_name, item_name, item_category, "
                                + "item_unit, item_price, item_available, quantity, status, notes FROM service_orders "
                                + "ORDER BY CAST(substr(code, 5) AS INTEGER) DESC",
                        r -> new OrderRow(r.getString(1), r.getString(2), r.getString(3), r.getString(4), r.getString(5),
                                new ItemRow(r.getString(6), r.getString(7), r.getString(8), r.getLong(9), r.getInt(10) == 1),
                                r.getInt(11), r.getString(12), r.getString(13))));
        } catch (SQLException failure) {
            throw new IllegalStateException("Gagal membaca layanan dari database: " + failure.getMessage(), failure);
        }
    }

    public void save(Snapshot snapshot) {
        try (Connection c = database.connect()) {
            c.setAutoCommit(false);
            try {
                Sql.run(c, "DELETE FROM service_catalog");
                for (ItemRow item : snapshot.catalog()) {
                    Sql.update(c, "INSERT INTO service_catalog (name, category, unit, price, available) VALUES (?, ?, ?, ?, ?)",
                            item.name(), item.category(), item.unit(), item.price(), item.available() ? 1 : 0);
                }
                for (OrderRow o : snapshot.orders()) {
                    Sql.update(c, """
                            INSERT INTO service_orders (code, created, reservation_code, room_number, guest_name, item_name,
                                item_category, item_unit, item_price, item_available, quantity, status, notes)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            ON CONFLICT(code) DO UPDATE SET created = excluded.created,
                                reservation_code = excluded.reservation_code, room_number = excluded.room_number,
                                guest_name = excluded.guest_name, item_name = excluded.item_name,
                                item_category = excluded.item_category, item_unit = excluded.item_unit,
                                item_price = excluded.item_price, item_available = excluded.item_available,
                                quantity = excluded.quantity, status = excluded.status, notes = excluded.notes""",
                            o.code(), o.created(), o.reservation(), o.room(), o.guest(), o.item().name(),
                            o.item().category(), o.item().unit(), o.item().price(), o.item().available() ? 1 : 0,
                            o.quantity(), o.status(), o.notes());
                }
                Sql.prune(c, "service_orders", "code", Sql.keys(snapshot.orders(), OrderRow::code));
                c.commit();
            } catch (SQLException | RuntimeException failure) {
                c.rollback();
                throw failure;
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Gagal menyimpan layanan ke database: " + failure.getMessage(), failure);
        }
    }
}
