package com.example.tubespbolkel7.model;

import javafx.scene.image.Image;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HotelSettings {
    public record Hotel(String name, String phone, String email, String city, String address, LocalTime arrival,
                 LocalTime departure, Image logo) { }
    public record Policy(boolean verifyIdentity, String checkInNote, String cancellationNote) { }
    public record Profile(String displayName, String email) { }
    public record Login(String username, Role role, LocalDateTime when) { }
    public record Pricing(int hotelTax, int serviceCharge, long securityDeposit, int weekendSurcharge) {
        public int combinedPercent() { return hotelTax + serviceCharge; }
        public long weekendRate(long base) { return base + Math.round(base * weekendSurcharge / 100.0); }
    }

    private Hotel hotel = new Hotel("Grand Merdeka Hotel", "", "", "Jakarta", "", LocalTime.of(14, 0), LocalTime.NOON, null);
    private Policy policy = new Policy(true, "", "");
    private Pricing pricing = new Pricing(11, 0, 0, 0);
    private final UserAccounts accounts = new UserAccounts();
    private final Map<String, Profile> profiles = new LinkedHashMap<>();
    private final Map<String, Login> logins = new LinkedHashMap<>();

    public Hotel hotel() { return hotel; }
    public Policy policy() { return policy; }
    public Pricing pricing() { return pricing; }
    public UserAccounts accounts() { return accounts; }
    public List<Login> logins() { return List.copyOf(logins.values()).reversed(); }

    public void savePricing(Pricing next) {
        if (next.hotelTax() < 0 || next.serviceCharge() < 0 || next.combinedPercent() > 100
                || next.weekendSurcharge() < 0 || next.weekendSurcharge() > 100
                || next.securityDeposit() < 0 || next.securityDeposit() > 100_000_000) {
            throw new IllegalArgumentException("Jumlah pajak dan biaya layanan maksimal 100%, kenaikan tarif 0–100%, dan jaminan Rp 0–100.000.000.");
        }
        pricing = next;
    }

    public void saveHotel(Hotel next) {
        if (next.name().isBlank()) throw new IllegalArgumentException("Nama hotel wajib diisi.");
        checkEmail(next.email());
        hotel = next;
    }

    public void savePolicy(Policy next) {
        if (next.checkInNote().length() > 500 || next.cancellationNote().length() > 500) {
            throw new IllegalArgumentException("Setiap catatan kebijakan maksimal 500 karakter.");
        }
        policy = next;
    }

    public Profile profile(String username) {
        UserAccounts.Account account = accounts.find(username);
        return profiles.getOrDefault(username.toLowerCase(java.util.Locale.ROOT),
                new Profile(account == null ? username : account.name(), ""));
    }

    public void saveProfile(String username, Profile next) {
        if (username.isBlank() || next.displayName().isBlank()) throw new IllegalArgumentException("Nama tampilan wajib diisi.");
        checkEmail(next.email());
        profiles.put(username.toLowerCase(java.util.Locale.ROOT), next);
    }

    public void recordLogin(String username, Role role) {
        String key = role.name() + ":" + username;
        logins.remove(key);
        logins.put(key, new Login(username, role, LocalDateTime.now()));
    }

    public static void checkEmail(String value) {
        if (!value.isBlank() && !value.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            throw new IllegalArgumentException("Format email tidak valid.");
        }
    }
}
