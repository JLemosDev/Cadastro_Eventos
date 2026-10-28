package br.com.minispring;

import br.com.minispring.exemplo.repository.*;
import br.com.minispring.exemplo.model.*;
import br.com.minispring.framework.web.HttpException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class DatabaseTest {
    @TempDir Path directory;
    private Database open() { return new Database(directory.toString()); }

    @Test void migratesJsonPreservingIdsSequencesAndNeverImportsTwice() throws Exception {
        String events = """
            {"sequence":70,"items":[{"id":4,"titulo":"Música","participantes":10}]}
            """;
        Files.writeString(directory.resolve("eventos.json"), events);
        Files.writeString(directory.resolve("pessoas.json"), """
            {"sequence":80,"items":[{"id":7,"nome":"Ana","email":"ana@example.com","eventoId":4}]}
            """);
        var db = open(); var repo = new SqliteEventoRepository(db); var people = new SqlitePessoaRepository(db);
        assertEquals(1, repo.findById(4).orElseThrow().inscritos());
        assertEquals(10, repo.findById(4).orElseThrow().capacidade());
        assertEquals(71, repo.create("Novo", 5).id());
        assertEquals(81, people.create("Bia", "bia@example.com", 4).id());
        people.delete(7); people.delete(81); repo.delete(4);
        assertTrue(new SqliteEventoRepository(open()).findById(4).isEmpty());
        assertEquals(events, Files.readString(directory.resolve("eventos.json")));
    }

    @Test void failedMigrationRollsBackAndCanBeRetried() throws Exception {
        Files.writeString(directory.resolve("eventos.json"), """
            {"sequence":3,"items":[{"id":3,"titulo":"Evento","participantes":5}]}
            """);
        Files.writeString(directory.resolve("pessoas.json"), """
            {"sequence":1,"items":[{"id":1,"nome":"Ana","email":"ana@example.com","eventoId":99}]}
            """);
        assertThrows(IllegalStateException.class, this::open);
        Files.writeString(directory.resolve("pessoas.json"), "{\"sequence\":1,\"items\":[]}");
        assertEquals(1, new SqliteEventoRepository(open()).findAll().size());
    }

    @Test void persistsMetadataAndProtectsLinksAndCapacityAtDatabaseLevel() {
        var db = open(); var events = new SqliteEventoRepository(db); var people = new SqlitePessoaRepository(db);
        var event = events.create(new EventoInput("Sarau", 1, "2027-06-01", "19:30", "Auditório", "Literatura", "Descrição"));
        var person = people.create("Ana", "ana@example.com", event.id());
        assertEquals(409, assertThrows(HttpException.class, () -> events.delete(event.id())).status());
        assertEquals(409, assertThrows(HttpException.class, () -> people.create("Bia", "bia@example.com", event.id())).status());
        assertEquals(person.id(), people.update(person.id(), "Ana Maria", "ana@example.com", event.id()).orElseThrow().id());
        var restored = new SqliteEventoRepository(open()).findById(event.id()).orElseThrow();
        assertEquals("2027-06-01", restored.data()); assertEquals("19:30", restored.horario()); assertEquals(1, restored.inscritos());
        assertEquals("Descrição", restored.descricao());
        people.delete(person.id()); assertTrue(events.delete(event.id()));
    }

    @Test void capacityReductionAndTransferAreAtomic() {
        var db = open(); var events = new SqliteEventoRepository(db); var people = new SqlitePessoaRepository(db);
        long first = events.create("Primeiro", 2).id(), second = events.create("Segundo", 1).id();
        var ana = people.create("Ana", "ana@example.com", first);
        people.create("Bia", "bia@example.com", first); people.create("Caio", "caio@example.com", second);
        assertEquals(409, assertThrows(HttpException.class, () -> events.update(first, "Menor", 1)).status());
        assertEquals(409, assertThrows(HttpException.class, () -> people.update(ana.id(), "Ana", "ana@example.com", second)).status());
        assertEquals(first, people.findById(ana.id()).orElseThrow().eventoId());
        assertEquals(2, events.findById(first).orElseThrow().capacidade());
    }

    @Test void concurrentEnrollmentsCannotOverbook() throws Exception {
        var db = open(); var events = new SqliteEventoRepository(db); var people = new SqlitePessoaRepository(db);
        long id = events.create("Limitado", 3).id();
        try (var executor = Executors.newFixedThreadPool(8)) {
            var tasks = new java.util.ArrayList<Future<Boolean>>();
            for (int i=0; i<20; i++) tasks.add(executor.submit(() -> {
                try { people.create("Teste", "teste@example.com", id); return true; }
                catch (HttpException e) { assertEquals(409, e.status()); return false; }
            }));
            int accepted = 0; for (var task : tasks) if (task.get()) accepted++;
            assertEquals(3, accepted); assertEquals(3, people.countByEvento(id));
        }
    }

    @Test void paginatesFiltersAccentsCategoriesAndDatesInSql() {
        var events = new SqliteEventoRepository(open());
        for (int i=0; i<31; i++) events.create(new EventoInput("Música " + i, 10, "2099-01-01", "", "", "Arte", ""));
        events.create(new EventoInput("Passado", 10, "2000-01-01", "", "", "Teatro", ""));
        events.create("Sem data", 1);
        var page = events.page("musica", 2, 10, "id", "arte", "futuros");
        assertEquals(31, page.total()); assertEquals(10, page.items().size()); assertEquals(11, page.items().getFirst().id());
        assertEquals(4, page.pages()); assertEquals(33, page.resumo().eventos());
        assertEquals(1, events.page("", 1, 25, "data", "", "passados").total());
        assertEquals(1, events.page("", 1, 25, "id", "", "sem-data").total());
        assertEquals(4, events.page("musica", 999, 10, "id", "", "").page());
    }
}
