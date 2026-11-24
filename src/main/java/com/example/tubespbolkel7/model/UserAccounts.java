package com.example.tubespbolkel7.model;

import com.example.tubespbolkel7.db.Database;
import com.example.tubespbolkel7.db.UserRepository;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class UserAccounts {
    public record Account(String username, String name, Role role, boolean enabled) { }
    private record Credential(byte[] salt, byte[] hash) { }
    private record Entry(Account account, Credential credential) { }

    private static final SecureRandom RANDOM = new SecureRandom();
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final UserRepository repository;

    public UserAccounts() { this(new UserRepository(Database.shared())); }

    public UserAccounts(UserRepository repository) {
        this.repository = repository;
        for (UserRepository.Row row : repository.loadAll()) {
            Account account = new Account(row.username(), row.name(), Role.valueOf(row.role()), row.enabled());
            entries.put(key(row.username()), new Entry(account, new Credential(row.salt(), row.hash())));
        }
    }

    public List<Account> all() { return entries.values().stream().map(Entry::account).toList(); }

    public Account find(String username) {
        Entry entry = entries.get(key(username));
        return entry == null ? null : entry.account();
    }

    public boolean accepts(String username, String password, Role role) {
        Entry entry = entries.get(key(username));
        if (entry == null) return false;
        Account account = entry.account();
        return account.enabled() && account.role() == role && verifies(entry.credential(), password);
    }

    public boolean signIn(String username, String password, Role role) {
        if (find(username) == null) {
            create(username, username, role, true, password);
            return true;
        }
        return accepts(username, password, role);
    }

    public void create(String username, String name, Role role, boolean enabled, String password) {
        if (!username.matches("[a-zA-Z0-9._-]{3,40}")) {
            throw new IllegalArgumentException("Username harus 3–40 karakter berupa huruf, angka, titik, garis bawah, atau tanda hubung.");
        }
        if (entries.containsKey(key(username))) throw new IllegalArgumentException("Username sudah terdaftar.");
        validateProfile(name, role);
        store(new Entry(new Account(username, name, role, enabled), credential(password)));
    }

    public void update(String username, String name, Role role, boolean enabled, String actor) {
        Entry entry = require(username);
        validateProfile(name, role);
        if (key(username).equals(key(actor)) && (!enabled || role != Role.OWNER)) {
            throw new IllegalArgumentException("Akun Owner yang sedang digunakan harus tetap aktif sebagai Owner.");
        }
        store(new Entry(new Account(entry.account().username(), name, role, enabled), entry.credential()));
    }

    void delete(String username, String actor) {
        Entry owner = require(actor);
        if (owner.account().role() != Role.OWNER || !owner.account().enabled()) {
            throw new IllegalArgumentException("Hanya Owner aktif yang bisa menghapus akun.");
        }
        if (key(username).equals(key(actor))) {
            throw new IllegalArgumentException("Kamu tidak bisa menghapus akun yang sedang kamu gunakan.");
        }
        Account account = require(username).account();
        repository.delete(account.username());
        entries.remove(key(account.username()));
    }

    public void resetPassword(String username, String password) {
        Entry entry = require(username);
        store(new Entry(entry.account(), credential(password)));
    }

    public void changePassword(String username, String oldPassword, String newPassword) {
        Entry entry = require(username);
        if (!verifies(entry.credential(), oldPassword)) throw new IllegalArgumentException("Kata sandi lama salah.");
        resetPassword(username, newPassword);
    }

    /** Simpan ke database lebih dulu, baru ke memori, agar keduanya tetap sama bila penyimpanan gagal. */
    private void store(Entry entry) {
        Account account = entry.account();
        repository.save(new UserRepository.Row(account.username(), account.name(), account.role().name(),
                account.enabled(), entry.credential().salt(), entry.credential().hash()));
        entries.put(key(account.username()), entry);
    }

    private Entry require(String username) {
        Entry entry = entries.get(key(username));
        if (entry == null) throw new IllegalArgumentException("Akun tidak terdaftar.");
        return entry;
    }

    private static String key(String username) { return username.toLowerCase(Locale.ROOT); }

    private static void validateProfile(String name, Role role) {
        if (name.isBlank() || name.length() > 100 || role == null) {
            throw new IllegalArgumentException("Isi nama tampilan (maksimal 100 karakter) dan pilih peran.");
        }
    }

    private static Credential credential(String password) {
        if (password.isBlank() || password.length() < 8 || password.length() > 128) {
            throw new IllegalArgumentException("Gunakan kata sandi 8–128 karakter.");
        }
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return new Credential(salt, derive(password, salt));
    }

    private static boolean verifies(Credential expected, String password) {
        return password.length() <= 128 && MessageDigest.isEqual(expected.hash(), derive(password, expected.salt()));
    }

    private static byte[] derive(String password, byte[] salt) {
        char[] chars = password.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(chars, salt, 210_000, 256);
        java.util.Arrays.fill(chars, '\0');
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("Pemeriksaan kata sandi tidak tersedia.", failure);
        } finally {
            spec.clearPassword();
        }
    }
}
