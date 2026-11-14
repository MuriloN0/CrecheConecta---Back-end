package com.crecheconecta.dto;

import com.crecheconecta.entity.Observacao;
import java.time.Instant;
import java.util.UUID;

/**
 * Observação do aluno escrita pelo professor.
 * "editavel" é verdadeiro só para o professor que a registrou (os demais apenas leem).
 */
public record ObservacaoResponse(
        UUID id,
        UUID alunoId,
        String titulo,
        String conteudo,
        UUID autorId,
        String autorNome,
        boolean editavel,
        Long versao,
        Instant dataCriacao,
        Instant dataAtualizacao) {

    public static ObservacaoResponse de(Observacao obs, String conteudoDecifrado, String autorNome, boolean editavel) {
        return new ObservacaoResponse(
                obs.getId(),
                obs.getAlunoId(),
                obs.getTitulo(),
                conteudoDecifrado,
                obs.getAutorId(),
                autorNome,
                editavel,
                obs.getVersao(),
                obs.getDataCriacao(),
                obs.getDataAtualizacao());
    }
}
