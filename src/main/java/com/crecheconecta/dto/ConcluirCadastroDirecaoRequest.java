package com.crecheconecta.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ConcluirCadastroDirecaoRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String token,
        @NotBlank @Pattern(regexp = "[1-9][0-9]{9,10}", message = "Informe o telefone com DDD e somente números.") String telefone,
        @NotBlank @Size(min = 12, max = 72) String senha) {

    @Override
    public String toString() {

        return "ConcluirCadastroDirecaoRequest[dados omitidos]";
    }
}