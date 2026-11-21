package com.example.tubespbolkel7.model;

import com.example.tubespbolkel7.db.BookingRepository;
import com.example.tubespbolkel7.db.Database;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.example.tubespbolkel7.model.GuestRegistry.Guest;
import static com.example.tubespbolkel7.model.RoomInventory.Condition;
import static com.example.tubespbolkel7.model.RoomInventory.Type;
import static com.example.tubespbolkel7.model.RoomInventory.Unit;

public final class HotelData {
    public enum Status {
        BOOKED("Dipesan"), IN_HOUSE("Terisi/Check-in"), COMPLETE("Selesai"), CANCELLED("Dibatalkan");

        private final String label;

        Status(String label) { this.label = label; }
        public String label() { return label; }
        @Override public String toString() { return label; }
    }

    public record Room(String number, String type, long rate) { }

    public record Booking(String code, String guest, Room room, LocalDate arrival, LocalDate departure, Status status,
                   long deposit, String notes) {
        public long nights() { return ChronoUnit.DAYS.between(arrival, departure); }
        long subtotal() { return nights() * room.rate(); }
    }

    public record Receipt(String code, LocalDate date, long amount, String method, String reference, boolean deposit) { }
    public record Stay(LocalDate checkIn, LocalDate checkOut) { }
    public record SecurityDeposit(long amount, boolean returned) { }

    private final ObservableList<Booking> bookings = FXCollections.observableArrayList();
    private final ObservableList<Booking> view = FXCollections.unmodifiableObservableList(bookings);
    private final List<Receipt> receipts = new ArrayList<>();
    private final Map<String, Stay> stays = new HashMap<>();
    private final Map<String, String> guestByBooking = new HashMap<>();
    private final Map<String, HotelSettings.Pricing> pricingByBooking = new HashMap<>();
    private final Map<String, SecurityDeposit> securityDeposits = new HashMap<>();
    private final Map<String, String> keycards = new HashMap<>();
    private final GuestRegistry guests = new GuestRegistry();
    private final ServiceLedger services = new ServiceLedger();
    private final RoomInventory inventory = new RoomInventory();
    private final HotelSettings settings = new HotelSettings();
    private final FrontDeskNotes frontDesk = new FrontDeskNotes();
    private int nextNumber = 1;
    private final BookingRepository repository = new BookingRepository(Database.shared());

    public HotelData() { restore(); }

    public ObservableList<Booking> bookings() { return view; }
    public ServiceLedger services() { return services; }
    public List<Room> rooms() { return inventory.rooms(); }
    public RoomInventory inventory() { return inventory; }
    public HotelSettings settings() { return settings; }
    public FrontDeskNotes frontDesk() { return frontDesk; }
    public GuestRegistry guests() { return guests; }
    public Guest guestFor(String code) {
        String id = guestByBooking.get(code);
        return id == null ? null : guests.find(id);
    }

    public List<Booking> bookingsFor(Guest guest) {
        return bookings.stream().filter(item -> guest.id().equals(guestByBooking.get(item.code()))).toList();
    }

    public void saveGuest(Guest original, String name, String phone, String email, String identity, boolean vip, String notes) {
        Guest saved = guests.save(original, name, phone, email, identity, vip, notes);
        if (original == null || saved.name().equals(original.name())) return;
        for (Booking booking : bookingsFor(saved)) {
            bookings.set(bookings.indexOf(booking), new Booking(booking.code(), saved.name(), booking.room(),
                    booking.arrival(), booking.departure(), booking.status(), booking.deposit(), booking.notes()));
        }
    }
    public List<Receipt> receipts() { return List.copyOf(receipts); }
    public Stay stay(String code) { return stays.get(code); }
    public LocalDate today() { return LocalDate.now(); }
    public String nextCode() { return String.format(Locale.ROOT, "RSV-%04d", nextNumber); }

    public Booking find(String code) {
        return bookings.stream().filter(item -> item.code().equals(code)).findFirst().orElse(null);
    }

    public boolean available(Room room, LocalDate arrival, LocalDate departure, String exceptCode) {
        Unit unit = inventory.find(room.number());
        if (unit == null || unit.condition() != Condition.READY) return false;
        return bookings.stream().noneMatch(item -> !item.code().equals(exceptCode) && sameRoom(item, room)
                && (item.status() == Status.BOOKED || item.status() == Status.IN_HOUSE)
                && arrival.isBefore(item.departure()) && departure.isAfter(item.arrival()));
    }

    public Booking occupant(String number) {
        return bookings.stream().filter(item -> item.room().number().equals(number) && item.status() == Status.IN_HOUSE)
                .findFirst().orElse(null);
    }

    public RoomInventory.Status roomStatus(Unit unit) {
        if (occupant(unit.room().number()) != null) return RoomInventory.Status.OCCUPIED;
        return switch (unit.condition()) {
            case CLEANING -> RoomInventory.Status.CLEANING;
            case MAINTENANCE -> RoomInventory.Status.MAINTENANCE;
            case READY -> available(unit.room(), today(), today().plusDays(1), null)
                    ? RoomInventory.Status.READY : RoomInventory.Status.BOOKED;
        };
    }

    public void saveRoom(String number, int floor, Type type, Condition condition, String notes, boolean create) {
        ensureUnoccupied(number, condition);
        boolean activeBooking = bookings.stream().anyMatch(item -> item.room().number().equals(number)
                && (item.status() == Status.BOOKED || item.status() == Status.IN_HOUSE));
        inventory.save(number, floor, type, condition, notes, create, activeBooking);
    }

    public void setRoomCondition(String number, Condition condition) {
        ensureUnoccupied(number, condition);
        inventory.setCondition(number, condition);
    }

    public void removeRoom(String number) {
        if (bookings.stream().anyMatch(item -> item.room().number().equals(number))) {
            throw new IllegalArgumentException("Kamar memiliki riwayat reservasi dan harus tetap tersedia dalam arsip.");
        }
        inventory.remove(number);
    }

    public void saveRoomType(Type type, String name, long rate, int capacity, String facilities) {
        inventory.saveType(type, name, rate, capacity, facilities);
        if (type == null || name.equals(type.label())) return;
        for (Booking booking : List.copyOf(bookings)) {
            if (!booking.room().type().equals(type.label())) continue;
            Room room = new Room(booking.room().number(), name, booking.room().rate());
            bookings.set(bookings.indexOf(booking), new Booking(booking.code(), booking.guest(), room, booking.arrival(),
                    booking.departure(), booking.status(), booking.deposit(), booking.notes()));
        }
        persist();
    }

    public void removeRoomType(Type type) {
        if (bookings.stream().anyMatch(item -> item.room().type().equals(type.label()))) {
            throw new IllegalArgumentException("Tipe ini masih dipakai dalam riwayat reservasi.");
        }
        inventory.removeType(type);
    }

    public void reportRoom(String number, Condition condition, RoomInventory.Priority priority, String description, String assignee) {
        reportRoom(number, condition, priority, description, assignee, 0);
    }

    public void reportRoom(String number, Condition condition, RoomInventory.Priority priority, String description, String assignee,
                    int estimatedMinutes) {
        ensureUnoccupied(number, condition);
        inventory.report(number, condition, priority, description, assignee, estimatedMinutes);
    }

    private void ensureUnoccupied(String number, Condition condition) {
        if (condition != Condition.READY && occupant(number) != null) {
            throw new IllegalArgumentException("Selesaikan check-out sebelum memblokir kamar yang masih terisi.");
        }
    }

    private static boolean sameRoom(Booking booking, Room room) {
        return booking.room().number().equals(room.number());
    }

    public void save(Booking item) {
        Unit unit = item.room() == null ? null : inventory.find(item.room().number());
        if (item.guest() == null || item.guest().isBlank() || unit == null) {
            throw new IllegalArgumentException("Masukkan nama tamu dan pilih kamar.");
        }
        if (item.arrival() == null || item.departure() == null || !item.departure().isAfter(item.arrival())) {
            throw new IllegalArgumentException("Tanggal check-out harus setelah check-in.");
        }
        Booking old = find(item.code());
        Room bookedRoom = old != null && sameRoom(old, item.room()) ? old.room() : unit.room();
        item = new Booking(item.code(), item.guest().strip(), bookedRoom, item.arrival(), item.departure(),
                item.status(), item.deposit(), item.notes());
        boolean inHouse = old != null && old.status() == Status.IN_HOUSE;
        if (old != null && old.status() != Status.BOOKED && !inHouse) {
            throw new IllegalArgumentException("Reservasi yang selesai atau dibatalkan tidak dapat diubah.");
        }
        if (item.status() != (inHouse ? Status.IN_HOUSE : Status.BOOKED)
                && (old == null || inHouse || item.status() != Status.CANCELLED)) {
            throw new IllegalArgumentException("Status reservasi mengikuti proses check-in dan check-out.");
        }
        if (inHouse && (!sameRoom(old, item.room()) || !item.arrival().equals(old.arrival()) || item.deposit() != old.deposit()
                || !item.departure().equals(old.departure()) && !item.departure().isAfter(today()))) {
            throw new IllegalArgumentException("Tamu yang menginap hanya dapat memperbarui keberangkatan setelah hari ini dan permintaan menginap.");
        }
        if (item.status() != Status.CANCELLED && !available(item.room(), item.arrival(), item.departure(), item.code())) {
            throw new IllegalArgumentException("Kamar sedang diblokir atau sudah dipesan dalam periode tersebut.");
        }
        if (item.deposit() < 0 || item.deposit() + paymentsFor(item.code()) > total(item)) {
            throw new IllegalArgumentException("Uang muka dan pembayaran tidak boleh melebihi tagihan.");
        }
        if (old == null && !item.code().equals(nextCode())) {
            throw new IllegalArgumentException("Kode reservasi berubah. Buka ulang formulir.");
        }
        Guest guest = inHouse || old != null && old.guest().equals(item.guest()) ? guestFor(item.code()) : guests.fromSelection(item.guest());
        if (guest == null) guest = guests.save(null, item.guest(), "", "", "", false, "");
        guestByBooking.put(item.code(), guest.id());
        pricingByBooking.putIfAbsent(item.code(), settings.pricing());
        item = new Booking(item.code(), guest.name(), item.room(), item.arrival(), item.departure(),
                item.status(), item.deposit(), item.notes());
        long depositChange = item.deposit() - (old == null ? 0 : old.deposit());
        if (depositChange != 0) {
            receipts.add(new Receipt(item.code(), today(), depositChange,
                    depositChange > 0 ? "Uang muka" : "Penyesuaian uang muka", "", true));
        }
        if (old == null) {
            bookings.addFirst(item);
            nextNumber++;
        } else {
            bookings.set(bookings.indexOf(old), item);
        }
        persist();
    }

    public long servicesTotal(Booking item) { return services.serviceTotal(item.code()); }
    private HotelSettings.Pricing pricing(String code) { return pricingByBooking.getOrDefault(code, settings.pricing()); }
    public int taxPercent(String code) { return pricing(code).combinedPercent(); }
    public long subtotal(Booking item) {
        long nights = item.nights();
        long base = item.subtotal();
        long extra = pricing(item.code()).weekendRate(item.room().rate()) - item.room().rate();
        if (extra == 0) return base;
        long weekends = (nights / 7) * 3;
        int firstDay = item.arrival().getDayOfWeek().getValue() - 1;
        for (int day = 0; day < nights % 7; day++) {
            if ((firstDay + day) % 7 >= 4) weekends++;
        }
        return base + weekends * extra;
    }
    public long tax(Booking item) { return Math.round((subtotal(item) + servicesTotal(item)) * (taxPercent(item.code()) / 100.0)); }
    public long total(Booking item) { return subtotal(item) + servicesTotal(item) + tax(item); }
    public long remaining(Booking item) { return Math.max(0, total(item) - item.deposit() - paymentsFor(item.code())); }

    public long suggestedSecurityDeposit(String code) { return pricing(code).securityDeposit(); }
    public SecurityDeposit securityDeposit(String code) { return securityDeposits.get(code); }

    public void collectSecurityDeposit(String code, long amount) {
        Booking item = requireBooking(code);
        if (item.status() != Status.BOOKED || amount <= 0 || amount > 100_000_000 || securityDeposit(code) != null) {
            throw new IllegalArgumentException("Jaminan hanya dapat dicatat sekali sebelum check-in, dengan nominal Rp 1–100.000.000.");
        }
        securityDeposits.put(code, new SecurityDeposit(amount, false));
        persist();
    }

    public void returnSecurityDeposit(String code) {
        SecurityDeposit deposit = securityDeposit(code);
        if (deposit == null || deposit.returned()) throw new IllegalArgumentException("Tidak ada jaminan yang perlu dikembalikan.");
        securityDeposits.put(code, new SecurityDeposit(deposit.amount(), true));
        persist();
    }

    public void cancel(String code) {
        Booking item = requireBooking(code);
        if (item.status() != Status.BOOKED) throw new IllegalArgumentException("Hanya reservasi berstatus Dipesan yang bisa dibatalkan.");
        SecurityDeposit deposit = securityDeposit(code);
        if (deposit != null && !deposit.returned()) throw new IllegalArgumentException("Kembalikan jaminan sebelum membatalkan reservasi.");
        setStatus(item, Status.CANCELLED);
    }

    public void checkIn(String code) {
        checkIn(code, "");
    }

    public String keycard(String code) { return keycards.getOrDefault(code, ""); }

    public void checkIn(String code, String keycard) {
        Booking item = requireBooking(code);
        if (item.status() != Status.BOOKED || item.arrival().isAfter(today()) || !item.departure().isAfter(today())) {
            throw new IllegalArgumentException("Check-in hanya tersedia untuk reservasi aktif dalam jadwal menginap.");
        }
        if (occupant(item.room().number()) != null) {
            throw new IllegalArgumentException("Tamu sebelumnya masih menginap di kamar ini.");
        }
        if (inventory.find(item.room().number()).condition() != Condition.READY) {
            throw new IllegalArgumentException("Kamar masih menunggu pembersihan atau perbaikan.");
        }
        if (!keycard.isBlank()) {
            if (!keycard.matches("[A-Za-z0-9-]{3,40}")) throw new IllegalArgumentException("Format ID keycard tidak valid.");
            if (bookings.stream().anyMatch(booking -> booking.status() == Status.IN_HOUSE && keycard.equalsIgnoreCase(keycard(booking.code())))) {
                throw new IllegalArgumentException("Keycard sedang digunakan oleh tamu lain.");
            }
            keycards.put(code, keycard);
        }
        stays.put(code, new Stay(today(), null));
        setStatus(item, Status.IN_HOUSE);
    }

    public void checkOut(String code) {
        Booking item = requireBooking(code);
        if (item.status() != Status.IN_HOUSE || remaining(item) > 0) {
            throw new IllegalArgumentException("Check-out membutuhkan tamu yang masih menginap dengan tagihan lunas.");
        }
        SecurityDeposit deposit = securityDeposit(code);
        if (deposit != null && !deposit.returned()) {
            throw new IllegalArgumentException("Catat pengembalian jaminan sebelum menyelesaikan check-out.");
        }
        Stay stay = stays.get(code);
        stays.put(code, new Stay(stay.checkIn(), today()));
        inventory.setCondition(item.room().number(), Condition.CLEANING);
        setStatus(item, Status.COMPLETE);
    }

    public void pay(String code, long amount, String method, String reference) {
        Booking item = requireBooking(code);
        if (item.status() == Status.CANCELLED || amount <= 0 || amount > remaining(item)) {
            throw new IllegalArgumentException("Nominal pembayaran harus sesuai dengan sisa tagihan aktif.");
        }
        if (method == null || method.isBlank()) {
            throw new IllegalArgumentException("Pilih metode pembayaran.");
        }
        String proof = reference == null ? "" : reference.strip();
        if (!method.equals("Tunai") && proof.isEmpty()) {
            throw new IllegalArgumentException("Masukkan nomor bukti transaksi untuk pembayaran nontunai.");
        }
        if (!proof.isEmpty() && receipts.stream().anyMatch(receipt -> receipt.reference().equalsIgnoreCase(proof))) {
            throw new IllegalArgumentException("Nomor bukti transaksi sudah tercatat.");
        }
        receipts.add(new Receipt(code, today(), amount, method, proof, false));
        persist();
    }

    /** Menyimpan seluruh reservasi, pembayaran, dan jaminan ke database setiap kali ada perubahan. */
    private void persist() {
        repository.save(new BookingRepository.Snapshot(
                bookings.stream().map(item -> {
                    Stay stay = stays.get(item.code());
                    HotelSettings.Pricing price = pricing(item.code());
                    return new BookingRepository.ReservationRow(item.code(), guestByBooking.get(item.code()),
                            item.room().number(), item.room().type(), item.room().rate(), item.arrival().toString(),
                            item.departure().toString(), item.status().name(), item.deposit(), item.notes(),
                            keycard(item.code()), stay == null ? null : stay.checkIn().toString(),
                            stay == null || stay.checkOut() == null ? null : stay.checkOut().toString(),
                            "%d,%d,%d,%d".formatted(price.hotelTax(), price.serviceCharge(), price.securityDeposit(),
                                    price.weekendSurcharge()));
                }).toList(),
                receipts.stream().map(item -> new BookingRepository.ReceiptRow(item.code(), item.date().toString(),
                        item.amount(), item.method(), item.reference(), item.deposit())).toList(),
                securityDeposits.entrySet().stream().map(entry -> new BookingRepository.DepositRow(entry.getKey(),
                        entry.getValue().amount(), entry.getValue().returned())).toList()));
    }

    private void restore() {
        BookingRepository.Snapshot saved = repository.load();
        for (BookingRepository.ReservationRow row : saved.reservations()) {
            Guest guest = guests.find(row.guestId());
            Room room = new Room(row.roomNumber(), row.roomType(), row.roomRate());
            bookings.add(new Booking(row.code(), guest == null ? "" : guest.name(), room, LocalDate.parse(row.arrival()),
                    LocalDate.parse(row.departure()), Status.valueOf(row.status()), row.deposit(), row.notes()));
            guestByBooking.put(row.code(), row.guestId());
            if (!row.keycard().isBlank()) keycards.put(row.code(), row.keycard());
            if (row.checkedInOn() != null) {
                stays.put(row.code(), new Stay(LocalDate.parse(row.checkedInOn()),
                        row.checkedOutOn() == null ? null : LocalDate.parse(row.checkedOutOn())));
            }
            if (row.pricing() != null) {
                String[] part = row.pricing().split(",");
                pricingByBooking.put(row.code(), new HotelSettings.Pricing(Integer.parseInt(part[0]),
                        Integer.parseInt(part[1]), Long.parseLong(part[2]), Integer.parseInt(part[3])));
            }
            if (row.code().matches("RSV-[0-9]+")) nextNumber = Math.max(nextNumber, Integer.parseInt(row.code().substring(4)) + 1);
        }
        for (BookingRepository.ReceiptRow row : saved.receipts()) {
            receipts.add(new Receipt(row.code(), LocalDate.parse(row.date()), row.amount(), row.method(), row.reference(), row.deposit()));
        }
        for (BookingRepository.DepositRow row : saved.deposits()) {
            securityDeposits.put(row.code(), new SecurityDeposit(row.amount(), row.returned()));
        }
    }

    private Booking requireBooking(String code) {
        Booking item = find(code);
        if (item == null) throw new IllegalArgumentException("Reservasi tidak ditemukan.");
        return item;
    }

    private void setStatus(Booking item, Status status) {
        bookings.set(bookings.indexOf(item), new Booking(item.code(), item.guest(), item.room(), item.arrival(),
                item.departure(), status, item.deposit(), item.notes()));
        persist();
    }

    private long paymentsFor(String code) {
        return receipts.stream().filter(receipt -> !receipt.deposit() && receipt.code().equals(code))
                .mapToLong(Receipt::amount).sum();
    }
}
