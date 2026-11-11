package com.crecheconecta.dto;

import com.crecheconecta.model.DadosObservacao;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ObservacaoRequest(
        @NotBlank @Size(max = 120) String titulo,
        @Size(max = 5000) String conteudo) {

    public DadosObservacao paraDominio() {
        return new DadosObservacao(titulo, conteudo);
    }
}
