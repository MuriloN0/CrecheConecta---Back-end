package com.crecheconecta.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record DecidirCadastroProfessorRequest(@NotNull @PositiveOrZero Long versao) {
}
