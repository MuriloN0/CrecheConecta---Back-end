package com.crecheconecta.dto;

import com.crecheconecta.model.DadosInformacaoRelevante;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InformacaoRelevanteRequest(
    @NotBlank @Size(max = 120) String nome,
    @Size(max = 5000) String observacoes) {

    public DadosInformacaoRelevante paraDominio() {
        return new DadosInformacaoRelevante(nome, observacoes);
    }
}