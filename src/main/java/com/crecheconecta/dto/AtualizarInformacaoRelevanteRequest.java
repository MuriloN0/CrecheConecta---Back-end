package com.crecheconecta.dto;

import com.crecheconecta.model.DadosFichaSaude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AtualizarFichaSaudeRequest(
        @NotBlank @Size (max = 120) String nome,
        @Size(max = 5000) String observacoes,
        @NotNull Long versao) {

    public DadosFichaSaude paraDominio() {
        return new DadosFichaSaude(nome, observacoes);
    }
}
