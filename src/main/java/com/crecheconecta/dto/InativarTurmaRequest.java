package com.crecheconecta.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record InativarTurmaRequest(@NotNull @PositiveOrZero Long versao) {
}