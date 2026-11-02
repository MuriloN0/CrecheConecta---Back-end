package com.crecheconecta.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record AtualizarTurmaRequest(@NotBlank @Size(max = 150) String nome, @NotBlank @Size(max = 20) String numeroSala, @NotNull @PositiveOrZero Long versao) {
}