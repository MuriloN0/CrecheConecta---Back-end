package com.crecheconecta.dto;

import com.crecheconecta.model.DadosFichaSaude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FichaSaudeRequest(
    @NotBlank @Size(max = 120) String nome,
    @Size(max = 5000) String observacoes) {

    public DadosFichaSaude paraDominio() {
        return new DadosFichaSaude(nome, observacoes);
    }
}