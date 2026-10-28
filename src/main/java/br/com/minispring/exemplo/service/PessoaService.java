package br.com.minispring.exemplo.service;

import br.com.minispring.framework.annotation.Service;
import br.com.minispring.framework.web.HttpException;
import br.com.minispring.exemplo.model.*;
import br.com.minispring.exemplo.repository.EventoRepository;
import br.com.minispring.exemplo.repository.PessoaRepository;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public final class PessoaService {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final PessoaRepository repository;
    private final EventoRepository eventoRepository;

    public PessoaService(PessoaRepository repository, EventoRepository eventoRepository) {
        this.repository = repository;
        this.eventoRepository = eventoRepository;
    }

    public List<Pessoa> list(String nome) {

        String filter = nome.toLowerCase(Locale.ROOT);
        return repository.findAll().stream().filter(p -> p.nome().toLowerCase(Locale.ROOT).contains(filter)).toList();
    }

    public Pessoa get(long id) {
        return repository.findById(id).orElseThrow(this::notFound);
    }

    public List<Pessoa> list(String nome, long eventoId) {
        return list(nome).stream().filter(p -> eventoId == 0 || p.eventoId() == eventoId).toList();
    }

    public Pessoa create(PessoaInput input) {
        validate(input);
        checkCapacity(input.eventoId(), 0);
        return repository.create(input.nome().trim(), input.email().trim(), input.eventoId());
    }

    public Pessoa update(long id, PessoaInput input) {
        get(id);
        validate(input);
        checkCapacity(input.eventoId(), id);
        return repository.update(id, input.nome().trim(), input.email().trim(), input.eventoId()).orElseThrow(this::notFound);
    }

    public void delete(long id) {
        if (!repository.delete(id)) throw notFound();
    }

    // Validações acontecem antes de gravar: uma requisição rejeitada nunca altera os dados.
    private void validate(PessoaInput input) {
        if (input.nome() == null || input.nome().isBlank() || input.nome().length() > 200 || input.email() == null || input.email().length() > 254)
            throw new HttpException(400, "Informe nome (até 200 caracteres) e e-mail (até 254 caracteres)");
        if (!EMAIL.matcher(input.email().trim()).matches()) throw new HttpException(400, "E-mail inválido");
        if (eventoRepository.findById(input.eventoId()).isEmpty())
            throw new HttpException(422, "O evento informado não existe");
    }

    private HttpException notFound() {
        return new HttpException(404, "Pessoa não encontrada");
    }

    private void checkCapacity(long eventoId, long pessoaId) {
        var event = eventoRepository.findById(eventoId).orElseThrow(() -> new HttpException(422, "O evento informado não existe"));
        long count = repository.countByEvento(eventoId);
        if (pessoaId != 0 && repository.findById(pessoaId).filter(p -> p.eventoId() == eventoId).isPresent()) count--;
        if (count >= event.capacidade()) throw new HttpException(409, "O evento atingiu sua capacidade máxima");
    }
}
