package com.example.tubespbolkel7.model;

public class Owner extends Pengguna {

    public Owner(String username, String nama) {
        super(username, nama, Role.OWNER);
    }

    @Override
    public String getDeskripsiTugas() {
        return "Mengelola dan memantau keseluruhan operasional hotel.";
    }
}
