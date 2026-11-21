package com.example.tubespbolkel7.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Menyimpan dan memuat reservasi beserta pembayaran dan jaminannya sebagai satu snapshot.
 * Penyimpanan berjalan dalam satu transaksi: berhasil semua atau batal semua.
 */
public final class BookingRepository {
    public record ReservationRow(String code, String guestId, String roomNumber, String roomType, long roomRate,
                                 String arrival, String departure, String status, long deposit, String notes,
                                 String keycard, String checkedInOn, String checkedOutOn, String pricing) { }
    public record ReceiptRow(String code, String date, long amount, String method, String reference, boolean deposit) { }
    public record DepositRow(String code, long amount, boolean returned) { }
    public record Snapshot(List<ReservationRow> reservations, List<ReceiptRow> receipts, List<DepositRow> deposits) { }

    private final Database database;

    public BookingRepository(Database database) { this.database = database; }

    /** Reservasi dimuat dari yang terbaru ke terlama. */
    public Snapshot load() {
        try (Connection c = database.connect()) {
            return new Snapshot(
                Sql.query(c, "SELECT code, guest_id, room_number, room_type, room_rate, arrival, departure, status, deposit, "
                                + "notes, keycard, checked_in_on, checked_out_on, pricing FROM reservations ORDER BY code DESC",
                        r -> new ReservationRow(r.getString(1), r.getString(2), r.getString(3), r.getString(4), r.getLong(5),
                                r.getString(6), r.getString(7), r.getString(8), r.getLong(9), r.getString(10), r.getString(11),
                                r.getString(12), r.getString(13), r.getString(14))),
                Sql.query(c, "SELECT reservation_code, date, amount, method, reference, is_deposit FROM receipts ORDER BY id",
                        r -> new ReceiptRow(r.getString(1), r.getString(2), r.getLong(3), r.getString(4), r.getString(5),
                                r.getInt(6) == 1)),
                Sql.query(c, "SELECT reservation_code, amount, returned FROM security_deposits",
                        r -> new DepositRow(r.getString(1), r.getLong(2), r.getInt(3) == 1)));
        } catch (SQLException failure) {
            throw new IllegalStateException("Gagal membaca reservasi dari database: " + failure.getMessage(), failure);
        }
    }

    /** Menyamakan isi database dengan snapshot. Daftar pembayaran harus berurutan dari yang terlama. */
    public void save(Snapshot snapshot) {
        try (Connection c = database.connect()) {
            c.setAutoCommit(false);
            try {
                Sql.run(c, "DELETE FROM receipts");
                Sql.run(c, "DELETE FROM security_deposits");

                for (ReservationRow r : snapshot.reservations()) {
                    Sql.update(c, """
                            INSERT INTO reservations (code, guest_id, room_number, room_type, room_rate, arrival, departure,
                                status, deposit, notes, keycard, checked_in_on, checked_out_on, pricing)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            ON CONFLICT(code) DO UPDATE SET guest_id = excluded.guest_id, room_number = excluded.room_number,
                                room_type = excluded.room_type, room_rate = excluded.room_rate, arrival = excluded.arrival,
                                departure = excluded.departure, status = excluded.status, deposit = excluded.deposit,
                                notes = excluded.notes, keycard = excluded.keycard, checked_in_on = excluded.checked_in_on,
                                checked_out_on = excluded.checked_out_on, pricing = excluded.pricing""",
                            r.code(), r.guestId(), r.roomNumber(), r.roomType(), r.roomRate(), r.arrival(), r.departure(),
                            r.status(), r.deposit(), r.notes(), r.keycard(), r.checkedInOn(), r.checkedOutOn(), r.pricing());
                }
                Sql.prune(c, "reservations", "code", Sql.keys(snapshot.reservations(), ReservationRow::code));

                for (ReceiptRow r : snapshot.receipts()) {
                    Sql.update(c, "INSERT INTO receipts (reservation_code, date, amount, method, reference, is_deposit) VALUES (?, ?, ?, ?, ?, ?)",
                            r.code(), r.date(), r.amount(), r.method(), r.reference(), r.deposit() ? 1 : 0);
                }
                for (DepositRow d : snapshot.deposits()) {
                    Sql.update(c, "INSERT INTO security_deposits (reservation_code, amount, returned) VALUES (?, ?, ?)",
                            d.code(), d.amount(), d.returned() ? 1 : 0);
                }
                c.commit();
            } catch (SQLException | RuntimeException failure) {
                c.rollback();
                throw failure;
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Gagal menyimpan reservasi ke database: " + failure.getMessage(), failure);
        }
    }
}
