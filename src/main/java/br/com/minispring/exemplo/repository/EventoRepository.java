package br.com.minispring.exemplo.repository;

import br.com.minispring.exemplo.model.Evento;
import br.com.minispring.exemplo.model.EventoInput;
import br.com.minispring.exemplo.model.Pagina;

import java.util.List;
import java.util.Optional;

/**
 * A aplicação depende de uma abstração; o container encontra a implementação.
 */
public interface EventoRepository {
    Evento create(String titulo, int participantes);

    List<Evento> findAll();

    Optional<Evento> findById(long id);

    Optional<Evento> update(long id, String titulo, int participantes);

    boolean delete(long id);

    default Evento create(EventoInput input) { return create(input.titulo(), input.capacidade()); }
    default Optional<Evento> update(long id, EventoInput input) { return update(id, input.titulo(), input.capacidade()); }
    default Pagina<Evento> page(String titulo, int page, int size, String sort, String categoria, String periodo) {
        throw new UnsupportedOperationException("Paginação disponível no repositório SQLite");
    }
}
