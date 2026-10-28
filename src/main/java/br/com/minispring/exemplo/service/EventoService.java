package br.com.minispring.exemplo.service;

import br.com.minispring.framework.annotation.Service;
import br.com.minispring.framework.web.HttpException;
import br.com.minispring.exemplo.model.*;
import br.com.minispring.exemplo.repository.EventoRepository;
import br.com.minispring.exemplo.repository.PessoaRepository;

import java.util.List;
import java.util.Locale;

@Service
public final class EventoService {
    private final EventoRepository repository;
    private final PessoaRepository pessoas;

    public EventoService(EventoRepository repository, PessoaRepository pessoas) {
        this.repository = repository;
        this.pessoas = pessoas;
    }

    public List<Evento> list(String titulo) {

        String filter = titulo.toLowerCase(Locale.ROOT);
        return repository.findAll().stream().filter(c -> c.titulo().toLowerCase(Locale.ROOT).contains(filter)).toList();
    }

    public Evento get(long id) {
        return repository.findById(id).orElseThrow(this::notFound);
    }

    public Evento create(EventoInput input) {
        validateBusinessRule(input);
        return repository.create(input);
    }

    public Evento update(long id, EventoInput input) {
        validateBusinessRule(input);
        if (input.capacidade() < pessoas.countByEvento(id)) throw new HttpException(409, "A capacidade não pode ser menor que o número de inscritos");
        return repository.update(id, input).orElseThrow(this::notFound);
    }

    public void delete(long id) {
        get(id);
        if (pessoas.countByEvento(id) > 0) throw new HttpException(409, "Existem pessoas vinculadas a este evento. Remova ou transfira as inscrições antes de excluir");
        if (!repository.delete(id)) throw notFound();
    }

    private void validateBusinessRule(EventoInput input) {
        if (input.titulo() == null || input.titulo().isBlank() || input.capacidade() <= 0) throw new HttpException(400, "Título e capacidade positiva são obrigatórios");
        if (input.participantes() > 5000) throw new HttpException(422, "Um evento pode ter no máximo 5000 participantes");
        if (input.titulo().length() > 200 || input.local().length() > 200 || input.categoria().length() > 80 || input.descricao().length() > 5000)
            throw new HttpException(400, "Limites: título e local 200 caracteres, categoria 80 e descrição 5000");
        try {
            if (!input.data().isEmpty()) java.time.LocalDate.parse(input.data());
            if (!input.horario().isEmpty()) {
                if (!input.horario().matches("\\d{2}:\\d{2}")) throw new java.time.DateTimeException("Horário inválido");
                java.time.LocalTime.parse(input.horario());
            }
        } catch (java.time.DateTimeException e) { throw new HttpException(400, "Use uma data válida no formato AAAA-MM-DD e horário HH:mm"); }
    }

    public Pagina<Evento> page(String titulo, int page, int size, String sort, String categoria, String periodo) {
        if (page < 1 || size < 1 || size > 100) throw new HttpException(400, "page deve ser positivo e size deve estar entre 1 e 100");
        if (!java.util.Set.of("id", "newest", "title", "participants", "capacidade", "inscritos", "data").contains(sort)) throw new HttpException(400, "Ordenação inválida");
        if (!java.util.Set.of("", "futuros", "passados", "sem-data").contains(periodo)) throw new HttpException(400, "Período inválido");
        return repository.page(titulo.trim(), page, size, sort, categoria.trim(), periodo);
    }

    private HttpException notFound() {
        return new HttpException(404, "Evento não encontrado");
    }
}
