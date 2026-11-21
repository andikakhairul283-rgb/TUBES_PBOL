package com.example.tubespbolkel7.model;

import com.example.tubespbolkel7.db.Database;
import com.example.tubespbolkel7.db.FrontDeskRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class FrontDeskNotes {
    public record Note(String author, String room, String message, LocalDateTime created) { }

    private final List<Note> notes = new ArrayList<>();
    private final FrontDeskRepository repository;

    public FrontDeskNotes() { this(new FrontDeskRepository(Database.shared())); }

    public FrontDeskNotes(FrontDeskRepository repository) {
        this.repository = repository;
        for (FrontDeskRepository.Row row : repository.loadNewestFirst()) {
            notes.add(new Note(row.author(), row.room(), row.message(), LocalDateTime.parse(row.created())));
        }
    }

    public List<Note> all() { return List.copyOf(notes); }

    public void send(String author, String room, String message) {
        String body = message.strip();
        if (body.isBlank() || body.length() > 500) {
            throw new IllegalArgumentException("Isi pesan hingga 500 karakter.");
        }
        Note note = new Note(author.isBlank() ? "Pengurus Maintenance" : author, room, body, LocalDateTime.now());
        repository.add(new FrontDeskRepository.Row(note.author(), note.room(), note.message(), note.created().toString()));
        notes.addFirst(note);
    }
}
