package com.example.tubespbolkel7.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GuestRegistry {
    public record Guest(String id, String name, String phone, String email, String identity, boolean vip, String notes) {
        public String choice() { return name + " · " + id; }
    }

    private final Map<String, Guest> guests = new LinkedHashMap<>();
    private int next = 1;

    public List<Guest> all() { return List.copyOf(guests.values()).reversed(); }
    public Guest find(String id) { return guests.get(id); }

    public Guest fromSelection(String text) {
        for (Guest guest : guests.values()) {
            if (guest.choice().equals(text)) return guest;
        }
        List<Guest> matches = guests.values().stream().filter(guest -> guest.name().equalsIgnoreCase(text)).toList();
        if (matches.size() > 1) throw new IllegalArgumentException("Ada beberapa tamu bernama sama. Pilih nama dengan kode tamu dari daftar.");
        return matches.isEmpty() ? null : matches.getFirst();
    }

    public Guest save(Guest original, String name, String phone, String email, String identity, boolean vip, String notes) {
        if (name.isBlank() || name.length() > 100) throw new IllegalArgumentException("Nama tamu wajib diisi, maksimal 100 karakter.");
        if (!phone.isBlank() && !phone.matches("[+0-9][0-9 ()-]{5,24}")) {
            throw new IllegalArgumentException("Nomor telepon tidak valid.");
        }
        HotelSettings.checkEmail(email);
        if (identity.length() > 40 || notes.length() > 500) {
            throw new IllegalArgumentException("Nomor identitas maksimal 40 karakter dan catatan maksimal 500 karakter.");
        }
        if (!identity.isBlank() && guests.values().stream().anyMatch(guest -> guest != original
                && guest.identity().equalsIgnoreCase(identity))) {
            throw new IllegalArgumentException("Nomor identitas sudah terdaftar.");
        }
        String id = original == null ? "TMU-%04d".formatted(next++) : original.id();
        Guest guest = new Guest(id, name, phone, email, identity, vip, notes);
        guests.put(id, guest);
        return guest;
    }
}
