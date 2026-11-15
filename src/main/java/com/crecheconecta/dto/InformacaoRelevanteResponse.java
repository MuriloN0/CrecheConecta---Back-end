package com.crecheconecta.dto;

import com.crecheconecta.entity.InformacaoRelevante;
import java.time.Instant;
import java.util.UUID;

public record InformacaoRelevanteResponse(
        UUID id,
        UUID alunoId,
        String nome,
        String observacoes,
        Long versao,
        Instant dataCriacao,
        Instant dataAtualizacao) {

    public static InformacaoRelevanteResponse de(InformacaoRelevante informacao, String observacoesDecifradas) {
        return new InformacaoRelevanteResponse(
                informacao.getId(),
                informacao.getAlunoId(),
                informacao.getNome(),
                observacoesDecifradas,
                informacao.getVersao(),
                informacao.getDataCriacao(),
                informacao.getDataAtualizacao());
    }
}
