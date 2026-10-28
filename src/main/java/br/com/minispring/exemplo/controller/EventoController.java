package br.com.minispring.exemplo.controller;

import br.com.minispring.framework.annotation.*;
import br.com.minispring.framework.web.HttpResult;
import br.com.minispring.exemplo.model.*;
import br.com.minispring.exemplo.service.EventoService;
import java.util.List;

@RestController("/eventos")
public final class EventoController {
    private final EventoService service;

    public EventoController(EventoService service) { this.service = service; }

    @Route(method = "GET")
    public Pagina<Evento> list(@RequestParam("titulo") String titulo,
                              @RequestParam(value="page", defaultValue="1") int page,
                              @RequestParam(value="size", defaultValue="25") int size,
                              @RequestParam(value="sort", defaultValue="id") String sort,
                              @RequestParam("categoria") String categoria, @RequestParam("periodo") String periodo) {
        return service.page(titulo, page, size, sort, categoria, periodo);
    }

    @Route(method = "GET", path = "/{id}")
    public Evento get(@PathVariable("id") long id) { return service.get(id); }

    @Route(method = "POST")
    public HttpResult create(@RequestBody EventoInput input) {
        Evento evento = service.create(input);
        return HttpResult.created("/eventos/" + evento.id(), evento);
    }

    @Route(method = "PUT", path = "/{id}")
    public Evento update(@PathVariable("id") long id, @RequestBody EventoInput input) {
        return service.update(id, input);
    }

    @Route(method = "DELETE", path = "/{id}")
    public HttpResult delete(@PathVariable("id") long id) {
        service.delete(id);
        return HttpResult.noContent();
    }
}
