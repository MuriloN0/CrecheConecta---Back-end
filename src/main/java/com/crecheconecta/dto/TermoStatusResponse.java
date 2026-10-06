package com.crecheconecta.dto;

public record TermoStatusResponse(
        boolean precisaAceitar,
        String versao,
        String termoUso,
        String politicaPrivacidade) {}