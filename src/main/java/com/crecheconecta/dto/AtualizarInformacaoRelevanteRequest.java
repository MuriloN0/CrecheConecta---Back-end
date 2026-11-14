package com.crecheconecta.dto;

import com.crecheconecta.model.DadosInformacaoRelevante;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AtualizarInformacaoRelevanteRequest(
        @NotBlank @Size (max = 120) String nome,
        @Size(max = 5000) String observacoes,
        @NotNull Long versao) {

    public DadosInformacaoRelevante paraDominio() {
        return new DadosInformacaoRelevante(nome, observacoes);
    }
}
