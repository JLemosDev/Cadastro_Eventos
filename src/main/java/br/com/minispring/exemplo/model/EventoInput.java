package br.com.minispring.exemplo.model;

import br.com.minispring.framework.validation.NotBlank;
import br.com.minispring.framework.validation.Positive;

/** DTO de entrada separado do modelo: o cliente não escolhe o ID. */

public record EventoInput(@NotBlank String titulo,
                          @com.fasterxml.jackson.annotation.JsonAlias("participantes") @Positive int capacidade,
                          String data, String horario, String local, String categoria, String descricao) {
    public EventoInput(String titulo, int capacidade) { this(titulo, capacidade, "", "", "", "", ""); }
    public int participantes() { return capacidade; }
    public EventoInput {
        titulo = titulo == null ? null : titulo.trim();
        data = clean(data); horario = clean(horario); local = clean(local);
        categoria = clean(categoria); descricao = clean(descricao);
    }
    private static String clean(String text) { return text == null ? "" : text.trim(); }
}
