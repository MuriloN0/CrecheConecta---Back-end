package com.crecheconecta.dto;

import jakarta.validation.constraints.*;

public record AtualizarProfessorRequest(@NotBlank @Size(max = 150) String nome, @NotBlank @Pattern(regexp = "[1-9][0-9]{9,10}", message = "Informe o telefone com DDD e somente números.") String telefone, @NotNull @PositiveOrZero Long versao) {

}
