package com.crecheconecta.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record RegistrarPresencaRequest(@NotNull Boolean presente, @PositiveOrZero Long versao) {
}