package com.crecheconecta.saude.adapter.in.web;

import com.crecheconecta.saude.domain.model.FichaSaude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FichaSaudeResponse(
        UUID id,
        UUID alunoId,
        String nome,
        String observacoes,
        List<AnexoResponse> anexos,
        Instant dataCriacao,
        Instant dataAtualizacao
) {
    public static FichaSaudeResponse de(FichaSaude ficha) {
        return new FichaSaudeResponse(
                ficha.id(),
                ficha.alunoId(),
                ficha.nome(),
                ficha.observacoes(),
                ficha.anexos().stream().map(AnexoResponse::de).toList(),
                ficha.dataCriacao(),
                ficha.dataAtualizacao());
    }
}
