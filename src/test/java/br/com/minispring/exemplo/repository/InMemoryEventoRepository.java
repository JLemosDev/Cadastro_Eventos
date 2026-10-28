package br.com.minispring.exemplo.repository;

import br.com.minispring.exemplo.model.Evento;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class InMemoryEventoRepository implements EventoRepository {

    private final ConcurrentHashMap<Long, Evento> events = new ConcurrentHashMap<>();

    private final AtomicLong sequence = new AtomicLong();

    public Evento create(String titulo, int participantes) {
        long id = sequence.incrementAndGet();
        var evento = new Evento(id, titulo, participantes);
        events.put(id, evento);
        return evento;
    }

    public List<Evento> findAll() {
        return events.values().stream().sorted(Comparator.comparingLong(Evento::id)).toList();
    }

    public Optional<Evento> findById(long id) {
        return Optional.ofNullable(events.get(id));
    }

    public Optional<Evento> update(long id, String titulo, int participantes) {
        return Optional.ofNullable(events.computeIfPresent(id, (key, old) -> new Evento(id, titulo, participantes)));
    }

    public boolean delete(long id) {
        return events.remove(id) != null;
    }
}
