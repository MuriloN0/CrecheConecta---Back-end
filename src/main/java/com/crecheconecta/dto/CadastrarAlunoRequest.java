package com.crecheconecta.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CadastrarAlunoRequest(@NotBlank @Size(max = 150) String nome, @NotNull @PastOrPresent LocalDate dataNascimento) {
}