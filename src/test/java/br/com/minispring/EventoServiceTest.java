package br.com.minispring;

import br.com.minispring.exemplo.model.Evento;
import br.com.minispring.exemplo.model.EventoInput;
import br.com.minispring.exemplo.repository.EventoRepository;
import br.com.minispring.exemplo.service.EventoService;
import br.com.minispring.framework.web.HttpException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Testa a regra de negócio isolada, injetando um repositório falso pelo construtor. */
class EventoServiceTest {

    private FakeRepository repository;
    private EventoService service;

    @BeforeEach
    void setUp() {
        repository = new FakeRepository();
        service = new EventoService(repository, new br.com.minispring.exemplo.repository.InMemoryPessoaRepository());
    }

    @Test
    void createsAndTrimsTitle() {
        Evento evento = service.create(new EventoInput("  Sarau  ", 50));
        assertEquals(1, evento.id());
        assertEquals("Sarau", evento.titulo());
    }

    @Test
    void acceptsLimitAndRejectsAboveIt() {
        assertEquals(5000, service.create(new EventoInput("Festival", 5000)).participantes());
        var error = assertThrows(HttpException.class, () -> service.create(new EventoInput("Festival", 5001)));
        assertEquals(422, error.status());
        assertEquals(1, repository.findAll().size());
    }

    @Test
    void rejectedUpdateKeepsStoredEvent() {
        Evento original = service.create(new EventoInput("Mostra", 100));
        var error = assertThrows(HttpException.class, () -> service.update(original.id(), new EventoInput("Outro", 6000)));
        assertEquals(422, error.status());
        assertEquals(original, service.get(original.id()));
    }

    @Test
    void updatePreservesId() {
        Evento original = service.create(new EventoInput("Mostra", 100));
        Evento updated = service.update(original.id(), new EventoInput("Mostra de Teatro", 200));
        assertEquals(original.id(), updated.id());
        assertEquals(200, updated.participantes());
    }

    @Test
    void filtersByPartOfTitleIgnoringCase() {
        service.create(new EventoInput("Festival de Música", 10));
        service.create(new EventoInput("Mostra de Teatro", 10));
        assertEquals(1, service.list("MÚS").size());
        assertEquals(2, service.list("").size());
        assertEquals(0, service.list("cinema").size());
    }

    @Test
    void missingEventReturns404() {
        assertEquals(404, assertThrows(HttpException.class, () -> service.get(99)).status());
        assertEquals(404, assertThrows(HttpException.class, () -> service.delete(99)).status());
        assertEquals(404, assertThrows(HttpException.class,
            () -> service.update(99, new EventoInput("X", 1))).status());
    }

    private static final class FakeRepository implements EventoRepository {
        private final Map<Long, Evento> data = new TreeMap<>();
        private long sequence;

        public Evento create(String titulo, int participantes) {
            var evento = new Evento(++sequence, titulo, participantes);
            data.put(evento.id(), evento);
            return evento;
        }

        public List<Evento> findAll() { return new ArrayList<>(data.values()); }

        public Optional<Evento> findById(long id) { return Optional.ofNullable(data.get(id)); }

        public Optional<Evento> update(long id, String titulo, int participantes) {
            if (!data.containsKey(id)) return Optional.empty();
            var evento = new Evento(id, titulo, participantes);
            data.put(id, evento);
            return Optional.of(evento);
        }

        public boolean delete(long id) { return data.remove(id) != null; }
    }
}
