package com.example.tubespbolkel7.report;

import com.example.tubespbolkel7.model.HotelData;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.example.tubespbolkel7.model.HotelData.Booking;
import static com.example.tubespbolkel7.model.HotelData.Receipt;
import static com.example.tubespbolkel7.model.HotelData.Room;
import static com.example.tubespbolkel7.model.HotelData.Status;

public final class ReportData {
    public static final String ALL_TYPES = "Semua tipe kamar";

    public record Filter(LocalDate start, LocalDate end, String roomType) {
        public static Filter currentMonth(LocalDate today) {
            YearMonth month = YearMonth.from(today);
            return new Filter(month.atDay(1), month.atEndOfMonth(), ALL_TYPES);
        }

        public void validate() {
            if (start == null || end == null || end.isBefore(start)) {
                throw new IllegalArgumentException("Pilih tanggal mulai dan selesai yang berurutan.");
            }
            if (ChronoUnit.DAYS.between(start, end) >= 366) {
                throw new IllegalArgumentException("Periode laporan maksimal 366 hari.");
            }
        }

        boolean includes(LocalDate date) {
            return !date.isBefore(start) && !date.isAfter(end);
        }

        boolean matches(Room room) {
            return ALL_TYPES.equals(roomType) || room.type().equals(roomType);
        }
    }

    public record Activity(Booking booking, boolean occupied, boolean checkIn, boolean checkOut) { }
    public record Transaction(Booking booking, Receipt receipt) { }

    public record Day(LocalDate date, List<Activity> activity, List<Transaction> transactions, int capacity) {
        public long occupied() {
            return activity.stream().filter(Activity::occupied).map(item -> item.booking().room().number()).distinct().count();
        }

        public long arrivals() { return activity.stream().filter(Activity::checkIn).count(); }
        public long departures() { return activity.stream().filter(Activity::checkOut).count(); }
        public double occupancy() { return percent(occupied(), capacity); }
        public long deposits() { return receipts(true); }
        public long payments() { return receipts(false); }
        public long total() { return deposits() + payments(); }

        private long receipts(boolean deposit) {
            return transactions.stream().map(Transaction::receipt).filter(receipt -> receipt.deposit() == deposit)
                    .mapToLong(Receipt::amount).sum();
        }
    }

    public record RoomType(String name, long rooms, long occupiedNights) { }

    public record Report(Filter filter, List<Day> days, List<RoomType> roomTypes, long reservations, long cancellations) {
        public double occupancy() {
            return percent(days.stream().mapToLong(Day::occupied).sum(), days.stream().mapToLong(Day::capacity).sum());
        }

        public long stays() {
            return days.stream().flatMap(day -> day.activity().stream()).filter(item -> item.occupied() || item.checkIn())
                    .map(item -> item.booking().code()).distinct().count();
        }

        public double nightsPerStay() {
            long count = stays();
            return count == 0 ? 0 : days.stream().mapToLong(Day::occupied).sum() / (double) count;
        }

        public long deposits() { return days.stream().mapToLong(Day::deposits).sum(); }
        public long payments() { return days.stream().mapToLong(Day::payments).sum(); }
        public long total() { return deposits() + payments(); }
    }

    public static Report calculate(HotelData data, Filter filter) {
        filter.validate();
        List<Booking> bookings = data.bookings().stream().filter(item -> filter.matches(item.room())).toList();
        List<Room> rooms = data.rooms().stream().filter(filter::matches).toList();
        Map<String, Booking> byCode = new HashMap<>();
        bookings.forEach(item -> byCode.put(item.code(), item));
        Map<LocalDate, List<Transaction>> transactions = new HashMap<>();
        for (Receipt receipt : data.receipts()) {
            Booking booking = byCode.get(receipt.code());
            if (booking != null && filter.includes(receipt.date())) {
                transactions.computeIfAbsent(receipt.date(), date -> new ArrayList<>()).add(new Transaction(booking, receipt));
            }
        }

        List<Day> days = new ArrayList<>();
        LocalDate today = data.today();
        LocalDate end = filter.end().isBefore(today) ? filter.end() : today;
        if (!filter.start().isAfter(end)) {
            filter.start().datesUntil(end.plusDays(1)).forEach(date -> {
                List<Activity> activities = new ArrayList<>();
                for (Booking booking : bookings) {
                    var stay = data.stay(booking.code());
                    if (stay == null) continue;
                    boolean checkIn = date.equals(stay.checkIn());
                    boolean checkOut = date.equals(stay.checkOut());
                    boolean occupied = !date.isBefore(stay.checkIn()) && (stay.checkOut() == null || date.isBefore(stay.checkOut()));
                    if (occupied || checkIn || checkOut) {
                        activities.add(new Activity(booking, occupied, checkIn, checkOut));
                    }
                }
                days.add(new Day(date, List.copyOf(activities),
                        List.copyOf(transactions.getOrDefault(date, List.of())), rooms.size()));
            });
        }
        List<RoomType> types = rooms.stream().map(Room::type).distinct().map(type -> {
            long count = rooms.stream().filter(room -> room.type().equals(type)).count();
            long nights = days.stream().mapToLong(day -> day.activity().stream()
                    .filter(item -> item.occupied() && item.booking().room().type().equals(type))
                    .map(item -> item.booking().room().number()).distinct().count()).sum();
            return new RoomType(type, count, nights);
        }).toList();
        long reservations = bookings.stream().filter(item -> filter.includes(item.arrival())).count();
        long cancelled = bookings.stream().filter(item -> item.status() == Status.CANCELLED && filter.includes(item.arrival())).count();
        return new Report(filter, List.copyOf(days), types, reservations, cancelled);
    }

    public static double percent(long value, long capacity) {
        return capacity == 0 ? 0 : value * 100.0 / capacity;
    }
}
