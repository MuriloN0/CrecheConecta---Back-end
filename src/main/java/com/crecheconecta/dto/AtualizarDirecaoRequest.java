package com.crecheconecta.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record AtualizarDirecaoRequest(@NotBlank @Size(max = 150) String nome, @NotBlank @Pattern(regexp = "[1-9][0-9]{9,10}", message = "Informe o telefone com DDD e somente números.") String telefone, @NotNull @PositiveOrZero Long versao) {
}