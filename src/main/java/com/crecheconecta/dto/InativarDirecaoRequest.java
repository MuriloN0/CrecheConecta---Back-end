package com.crecheconecta.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record InativarDirecaoRequest(@NotNull @PositiveOrZero Long versao) {
}