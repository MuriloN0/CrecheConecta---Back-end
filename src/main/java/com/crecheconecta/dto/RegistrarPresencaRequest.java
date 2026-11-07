package com.crecheconecta.dto;

import jakarta.validation.constraints.PositiveOrZero;

public record RegistrarPresencaRequest(Boolean presente, @PositiveOrZero Long versao, Boolean limpar) {

    public RegistrarPresencaRequest(Boolean presente, Long versao) {

        this(presente, versao, null);
    }
}