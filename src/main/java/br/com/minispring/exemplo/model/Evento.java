package br.com.minispring.exemplo.model;

public record Evento(long id, String titulo, int capacidade, int inscritos,
                     String data, String horario, String local, String categoria, String descricao) {
    public Evento(long id, String titulo, int capacidade) {
        this(id, titulo, capacidade, 0, "", "", "", "", "");
    }
    /** Compatibilidade com os clientes anteriores: participantes significava capacidade. */
    @com.fasterxml.jackson.annotation.JsonProperty("participantes")
    public int participantes() { return capacidade; }
}
