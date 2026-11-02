package com.crecheconecta.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CadastrarTurmaRequest(@NotBlank @Size(max = 150) String nome, @NotBlank @Size(max = 20) String numeroSala) {
}