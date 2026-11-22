
package com.example.tubespbolkel7.model;

import com.example.tubespbolkel7.db.Database;
import com.example.tubespbolkel7.db.HotelSettingsRepository;

import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;

import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class HotelSettings {

    public record Hotel(
            String name,
            String phone,
            String email,
            String city,
            String address,
            LocalTime arrival,
            LocalTime departure,
            Image logo
    ) { }

    public record Policy(
            boolean verifyIdentity,
            String checkInNote,
            String cancellationNote
    ) { }

    public record Profile(String displayName, String email) { }

    public record Login(String username, Role role, LocalDateTime when) { }

    public record Pricing(
            int hotelTax,
            int serviceCharge,
            long securityDeposit,
            int weekendSurcharge
    ) {
        public int combinedPercent() {
            return hotelTax + serviceCharge;
        }

        public long weekendRate(long base) {
            return base + Math.round(base * weekendSurcharge / 100.0);
        }
    }

    private Hotel hotel = new Hotel(
            "Grand Merdeka Hotel", "", "", "Jakarta", "",
            LocalTime.of(14, 0), LocalTime.NOON, null
    );

    private Policy policy = new Policy(true, "", "");
    private Pricing pricing = new Pricing(11, 0, 0, 0);

    private final UserAccounts accounts = new UserAccounts();
    private final Map<String, Profile> profiles = new LinkedHashMap<>();
    private final Map<String, Login> logins = new LinkedHashMap<>();

    private final HotelSettingsRepository repository =
            new HotelSettingsRepository(Database.shared());

    public HotelSettings() {
        restore();
    }

    public Hotel hotel() {
        return hotel;
    }

    public Policy policy() {
        return policy;
    }

    public Pricing pricing() {
        return pricing;
    }

    public UserAccounts accounts() {
        return accounts;
    }

    public List<Login> logins() {
        return List.copyOf(logins.values()).reversed();
    }

    private void restore() {
        HotelSettingsRepository.HotelRow savedHotel = repository.loadHotel();

        if (savedHotel != null) {
            hotel = new Hotel(
                    savedHotel.name(),
                    savedHotel.phone(),
                    savedHotel.email(),
                    savedHotel.city(),
                    savedHotel.address(),
                    LocalTime.parse(savedHotel.arrival()),
                    LocalTime.parse(savedHotel.departure()),
                    decodeLogo(savedHotel.logo())
            );
}

        HotelSettingsRepository.PolicyRow savedPolicy = repository.loadPolicy();

        if (savedPolicy != null) {
            policy = new Policy(
                    savedPolicy.verifyIdentity(),
                    savedPolicy.checkInNote(),
                    savedPolicy.cancellationNote()
            );
        }

        HotelSettingsRepository.PricingRow savedPricing = repository.loadPricing();

        if (savedPricing != null) {
            pricing = new Pricing(
                    savedPricing.hotelTax(),
                    savedPricing.serviceCharge(),
                    savedPricing.securityDeposit(),
                    savedPricing.weekendSurcharge()
            );
        }
    }

public void savePricing(Pricing next) {
        if (next.hotelTax() < 0
                || next.serviceCharge() < 0
                || next.combinedPercent() > 100
                || next.weekendSurcharge() < 0
                || next.weekendSurcharge() > 100
                || next.securityDeposit() < 0
                || next.securityDeposit() > 100_000_000) {

            throw new IllegalArgumentException(
                    "Jumlah pajak dan biaya layanan maksimal 100%, "
                            + "kenaikan tarif 0–100%, dan jaminan Rp 0–100.000.000."
            );
        }

        repository.savePricing(new HotelSettingsRepository.PricingRow(                next.hotelTax(),
                next.serviceCharge(),
                next.securityDeposit(),
                next.weekendSurcharge()
        ));

        pricing = next;
    }

    public void saveHotel(Hotel next) {
        if (next.name().isBlank()) {
            throw new IllegalArgumentException("Nama hotel wajib diisi.");
        }

        checkEmail(next.email());

        byte[] logo = encodeLogo(next.logo());

if (logo == null) {
    HotelSettingsRepository.HotelRow savedHotel = repository.loadHotel();
    logo = savedHotel == null ? null : savedHotel.logo();
}

repository.saveHotel(new HotelSettingsRepository.HotelRow(
        next.name(),
        next.phone(),
        next.email(),
        next.city(),
        next.address(),
        next.arrival().toString(),
        next.departure().toString(),
        logo
));

        hotel = next;
    }

    public void savePolicy(Policy next) {
        if (next.checkInNote().length() > 500
                || next.cancellationNote().length() > 500) {

            throw new IllegalArgumentException(
                    "Setiap catatan kebijakan maksimal 500 karakter."
            );        }

        repository.savePolicy(new HotelSettingsRepository.PolicyRow(
                next.verifyIdentity(),
                next.checkInNote(),
                next.cancellationNote()
        ));

        policy = next;
    }

    public Profile profile(String username) {
    String key = username.toLowerCase(Locale.ROOT);

    Profile cached = profiles.get(key);

    if (cached != null) {
        return cached;
    }

    HotelSettingsRepository.ProfileRow saved =
            repository.loadProfile(username);

    if (saved != null) {
        Profile profile = new Profile(
                saved.displayName(),
                saved.email()
        );

        profiles.put(key, profile);
        return profile;
    }

    UserAccounts.Account account = accounts.find(username);

    return new Profile(
            account == null ? username : account.name(),
            ""
    );
}

    public void saveProfile(String username, Profile next) {
        if (username.isBlank() || next.displayName().isBlank()) {
            throw new IllegalArgumentException("Nama tampilan wajib diisi.");
        }

        checkEmail(next.email());

        repository.saveProfile(new HotelSettingsRepository.ProfileRow(
                username,
                next.displayName(),
                next.email()
        ));

        profiles.put(username.toLowerCase(Locale.ROOT), next);
    }

    public void recordLogin(String username, Role role) {
        String key = role.name() + ":" + username;

        logins.remove(key);
        logins.put(key, new Login(username, role, LocalDateTime.now()));
    }

    private static byte[] encodeLogo(Image image) {
        if (image == null || image.getPixelReader() == null) {
            return null;
        }

        int width = (int) Math.round(image.getWidth());
        int height = (int) Math.round(image.getHeight());

        if (width <= 0 || height <= 0
                || (long) width * height > 4_000_000) {
            throw new IllegalArgumentException("Ukuran logo tidak valid.");
        }

        PixelReader reader = image.getPixelReader();

        ByteBuffer buffer = ByteBuffer.allocate(
                8 + width * height * Integer.BYTES
        );

        buffer.putInt(width);
        buffer.putInt(height);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                buffer.putInt(reader.getArgb(x, y));
            }
        }

        return buffer.array();
    }

    private static Image decodeLogo(byte[] data) {
        if (data == null || data.length < 8) {
            return null;
        }

        try {
            ByteBuffer buffer = ByteBuffer.wrap(data);

            int width = buffer.getInt();
            int height = buffer.getInt();

            if (width <= 0 || height <= 0
                    || (long) width * height > 4_000_000
                    || data.length != 8L + (long) width * height * Integer.BYTES) {
                return null;
            }

            WritableImage image = new WritableImage(width, height);

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    image.getPixelWriter().setArgb(
                            x, y, buffer.getInt()
                    );
                }
            }

            return image;

        } catch (RuntimeException failure) {
            return null;
        }
    }

    public static void checkEmail(String value) {
        if (!value.isBlank()
                && !value.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {

            throw new IllegalArgumentException("Format email tidak valid.");
        }
    }
}
