package br.com.minispring.exemplo.model;

import br.com.minispring.framework.validation.NotBlank;
import br.com.minispring.framework.validation.Positive;

/** DTO de entrada separado do modelo: o cliente não escolhe o ID. */

public record PessoaInput(@NotBlank String nome, @NotBlank String email, @Positive long eventoId) {}
