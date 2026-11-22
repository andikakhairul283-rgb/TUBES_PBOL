
package com.example.tubespbolkel7.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class HotelSettingsRepository {

    public record HotelRow(
            String name,
            String phone,
            String email,
            String city,
            String address,
            String arrival,
            String departure,
            byte[] logo
    ) { }

    public record PolicyRow(
            boolean verifyIdentity,
            String checkInNote,
            String cancellationNote
    ) { }

    public record PricingRow(
            int hotelTax,
            int serviceCharge,
            long securityDeposit,
            int weekendSurcharge
    ) { }

    public record ProfileRow(
            String username,
            String displayName,
            String email
    ) { }

    private final Database database;

    public HotelSettingsRepository(Database database) {
        this.database = database;
    }

    public HotelRow loadHotel() {
        String sql = """
                SELECT name, phone, email, city, address,
                       arrival, departure, logo
                FROM hotel_settings
                WHERE id = 1
                """;

        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {

            if (!result.next()) return null;

            return new HotelRow(
                    result.getString("name"),
                    result.getString("phone"),
                    result.getString("email"),
                    result.getString("city"),
                    result.getString("address"),
                    result.getString("arrival"),
                    result.getString("departure"),
                    result.getBytes("logo")
            );

        } catch (SQLException failure) {
            throw databaseError("memuat informasi hotel", failure);
        }
    }

    public void saveHotel(HotelRow hotel) {
        String sql = """
                INSERT INTO hotel_settings
                    (id, name, phone, email, city, address,
                     arrival, departure, logo)
                VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    name = excluded.name,
                    phone = excluded.phone,
                    email = excluded.email,
                    city = excluded.city,
                    address = excluded.address,
                    arrival = excluded.arrival,
                    departure = excluded.departure,
                    logo = excluded.logo
                """;

        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, hotel.name());
            statement.setString(2, hotel.phone());
            statement.setString(3, hotel.email());
            statement.setString(4, hotel.city());
            statement.setString(5, hotel.address());
            statement.setString(6, hotel.arrival());
            statement.setString(7, hotel.departure());
            statement.setBytes(8, hotel.logo());

            statement.executeUpdate();

        } catch (SQLException failure) {
            throw databaseError("menyimpan informasi hotel", failure);
        }
    }

    public PolicyRow loadPolicy() {
        String sql = """
                SELECT verify_identity, check_in_note, cancellation_note
                FROM hotel_policies
                WHERE id = 1
                """;

        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {

            if (!result.next()) return null;

            return new PolicyRow(
                    result.getInt("verify_identity") == 1,
                    result.getString("check_in_note"),
                    result.getString("cancellation_note")
            );

        } catch (SQLException failure) {
            throw databaseError("memuat kebijakan hotel", failure);
        }
    }

    public void savePolicy(PolicyRow policy) {
        String sql = """
                INSERT INTO hotel_policies
                    (id, verify_identity, check_in_note, cancellation_note)
                VALUES (1, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    verify_identity = excluded.verify_identity,
                    check_in_note = excluded.check_in_note,
                    cancellation_note = excluded.cancellation_note
                """;

        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, policy.verifyIdentity() ? 1 : 0);
            statement.setString(2, policy.checkInNote());
            statement.setString(3, policy.cancellationNote());

            statement.executeUpdate();

        } catch (SQLException failure) {
            throw databaseError("menyimpan kebijakan hotel", failure);
        }
    }

    public PricingRow loadPricing() {
        String sql = """
                SELECT hotel_tax, service_charge,
                       security_deposit, weekend_surcharge
                FROM hotel_pricing
                WHERE id = 1
                """;

        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {

            if (!result.next()) return null;

            return new PricingRow(
                    result.getInt("hotel_tax"),
                    result.getInt("service_charge"),
                    result.getLong("security_deposit"),
                    result.getInt("weekend_surcharge")
            );

        } catch (SQLException failure) {
            throw databaseError("memuat pengaturan tarif", failure);
        }
    }

    public void savePricing(PricingRow pricing) {
        String sql = """
                INSERT INTO hotel_pricing
                    (id, hotel_tax, service_charge,
                     security_deposit, weekend_surcharge)
                VALUES (1, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    hotel_tax = excluded.hotel_tax,
                    service_charge = excluded.service_charge,
                    security_deposit = excluded.security_deposit,
                    weekend_surcharge = excluded.weekend_surcharge
                """;

        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, pricing.hotelTax());
            statement.setInt(2, pricing.serviceCharge());
            statement.setLong(3, pricing.securityDeposit());
            statement.setInt(4, pricing.weekendSurcharge());

            statement.executeUpdate();

        } catch (SQLException failure) {
            throw databaseError("menyimpan pengaturan tarif", failure);
        }
    }

    public ProfileRow loadProfile(String username) {
        String sql = """
                SELECT username, display_name, email
                FROM user_profiles
                WHERE username = ?
                """;

        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, username);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return null;

                return new ProfileRow(
                        result.getString("username"),
                        result.getString("display_name"),
                        result.getString("email")
                );
            }

        } catch (SQLException failure) {
            throw databaseError("memuat profil pengguna", failure);
        }
    }

    public void saveProfile(ProfileRow profile) {
        String sql = """
                INSERT INTO user_profiles (username, display_name, email)
                VALUES (?, ?, ?)
                ON CONFLICT(username) DO UPDATE SET
                    display_name = excluded.display_name,
                    email = excluded.email
                """;

        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, profile.username());
            statement.setString(2, profile.displayName());
            statement.setString(3, profile.email());

            statement.executeUpdate();

        } catch (SQLException failure) {
            throw databaseError("menyimpan profil pengguna", failure);
        }
    }

    private static IllegalStateException databaseError(
            String operation, SQLException failure) {
        return new IllegalStateException(
                "Gagal " + operation + ": " + failure.getMessage(),
                failure
        );
    }
}
