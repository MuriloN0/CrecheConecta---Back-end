package com.crecheconecta.escolar.adapter.in.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record InativarAlunoRequest(@NotNull @PositiveOrZero Long versao) {
}
