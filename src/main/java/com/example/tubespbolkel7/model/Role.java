package com.example.tubespbolkel7.model;

public enum Role {
    STAFF("Staf Administrasi"),
    OWNER("Owner"),
    MAINTENANCE("Pengurus Maintenance");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String label() { return label; }

    @Override
    public String toString() { return label; }
}
