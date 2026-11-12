package com.crecheconecta.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record ConcluirCadastroResponsavelRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String token,
        @NotBlank @Size(max = 150) String nome,
        @NotBlank @Pattern(regexp = "[1-9][0-9]{9,10}") String telefone,
        @NotBlank @Size(min = 12, max = 72) String senha,
        @NotBlank @Size(max = 150) String nomeAluno,
        @NotNull(message = "Informe a data de nascimento do aluno.") LocalDate dataNascimento) {

    @Override
    public String toString() {

        return "ConcluirCadastroResponsavelRequest[dados omitidos]";
    }
}