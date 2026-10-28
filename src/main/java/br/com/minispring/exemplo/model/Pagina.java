package br.com.minispring.exemplo.model;

import java.util.List;

public record Pagina<T>(List<T> items, long total, int page, int size, long pages, Resumo resumo) {
    public record Resumo(long eventos, long capacidade, long inscritos) {}
}
