package com.example.tubespbolkel7.ui;

import com.example.tubespbolkel7.model.Role;

import java.util.List;

enum HotelRoute {
    DASHBOARD("Dashboard", "▥"), FLOOR_PLAN("Denah Kamar", "▦"),
    RESERVATIONS("Reservasi", "▤"), NEW_RESERVATION("Reservasi Baru", "+", RESERVATIONS),
    AVAILABILITY("Kalender Ketersediaan", "▦", RESERVATIONS),
    CHECK_IN("Tamu & Check-in", "→"), CHECK_OUT("Check-out", "←", CHECK_IN), IN_HOUSE("Tamu In-House", "▣", CHECK_IN),
    WALK_IN("Tamu Walk-in", "+", CHECK_IN),
    GUESTS("Data Tamu", "◎"), PAYMENTS("Pembayaran", "Rp"), SERVICES("Layanan Kamar", "◇"),
    SERVICE_CATALOG("Layanan Tambahan", "+"), ROOM_TYPES("Tipe Kamar", "◇"), ROOMS("Data Kamar", "▣"),
    ROOM_FORM("Formulir Kamar", "+", ROOMS), ROOM_GRID("Grid Status", "▦"), HOUSEKEEPING("Housekeeping", "⌁"),
    OCCUPANCY("Laporan", "▤"), REVENUE("Pendapatan", "Rp", OCCUPANCY),
    PROFILE("Pengaturan", "⚙"), RATES("Tarif & Pajak", "Rp", PROFILE), USERS("Pengguna & Peran", "◎", PROFILE),
    POLICIES("Kebijakan", "▤", PROFILE), MY_PROFILE("Profil Saya", "◎", PROFILE);

    record Link(String label, HotelRoute route) {
        Link(HotelRoute route) {
            this(route.title, route);
        }
    }

    record Section(String title, boolean collapsible, List<Link> links) {
        Section(String title, Link... links) {
            this(title, false, List.of(links));
        }
    }

    final String title;
    final String icon;
    final HotelRoute parent;

    HotelRoute(String title, String icon) {
        this(title, icon, null);
    }

    HotelRoute(String title, String icon, HotelRoute parent) {
        this.title = title;
        this.icon = icon;
        this.parent = parent;
    }

    static List<Section> menu(Role role) {
        Section rooms = new Section("Manajemen Kamar", true,
                List.of(new Link(ROOM_TYPES), new Link(ROOMS), new Link(ROOM_GRID), new Link(HOUSEKEEPING)));
        return switch (role) {
            case STAFF -> List.of(
                    new Section("OPERASIONAL", new Link(DASHBOARD), new Link("Check-in / Check-out", CHECK_OUT), new Link(RESERVATIONS)),
                    new Section("LAYANAN", new Link(CHECK_IN), new Link(PAYMENTS), new Link(SERVICE_CATALOG), new Link(SERVICES)),
                    new Section("LAYANAN & LAPORAN", new Link(OCCUPANCY)),
                    rooms, new Section("PENGATURAN", new Link(PROFILE)));
            case OWNER -> List.of(
                    new Section("OPERASIONAL", new Link(DASHBOARD), new Link(FLOOR_PLAN), new Link(RESERVATIONS)),
                    new Section("LAYANAN & LAPORAN", new Link(CHECK_IN), new Link(SERVICES), new Link(GUESTS)),
                    rooms, new Section("PENGATURAN", new Link(OCCUPANCY), new Link(PROFILE)));
            case MAINTENANCE -> List.of(new Section("MENU TUGAS", new Link("Dasbor", DASHBOARD), new Link(ROOM_GRID),
                    new Link("Housekeeping & Maint.", HOUSEKEEPING), new Link("Data Kamar · Lihat saja", ROOMS)));
        };
    }

    boolean availableTo(Role role) {
        if (this == GUESTS || this == WALK_IN) return role == Role.OWNER;
        if (role == Role.MAINTENANCE) {
            return this == DASHBOARD || this == ROOM_GRID || this == HOUSEKEEPING || this == ROOMS;
        }
        return true;
    }

}
