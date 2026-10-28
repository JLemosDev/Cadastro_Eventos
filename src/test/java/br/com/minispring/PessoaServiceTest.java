package br.com.minispring;

import br.com.minispring.exemplo.model.Pessoa;
import br.com.minispring.exemplo.model.PessoaInput;
import br.com.minispring.exemplo.repository.InMemoryEventoRepository;
import br.com.minispring.exemplo.repository.InMemoryPessoaRepository;
import br.com.minispring.exemplo.service.PessoaService;
import br.com.minispring.framework.web.HttpException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Testa o cadastro de pessoas isolado, injetando os repositórios pelo construtor. */
class PessoaServiceTest {

    private InMemoryPessoaRepository repository;
    private PessoaService service;
    private long eventoId;

    @BeforeEach
    void setUp() {
        var eventos = new InMemoryEventoRepository();
        eventoId = eventos.create("Festival de Música", 100).id();
        repository = new InMemoryPessoaRepository();
        service = new PessoaService(repository, eventos);
    }

    @Test
    void createsAndTrimsFields() {
        Pessoa pessoa = service.create(new PessoaInput("  Ana Souza  ", " ana@email.com ", eventoId));
        assertEquals(1, pessoa.id());
        assertEquals("Ana Souza", pessoa.nome());
        assertEquals("ana@email.com", pessoa.email());
        assertEquals(eventoId, pessoa.eventoId());
    }

    @Test
    void rejectsInvalidEmail() {
        for (String email : new String[] {"ana", "ana@", "@email.com", "ana email@x.com"}) {
            var error = assertThrows(HttpException.class, () -> service.create(new PessoaInput("Ana", email, eventoId)));
            assertEquals(400, error.status(), email);
        }
        assertEquals(0, repository.findAll().size());
    }

    @Test
    void rejectsNonexistentEvent() {
        var error = assertThrows(HttpException.class, () -> service.create(new PessoaInput("Ana", "ana@email.com", 999)));
        assertEquals(422, error.status());
        assertEquals(0, repository.findAll().size());
    }

    @Test
    void updatePreservesIdAndRejectedUpdateKeepsData() {
        Pessoa original = service.create(new PessoaInput("Ana", "ana@email.com", eventoId));
        Pessoa updated = service.update(original.id(), new PessoaInput("Ana Lima", "ana.lima@email.com", eventoId));
        assertEquals(original.id(), updated.id());
        assertEquals("Ana Lima", updated.nome());

        assertEquals(400, assertThrows(HttpException.class,
            () -> service.update(original.id(), new PessoaInput("Outra", "invalido", eventoId))).status());
        assertEquals(422, assertThrows(HttpException.class,
            () -> service.update(original.id(), new PessoaInput("Outra", "outra@email.com", 999))).status());
        assertEquals(updated, service.get(original.id()));
    }

    @Test
    void filtersByPartOfNameIgnoringCase() {
        service.create(new PessoaInput("Ana Souza", "ana@email.com", eventoId));
        service.create(new PessoaInput("Bruno Lima", "bruno@email.com", eventoId));
        assertEquals(1, service.list("ANA").size());
        assertEquals(2, service.list("").size());
        assertEquals(0, service.list("carlos").size());
    }

    @Test
    void missingPersonReturns404() {
        assertEquals(404, assertThrows(HttpException.class, () -> service.get(99)).status());
        assertEquals(404, assertThrows(HttpException.class, () -> service.delete(99)).status());
        assertEquals(404, assertThrows(HttpException.class,
            () -> service.update(99, new PessoaInput("X", "x@email.com", eventoId))).status());
    }
}
