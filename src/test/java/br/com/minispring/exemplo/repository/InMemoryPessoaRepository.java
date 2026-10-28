package br.com.minispring.exemplo.repository;

import br.com.minispring.exemplo.model.Pessoa;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class InMemoryPessoaRepository implements PessoaRepository {

    private final ConcurrentHashMap<Long, Pessoa> pessoas = new ConcurrentHashMap<>();

    private final AtomicLong sequence = new AtomicLong();

    public Pessoa create(String nome, String email, long eventoId) {
        long id = sequence.incrementAndGet();
        var pessoa = new Pessoa(id, nome, email, eventoId);
        pessoas.put(id, pessoa);
        return pessoa;
    }

    public List<Pessoa> findAll() {
        return pessoas.values().stream().sorted(Comparator.comparingLong(Pessoa::id)).toList();
    }

    public Optional<Pessoa> findById(long id) {
        return Optional.ofNullable(pessoas.get(id));
    }

    public Optional<Pessoa> update(long id, String nome, String email, long eventoId) {
        return Optional.ofNullable(pessoas.computeIfPresent(id, (key, old) -> new Pessoa(id, nome, email, eventoId)));
    }

    public boolean delete(long id) {
        return pessoas.remove(id) != null;
    }
}
