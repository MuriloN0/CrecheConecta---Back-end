package com.crecheconecta.escolar.adapter.in.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record AtualizarAlunoRequest(
        @NotNull
        @PositiveOrZero
        Long versao,

        @NotNull
        @Valid
        AlunoRequest dados
) {
}
