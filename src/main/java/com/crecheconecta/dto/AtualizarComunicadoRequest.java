package com.crecheconecta.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record AtualizarComunicadoRequest(@NotBlank @Size(max = 150) String titulo, @NotBlank @Size(max = 5000) String conteudo, @NotNull @PositiveOrZero Long versao) {
}