package com.crecheconecta.dto;

import com.crecheconecta.entity.Observacao;
import java.time.Instant;
import java.util.UUID;

public record ObservacaoResponse(
        UUID id,
        UUID alunoId,
        String titulo,
        String conteudo,
        Long versao,
        Instant dataCriacao,
        Instant dataAtualizacao) {

    public static ObservacaoResponse de(Observacao obs, String conteudoDecifrado) {
        return new ObservacaoResponse(
                obs.getId(),
                obs.getAlunoId(),
                obs.getTitulo(),
                conteudoDecifrado,
                obs.getVersao(),
                obs.getDataCriacao(),
                obs.getDataAtualizacao());
    }
}
