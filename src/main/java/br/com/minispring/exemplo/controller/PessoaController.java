package br.com.minispring.exemplo.controller;

import br.com.minispring.framework.annotation.*;
import br.com.minispring.framework.web.HttpResult;
import br.com.minispring.exemplo.model.*;
import br.com.minispring.exemplo.service.PessoaService;
import java.util.List;

@RestController("/pessoas")
public final class PessoaController {
    private final PessoaService service;

    public PessoaController(PessoaService service) { this.service = service; }

    @Route(method = "GET")
    public List<Pessoa> list(@RequestParam("nome") String nome, @RequestParam(value="eventoId", defaultValue="0") long eventoId) {
        if (eventoId < 0) throw new br.com.minispring.framework.web.HttpException(400, "eventoId deve ser positivo");
        return service.list(nome, eventoId);
    }

    @Route(method = "GET", path = "/{id}")
    public Pessoa get(@PathVariable("id") long id) { return service.get(id); }

    @Route(method = "POST")
    public HttpResult create(@RequestBody PessoaInput input) {
        Pessoa pessoa = service.create(input);
        return HttpResult.created("/pessoas/" + pessoa.id(), pessoa);
    }

    @Route(method = "PUT", path = "/{id}")
    public Pessoa update(@PathVariable("id") long id, @RequestBody PessoaInput input) {
        return service.update(id, input);
    }

    @Route(method = "DELETE", path = "/{id}")
    public HttpResult delete(@PathVariable("id") long id) {
        service.delete(id);
        return HttpResult.noContent();
    }
}
