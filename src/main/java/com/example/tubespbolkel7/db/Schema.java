package com.example.tubespbolkel7.db;

import java.util.List;

/** Skema tabel SQLite. Tanggal disimpan sebagai teks ISO (yyyy-MM-dd), uang sebagai bilangan bulat (rupiah). */
final class Schema {
    private Schema() { }

    static final List<String> TABLES = List.of(
        """
        CREATE TABLE IF NOT EXISTS users (
            username TEXT PRIMARY KEY COLLATE NOCASE,
            name     TEXT NOT NULL,
            role     TEXT NOT NULL CHECK (role IN ('STAFF','OWNER','MAINTENANCE')),
            enabled  INTEGER NOT NULL DEFAULT 1,
            salt     BLOB NOT NULL,
            hash     BLOB NOT NULL
        )""",
        """
        CREATE TABLE IF NOT EXISTS room_types (
            code       TEXT PRIMARY KEY,
            label      TEXT NOT NULL UNIQUE,
            rate       INTEGER NOT NULL CHECK (rate >= 0),
            capacity   INTEGER NOT NULL CHECK (capacity > 0),
            facilities TEXT NOT NULL DEFAULT ''
        )""",
        """
        CREATE TABLE IF NOT EXISTS rooms (
            number            TEXT PRIMARY KEY,
            floor             INTEGER NOT NULL,
            type_code         TEXT NOT NULL REFERENCES room_types(code),
            condition         TEXT NOT NULL CHECK (condition IN ('READY','CLEANING','MAINTENANCE')),
            notes             TEXT NOT NULL DEFAULT '',
            custom_facilities TEXT NOT NULL DEFAULT ''
        )""",
        """
        CREATE TABLE IF NOT EXISTS maintenance_jobs (
            code              TEXT PRIMARY KEY,
            room_number       TEXT NOT NULL REFERENCES rooms(number),
            condition         TEXT NOT NULL,
            priority          TEXT NOT NULL CHECK (priority IN ('NORMAL','HIGH','URGENT')),
            description       TEXT NOT NULL DEFAULT '',
            assignee          TEXT NOT NULL DEFAULT '',
            opened            TEXT NOT NULL,
            finished          TEXT,
            estimated_minutes INTEGER NOT NULL DEFAULT 0
        )""",
        """
        CREATE TABLE IF NOT EXISTS maintenance_materials (
            id          INTEGER PRIMARY KEY AUTOINCREMENT,
            job_code    TEXT NOT NULL REFERENCES maintenance_jobs(code),
            room_number TEXT NOT NULL,
            name        TEXT NOT NULL,
            quantity    INTEGER NOT NULL CHECK (quantity > 0),
            requested   TEXT NOT NULL
        )""",
        """
        CREATE TABLE IF NOT EXISTS card_requests (
            room_number TEXT PRIMARY KEY REFERENCES rooms(number),
            identifier  TEXT NOT NULL,
            requested   TEXT NOT NULL
        )""",
        """
        CREATE TABLE IF NOT EXISTS maintenance_stock (
            id       INTEGER PRIMARY KEY AUTOINCREMENT,
            name     TEXT NOT NULL UNIQUE COLLATE NOCASE,
            unit     TEXT NOT NULL,
            location TEXT NOT NULL DEFAULT '',
            quantity INTEGER NOT NULL CHECK (quantity >= 0),
            minimum  INTEGER NOT NULL CHECK (minimum >= 0)
        )""",
        """
        CREATE TABLE IF NOT EXISTS guests (
            id       TEXT PRIMARY KEY,
            name     TEXT NOT NULL,
            phone    TEXT NOT NULL DEFAULT '',
            email    TEXT NOT NULL DEFAULT '',
            identity TEXT UNIQUE COLLATE NOCASE,
            vip      INTEGER NOT NULL DEFAULT 0,
            notes    TEXT NOT NULL DEFAULT ''
        )""",
        """
        CREATE TABLE IF NOT EXISTS reservations (
            code           TEXT PRIMARY KEY,
            guest_id       TEXT NOT NULL REFERENCES guests(id),
            room_number    TEXT NOT NULL REFERENCES rooms(number),
            room_type      TEXT NOT NULL,
            room_rate      INTEGER NOT NULL,
            arrival        TEXT NOT NULL,
            departure      TEXT NOT NULL,
            status         TEXT NOT NULL CHECK (status IN ('BOOKED','IN_HOUSE','COMPLETE','CANCELLED')),
            deposit        INTEGER NOT NULL DEFAULT 0,
            notes          TEXT NOT NULL DEFAULT '',
            keycard        TEXT NOT NULL DEFAULT '',
            checked_in_on  TEXT,
            checked_out_on TEXT,
            pricing        TEXT,
            CHECK (departure > arrival)
        )""",
        """
        CREATE TABLE IF NOT EXISTS receipts (
            id               INTEGER PRIMARY KEY AUTOINCREMENT,
            reservation_code TEXT NOT NULL REFERENCES reservations(code),
            date             TEXT NOT NULL,
            amount           INTEGER NOT NULL,
            method           TEXT NOT NULL,
            reference        TEXT NOT NULL DEFAULT '',
            is_deposit       INTEGER NOT NULL DEFAULT 0
        )""",
        """
        CREATE TABLE IF NOT EXISTS security_deposits (
            reservation_code TEXT PRIMARY KEY REFERENCES reservations(code),
            amount           INTEGER NOT NULL CHECK (amount > 0),
            returned         INTEGER NOT NULL DEFAULT 0
        )""",
        "CREATE INDEX IF NOT EXISTS idx_res_room_dates ON reservations(room_number, arrival, departure)",
        "CREATE INDEX IF NOT EXISTS idx_res_status ON reservations(status)",
        "CREATE INDEX IF NOT EXISTS idx_receipts_res ON receipts(reservation_code)"
    );
}
