package com.example.tubespbolkel7.model;

public class AdminResepsionis extends Pengguna {

    public AdminResepsionis(String username, String nama) {
        super(username, nama, Role.STAFF);
    }

    @Override
    public String getDeskripsiTugas() {
        return "Mengelola administrasi dan operasional resepsionis hotel.";
    }
}
