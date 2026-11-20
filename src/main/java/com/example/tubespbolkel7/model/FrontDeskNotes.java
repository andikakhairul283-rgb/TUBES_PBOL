package com.example.tubespbolkel7.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class FrontDeskNotes {
    public record Note(String author, String room, String message, LocalDateTime created) { }

    private final List<Note> notes = new ArrayList<>();

    public List<Note> all() { return List.copyOf(notes); }

    public void send(String author, String room, String message) {
        String body = message.strip();
        if (body.isBlank() || body.length() > 500) {
            throw new IllegalArgumentException("Isi pesan hingga 500 karakter.");
        }
        notes.addFirst(new Note(author.isBlank() ? "Pengurus Maintenance" : author, room, body, LocalDateTime.now()));
    }
}
