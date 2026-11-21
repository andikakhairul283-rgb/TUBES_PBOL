package com.example.tubespbolkel7.model;

import com.example.tubespbolkel7.db.Database;
import com.example.tubespbolkel7.db.RoomRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.example.tubespbolkel7.model.HotelData.Room;

public final class RoomInventory {
    public static final class Type {
        private static final Type STANDARD = new Type("STANDARD", "Standard", 350_000, 2, "AC, TV, Wi-Fi, kamar mandi dalam");
        private static final Type DELUXE = new Type("DELUXE", "Deluxe", 550_000, 3, "AC, TV, Wi-Fi, minibar, balkon");
        private static final Type SUITE = new Type("SUITE", "Suite", 1_200_000, 4, "AC, Smart TV, Wi-Fi, minibar, ruang tamu, bathtub");

        private final String code;
        private final String label;
        private final long rate;
        private final int capacity;
        private final String facilities;

        private Type(String code, String label, long rate, int capacity, String facilities) {
            this.code = code;
            this.label = label;
            this.rate = rate;
            this.capacity = capacity;
            this.facilities = facilities;
        }

        public String name() { return code; }
        public String label() { return label; }
        public long rate() { return rate; }
        public int capacity() { return capacity; }
        public String facilities() { return facilities; }
        @Override public String toString() { return label; }
    }

    public enum Condition {
        READY("Tersedia"), CLEANING("Dibersihkan"), MAINTENANCE("Maintenance");

        private final String label;
        Condition(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    public enum Status {
        READY("Tersedia"), BOOKED("Dipesan"), OCCUPIED("Terisi"),
        CLEANING("Dibersihkan"), MAINTENANCE("Maintenance");

        private final String label;
        Status(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    public enum Priority {
        NORMAL("Normal"), HIGH("Tinggi"), URGENT("Darurat");

        private final String label;
        Priority(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    public record Unit(Room room, int floor, Type type, Condition condition, String notes) { }
    public record Job(String code, String room, Condition condition, Priority priority, String description, String assignee,
               LocalDateTime opened, LocalDateTime finished, int estimatedMinutes) {
        public boolean active() { return finished == null; }
    }
    public record Material(String job, String room, String name, int quantity, LocalDateTime requested) { }
    public record CardRequest(String identifier, LocalDateTime requested) { }

    private final Map<String, Unit> units = new LinkedHashMap<>();
    private final Map<String, Type> types = new LinkedHashMap<>();
    private final Map<String, String> customFacilities = new LinkedHashMap<>();
    private final Map<String, CardRequest> cardRequests = new LinkedHashMap<>();
    private final List<Job> jobs = new ArrayList<>();
    private final List<Material> materials = new ArrayList<>();
    private final MaintenanceStock stock = new MaintenanceStock();
    private int nextTicket = 1;
    private int nextType = 1;
    private final RoomRepository repository;

    public RoomInventory() { this(new RoomRepository(Database.shared())); }

    public RoomInventory(RoomRepository repository) {
        this.repository = repository;
        RoomRepository.Snapshot saved = repository.load();
        if (saved.types().isEmpty() && saved.rooms().isEmpty()) {
            for (Type type : List.of(Type.STANDARD, Type.DELUXE, Type.SUITE)) types.put(type.code, type);
            register(List.of("101", "102", "105", "108"), 1, Type.STANDARD);
            register(List.of("201", "204", "208"), 2, Type.DELUXE);
            register(List.of("302", "305"), 3, Type.SUITE);
            persist();
        } else {
            restore(saved);
        }
    }

    public List<Room> rooms() { return units.values().stream().map(Unit::room).toList(); }
    public List<Unit> units() { return List.copyOf(units.values()); }
    public List<Type> types() { return List.copyOf(types.values()); }
    public Unit find(String number) { return units.get(number); }
    public List<Job> jobs() { return List.copyOf(jobs); }
    public Job activeJob(String number) {
        return jobs.stream().filter(job -> job.room().equals(number) && job.active()).findFirst().orElse(null);
    }
    public List<Material> materials() { return List.copyOf(materials); }
    public MaintenanceStock stock() { return stock; }
    public String customFacilities(String number) { return customFacilities.getOrDefault(number, ""); }
    public String facilities(Unit unit) {
        String custom = customFacilities(unit.room().number());
        return custom.isBlank() ? unit.type().facilities : custom;
    }
    public CardRequest cardRequest(String number) { return cardRequests.get(number); }

    public void setFacilities(String number, String facilities) {
        requireUnit(number);
        if (facilities.length() > 400) throw new IllegalArgumentException("Fasilitas maksimal 400 karakter.");
        if (facilities.isBlank()) customFacilities.remove(number);
        else customFacilities.put(number, facilities);
        persist();
    }

    public void requestCard(String number, String identifier) {
        requireUnit(number);
        if (!identifier.matches("[A-Za-z0-9-]{3,40}")) {
            throw new IllegalArgumentException("ID keycard harus berisi 3–40 huruf, angka, atau tanda hubung.");
        }
        if (cardRequests.entrySet().stream().anyMatch(entry -> !entry.getKey().equals(number)
                && entry.getValue().identifier().equalsIgnoreCase(identifier))) {
            throw new IllegalArgumentException("ID keycard ini tercatat untuk kamar lain.");
        }
        cardRequests.put(number, new CardRequest(identifier, LocalDateTime.now()));
        persist();
    }

    public void saveType(Type original, String label, long rate, int capacity, String facilities) {
        if (label.isBlank() || label.length() > 45 || rate < 1 || rate > 100_000_000 || capacity < 1
                || capacity > 20 || facilities.length() > 400) {
            throw new IllegalArgumentException("Isi nama (maksimal 45 karakter), tarif Rp 1–100.000.000, kapasitas 1–20, dan fasilitas maksimal 400 karakter.");
        }
        if (types.values().stream().anyMatch(type -> type != original && type.label.equalsIgnoreCase(label))) {
            throw new IllegalArgumentException("Nama tipe kamar sudah terdaftar.");
        }
        String code = original == null ? "TIPE-" + nextType++ : original.code;
        Type replacement = new Type(code, label, rate, capacity, facilities);
        types.put(code, replacement);
        if (original != null) {
            units.replaceAll((number, unit) -> unit.type() == original
                    ? new Unit(new Room(number, label, rate), unit.floor(), replacement, unit.condition(), unit.notes())
                    : unit);
        }
        persist();
    }

    public void removeType(Type type) {
        if (units.values().stream().anyMatch(unit -> unit.type() == type)) {
            throw new IllegalArgumentException("Pindahkan kamar yang memakai tipe ini sebelum menghapusnya.");
        }
        types.remove(type.code, type);
        persist();
    }

    public void remove(String number) {
        requireUnit(number);
        if (jobs.stream().anyMatch(job -> job.room().equals(number))) {
            throw new IllegalArgumentException("Kamar memiliki riwayat tugas housekeeping atau maintenance.");
        }
        units.remove(number);
        customFacilities.remove(number);
        cardRequests.remove(number);
        persist();
    }

    public void save(String number, int floor, Type type, Condition condition, String notes, boolean newRoom, boolean activeBooking) {
        if (!number.matches("[0-9]{2,6}") || floor < 1 || floor > 50 || type == null || types.get(type.code) != type || condition == null) {
            throw new IllegalArgumentException("Isi nomor kamar (2–6 digit), lantai 1–50, tipe, dan status.");
        }
        if (notes.length() > 300) throw new IllegalArgumentException("Catatan maksimal 300 karakter.");
        Unit existing = find(number);
        if (newRoom && existing != null) throw new IllegalArgumentException("Nomor kamar sudah terdaftar.");
        if (!newRoom && existing == null) throw new IllegalArgumentException("Kamar tidak ditemukan.");
        if (activeBooking && existing != null && existing.type() != type) {
            throw new IllegalArgumentException("Tipe kamar dikunci selama masih ada reservasi aktif.");
        }
        if (existing != null) validateTransition(existing, condition);
        units.put(number, new Unit(new Room(number, type.label, type.rate), floor, type,
                existing == null ? Condition.READY : existing.condition(), notes));
        setCondition(number, condition);
        persist();
    }

    public void setCondition(String room, Condition condition) {
        Unit unit = requireUnit(room);
        validateTransition(unit, condition);
        if (unit.condition() == condition) return;
        Job active = activeJob(room);
        if (active != null) jobs.set(jobs.indexOf(active), finished(active));
        if (condition != Condition.READY) {
            jobs.addFirst(newJob(room, condition, Priority.NORMAL,
                    condition == Condition.CLEANING ? "Pembersihan kamar" : "Pemeriksaan kondisi kamar", "", 0));
        }
        updateCondition(unit, condition);
        persist();
    }

    public void report(String room, Condition condition, Priority priority, String description, String assignee, int estimatedMinutes) {
        Unit unit = requireUnit(room);
        if (condition == null || condition == Condition.READY || priority == null || description.isBlank()) {
            throw new IllegalArgumentException("Pilih jenis tugas dan prioritas, lalu isi deskripsi.");
        }
        validateJob(description, assignee, estimatedMinutes);
        if (activeJob(room) != null) {
            throw new IllegalArgumentException("Kamar sudah mempunyai tugas aktif. Buka detail tugas untuk memperbaruinya.");
        }
        jobs.addFirst(newJob(room, condition, priority, description.strip(), assignee.strip(), estimatedMinutes));
        updateCondition(unit, condition);
        persist();
    }

    public void editJob(Job job, Priority priority, String description, String assignee, int estimatedMinutes) {
        int index = requireActiveJob(job);
        if (priority == null) throw new IllegalArgumentException("Pilih prioritas tugas.");
        validateJob(description, assignee, estimatedMinutes);
        jobs.set(index, new Job(job.code(), job.room(), job.condition(), priority, description.strip(), assignee.strip(),
                job.opened(), null, estimatedMinutes));
        persist();
    }

    public void finish(Job job) {
        int index = requireActiveJob(job);
        jobs.set(index, finished(job));
        updateCondition(requireUnit(job.room()), Condition.READY);
        persist();
    }

    public void requestMaterial(Job job, String name, int quantity) {
        requireActiveJob(job);
        if (name.isBlank() || quantity < 1 || quantity > 1000) {
            throw new IllegalArgumentException("Masukkan nama material dan jumlah 1–1000.");
        }
        materials.addFirst(new Material(job.code(), job.room(), name.strip(), quantity, LocalDateTime.now()));
        persist();
    }

    /** Menyimpan seluruh keadaan kamar ke database setiap kali ada perubahan. */
    private void persist() {
        repository.save(new RoomRepository.Snapshot(
                types.values().stream().map(type -> new RoomRepository.TypeRow(
                        type.code, type.label, type.rate, type.capacity, type.facilities)).toList(),
                units.values().stream().map(unit -> new RoomRepository.RoomRow(unit.room().number(), unit.floor(),
                        unit.type().code, unit.condition().name(), unit.notes(),
                        customFacilities.getOrDefault(unit.room().number(), ""))).toList(),
                jobs.stream().map(job -> new RoomRepository.JobRow(job.code(), job.room(), job.condition().name(),
                        job.priority().name(), job.description(), job.assignee(), job.opened().toString(),
                        job.finished() == null ? null : job.finished().toString(), job.estimatedMinutes())).toList(),
                materials.reversed().stream().map(item -> new RoomRepository.MaterialRow(item.job(), item.room(),
                        item.name(), item.quantity(), item.requested().toString())).toList(),
                cardRequests.entrySet().stream().map(entry -> new RoomRepository.CardRow(entry.getKey(),
                        entry.getValue().identifier(), entry.getValue().requested().toString())).toList()));
    }

    private void restore(RoomRepository.Snapshot saved) {
        for (RoomRepository.TypeRow row : saved.types()) {
            types.put(row.code(), new Type(row.code(), row.label(), row.rate(), row.capacity(), row.facilities()));
            if (row.code().matches("TIPE-[0-9]+")) nextType = Math.max(nextType, Integer.parseInt(row.code().substring(5)) + 1);
        }
        for (RoomRepository.RoomRow row : saved.rooms()) {
            Type type = types.get(row.typeCode());
            units.put(row.number(), new Unit(new Room(row.number(), type.label, type.rate), row.floor(), type,
                    Condition.valueOf(row.condition()), row.notes()));
            if (!row.customFacilities().isBlank()) customFacilities.put(row.number(), row.customFacilities());
        }
        for (RoomRepository.JobRow row : saved.jobs()) {
            jobs.add(new Job(row.code(), row.room(), Condition.valueOf(row.condition()), Priority.valueOf(row.priority()),
                    row.description(), row.assignee(), LocalDateTime.parse(row.opened()),
                    row.finished() == null ? null : LocalDateTime.parse(row.finished()), row.estimatedMinutes()));
            if (row.code().matches("WO-[0-9]+")) nextTicket = Math.max(nextTicket, Integer.parseInt(row.code().substring(3)) + 1);
        }
        for (RoomRepository.MaterialRow row : saved.materials()) {
            materials.add(new Material(row.job(), row.room(), row.name(), row.quantity(), LocalDateTime.parse(row.requested())));
        }
        for (RoomRepository.CardRow row : saved.cards()) {
            cardRequests.put(row.room(), new CardRequest(row.identifier(), LocalDateTime.parse(row.requested())));
        }
    }

    private void register(List<String> numbers, int floor, Type type) {
        for (String number : numbers) {
            units.put(number, new Unit(new Room(number, type.label, type.rate), floor, type, Condition.READY, ""));
        }
    }

    private Unit requireUnit(String number) {
        Unit unit = find(number);
        if (unit == null) throw new IllegalArgumentException("Kamar tidak ditemukan.");
        return unit;
    }

    private static void validateTransition(Unit unit, Condition condition) {
        if (condition == null) throw new IllegalArgumentException("Pilih kondisi operasional kamar.");
        if (unit.condition() != Condition.READY && condition != Condition.READY && condition != unit.condition()) {
            throw new IllegalArgumentException("Selesaikan tugas aktif sebelum membuat tugas dengan jenis berbeda.");
        }
    }

    private int requireActiveJob(Job job) {
        int index = jobs.indexOf(job);
        if (index < 0 || !job.active()) throw new IllegalArgumentException("Tugas telah berubah. Buka kembali halamannya.");
        return index;
    }

    private static void validateJob(String description, String assignee, int estimatedMinutes) {
        if (description.isBlank() || description.length() > 500 || assignee.length() > 100) {
            throw new IllegalArgumentException("Isi deskripsi maksimal 500 karakter dan nama petugas maksimal 100 karakter.");
        }
        if (estimatedMinutes < 0 || estimatedMinutes > 10_080) {
            throw new IllegalArgumentException("Estimasi pengerjaan harus antara 0 dan 10.080 menit.");
        }
    }

    private void updateCondition(Unit unit, Condition condition) {
        units.put(unit.room().number(), new Unit(unit.room(), unit.floor(), unit.type(), condition, unit.notes()));
    }

    private Job newJob(String room, Condition condition, Priority priority, String description, String assignee, int estimatedMinutes) {
        return new Job("WO-%04d".formatted(nextTicket++), room, condition, priority, description, assignee,
                LocalDateTime.now(), null, estimatedMinutes);
    }

    private static Job finished(Job job) {
        return new Job(job.code(), job.room(), job.condition(), job.priority(), job.description(), job.assignee(),
                job.opened(), LocalDateTime.now(), job.estimatedMinutes());
    }
}
