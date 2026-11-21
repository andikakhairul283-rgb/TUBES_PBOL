package com.example.tubespbolkel7.model;

import com.example.tubespbolkel7.db.Database;
import com.example.tubespbolkel7.db.ServiceRepository;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.time.LocalDateTime;
import java.util.List;

public final class ServiceLedger {
    public enum Category {
        LAUNDRY("Laundry", "Cuci & setrika", "≋"),
        FOOD("Makanan & Minuman", "Pesanan dari dapur", "♨"),
        BED("Extra Bed", "Perlengkapan tidur", "▤"),
        OTHER("Lainnya", "Perlengkapan & transport", "◇");

        private final String label;
        private final String caption;
        private final String symbol;

        Category(String label, String caption, String symbol) {
            this.label = label;
            this.caption = caption;
            this.symbol = symbol;
        }

        public String caption() { return caption; }
        public String symbol() { return symbol; }
        @Override public String toString() { return label; }
    }

    public enum Status {
        PROCESSING("Diproses"), DELIVERED("Diantar"), COMPLETE("Selesai"), CANCELLED("Dibatalkan");

        private final String label;

        Status(String label) { this.label = label; }

        @Override public String toString() { return label; }

        public List<Status> choices() {
            return switch (this) {
                case PROCESSING -> List.of(PROCESSING, DELIVERED, COMPLETE, CANCELLED);
                case DELIVERED -> List.of(DELIVERED, COMPLETE, CANCELLED);
                case COMPLETE, CANCELLED -> List.of(this);
            };
        }
    }

    public record CatalogItem(String name, Category category, String unit, long price, boolean available) {
        @Override public String toString() { return name; }
    }

    public record RoomGuest(String reservation, String room, String guest) {
        @Override public String toString() { return "Kamar " + room + " · " + guest; }
    }

    public record Order(String code, LocalDateTime created, RoomGuest roomGuest, CatalogItem item,
                 int quantity, Status status, String notes) {
        public long subtotal() { return quantity * item.price(); }

        Order withStatus(Status nextStatus) {
            return new Order(code, created, roomGuest, item, quantity, nextStatus, notes);
        }
    }

    private final ObservableList<CatalogItem> catalog = FXCollections.observableArrayList(
            new CatalogItem("Cuci & Setrika Reguler", Category.LAUNDRY, "kg", 15_000, true),
            new CatalogItem("Laundry Kilat (4 Jam)", Category.LAUNDRY, "pcs", 20_000, true),
            new CatalogItem("Cuci Kering Jas / Gaun", Category.LAUNDRY, "stel", 45_000, true),
            new CatalogItem("Nasi Goreng Spesial Hotel", Category.FOOD, "porsi", 45_000, true),
            new CatalogItem("Club Sandwich & Kentang", Category.FOOD, "porsi", 40_000, true),
            new CatalogItem("Jus Buah Segar", Category.FOOD, "gelas", 25_000, true),
            new CatalogItem("Afternoon Tea Set", Category.FOOD, "set", 85_000, true),
            new CatalogItem("Extra Bed King / Single", Category.BED, "malam", 150_000, true),
            new CatalogItem("Ranjang Bayi", Category.BED, "malam", 50_000, true),
            new CatalogItem("Antar-Jemput Bandara", Category.OTHER, "trip", 150_000, true),
            new CatalogItem("Air Mineral & Snack Pack", Category.OTHER, "paket", 45_000, true),
            new CatalogItem("Layanan Spa Kamar", Category.OTHER, "sesi", 220_000, true));
    private final ObservableList<Order> orders = FXCollections.observableArrayList();
    private final ObservableList<CatalogItem> catalogView = FXCollections.unmodifiableObservableList(catalog);
    private final ObservableList<Order> ordersView = FXCollections.unmodifiableObservableList(orders);
    private int nextCode = 1;
    private final ServiceRepository repository;

    public ServiceLedger() { this(new ServiceRepository(Database.shared())); }

    public ServiceLedger(ServiceRepository repository) {
        this.repository = repository;
        ServiceRepository.Snapshot saved = repository.load();
        if (saved.catalog().isEmpty()) {
            persist();
            return;
        }
        catalog.setAll(saved.catalog().stream().map(ServiceLedger::toItem).toList());
        orders.setAll(saved.orders().stream().map(row -> new Order(row.code(), LocalDateTime.parse(row.created()),
                new RoomGuest(row.reservation(), row.room(), row.guest()), toItem(row.item()), row.quantity(),
                Status.valueOf(row.status()), row.notes())).toList());
        for (Order order : orders) {
            if (order.code().matches("LYN-[0-9]+")) nextCode = Math.max(nextCode, Integer.parseInt(order.code().substring(4)) + 1);
        }
    }

    public ObservableList<CatalogItem> catalog() { return catalogView; }

    public ObservableList<Order> orders() { return ordersView; }

    public Order addOrder(LocalDateTime time, RoomGuest guest, CatalogItem item, int quantity, String notes) {
        if (guest == null || item == null || !catalog.contains(item) || !item.available() || quantity < 1 || quantity > 100) {
            throw new IllegalArgumentException("Pilih kamar, layanan yang tersedia, dan jumlah antara 1–100.");
        }
        Order order = new Order(nextOrderCode(), time, guest, item, quantity, Status.PROCESSING, notes.strip());
        orders.add(0, order);
        persist();
        return order;
    }

    public void updateStatus(Order order, Status status) {
        int index = orders.indexOf(order);
        if (index < 0 || !order.status().choices().contains(status)) {
            throw new IllegalArgumentException("Status pesanan sudah berubah. Buka kembali detail pesanan.");
        }
        orders.set(index, order.withStatus(status));
        persist();
    }

    public void saveItem(CatalogItem original, CatalogItem item) {
        if (item.name().isBlank() || item.unit().isBlank() || item.price() < 0 || item.price() > 100_000_000) {
            throw new IllegalArgumentException("Lengkapi nama, satuan, dan tarif sampai Rp100 juta.");
        }
        if (catalog.stream().anyMatch(other -> !other.equals(original) && other.name().equalsIgnoreCase(item.name()))) {
            throw new IllegalArgumentException("Nama layanan ini sudah dipakai.");
        }
        if (original == null) {
            catalog.add(item);
        } else {
            int index = catalog.indexOf(original);
            if (index < 0) {
                throw new IllegalArgumentException("Data layanan sudah berubah. Buka kembali detail layanan.");
            }
            catalog.set(index, item);
        }
        persist();
    }

    public long serviceTotal(String reservationCode) {
        if (reservationCode == null || reservationCode.isBlank()) {
            return 0;
        }
        return orders.stream().filter(order -> order.status() != Status.CANCELLED
                        && reservationCode.equals(order.roomGuest().reservation()))
                .mapToLong(Order::subtotal).sum();
    }

    /** Menyimpan katalog dan seluruh pesanan ke database setiap kali ada perubahan. */
    private void persist() {
        repository.save(new ServiceRepository.Snapshot(
                catalog.stream().map(ServiceLedger::toRow).toList(),
                orders.stream().map(order -> new ServiceRepository.OrderRow(order.code(), order.created().toString(),
                        order.roomGuest().reservation(), order.roomGuest().room(), order.roomGuest().guest(),
                        toRow(order.item()), order.quantity(), order.status().name(), order.notes())).toList()));
    }

    private static ServiceRepository.ItemRow toRow(CatalogItem item) {
        return new ServiceRepository.ItemRow(item.name(), item.category().name(), item.unit(), item.price(), item.available());
    }

    private static CatalogItem toItem(ServiceRepository.ItemRow row) {
        return new CatalogItem(row.name(), Category.valueOf(row.category()), row.unit(), row.price(), row.available());
    }

    private String nextOrderCode() { return "LYN-%03d".formatted(nextCode++); }
}
