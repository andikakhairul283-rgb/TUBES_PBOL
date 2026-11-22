package com.example.tubespbolkel7.model;

public abstract class Pengguna {

    private final String username;
    private final String nama;
    private final Role role;

    protected Pengguna(String username, String nama, Role role) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username tidak boleh kosong.");
        }

        if (nama == null || nama.isBlank()) {
            throw new IllegalArgumentException("Nama pengguna tidak boleh kosong.");
        }

        this.username = username;
        this.nama = nama;
        this.role = role;
    }

    public String getUsername() {
        return username;
    }

    public String getNama() {
        return nama;
    }

    public Role getRole() {
        return role;
    }

    public abstract String getDeskripsiTugas();
}
