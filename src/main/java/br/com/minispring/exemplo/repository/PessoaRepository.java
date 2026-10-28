package br.com.minispring.exemplo.repository;

import br.com.minispring.exemplo.model.Pessoa;

import java.util.List;
import java.util.Optional;

/**
 * A aplicação depende de uma abstração; o container encontra a implementação.
 */
public interface PessoaRepository {
    Pessoa create(String nome, String email, long eventoId);

    List<Pessoa> findAll();

    Optional<Pessoa> findById(long id);

    Optional<Pessoa> update(long id, String nome, String email, long eventoId);

    boolean delete(long id);
    default long countByEvento(long eventoId) { return findAll().stream().filter(p -> p.eventoId() == eventoId).count(); }
}
