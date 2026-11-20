package com.example.tubespbolkel7.model;

import java.util.LinkedHashMap;
import java.util.List;

public final class MaintenanceStock {
    public record Item(long id, String name, String unit, String location, int quantity, int minimum) {
        public String status() {
            if (quantity == 0) return "Habis";
            return quantity <= minimum ? "Stok rendah" : "Tersedia";
        }
    }

    private final LinkedHashMap<Long, Item> items = new LinkedHashMap<>();
    private long nextId = 1;

    public List<Item> items() { return List.copyOf(items.values()); }

    public void save(Item original, String name, String unit, String location, int quantity, int minimum) {
        if (name.isBlank() || name.length() > 100 || unit.isBlank() || unit.length() > 20 || location.length() > 100) {
            throw new IllegalArgumentException("Isi nama dan satuan. Lokasi penyimpanan maksimal 100 karakter.");
        }
        if (quantity < 0 || minimum < 0 || quantity > 999_999 || minimum > 999_999) {
            throw new IllegalArgumentException("Jumlah dan batas stok harus antara 0 dan 999.999.");
        }
        if (items.values().stream().anyMatch(item -> item != original && item.name().equalsIgnoreCase(name))) {
            throw new IllegalArgumentException("Nama material sudah terdaftar.");
        }
        long id = original == null ? nextId++ : original.id();
        items.put(id, new Item(id, name, unit, location, quantity, minimum));
    }
}
