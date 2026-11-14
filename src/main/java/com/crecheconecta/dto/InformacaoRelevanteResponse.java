package com.crecheconecta.dto;

import com.crecheconecta.entity.FichaSaude;
import java.time.Instant;
import java.util.UUID;

public record FichaSaudeResponse(
        UUID id,
        UUID alunoId,
        String nome,
        String observacoes,
        Long versao,
        Instant dataCriacao,
        Instant dataAtualizacao) {

    public static FichaSaudeResponse de(FichaSaude ficha, String observacoesDecifradas) {
        return new FichaSaudeResponse(
                ficha.getId(),
                ficha.getAlunoId(),
                ficha.getNome(),
                observacoesDecifradas,
                ficha.getVersao(),
                ficha.getDataCriacao(),
                ficha.getDataAtualizacao());
    }
}
