package com.crecheconecta.dto;

import com.crecheconecta.model.DadosObservacao;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AtualizarObservacaoRequest(
        @NotBlank @Size(max = 120) String titulo,
        @Size(max = 5000) String conteudo,
        @NotNull Long versao) {

    public DadosObservacao paraDominio() {
        return new DadosObservacao(titulo, conteudo);
    }
}
