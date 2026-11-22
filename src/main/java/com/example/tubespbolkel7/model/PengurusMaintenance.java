package com.example.tubespbolkel7.model;

public class PengurusMaintenance extends Pengguna {

    public PengurusMaintenance(String username, String nama) {
        super(username, nama, Role.MAINTENANCE);
    }

    @Override
    public String getDeskripsiTugas() {
        return "Menangani pemeliharaan dan kondisi fasilitas kamar hotel.";
    }
}
