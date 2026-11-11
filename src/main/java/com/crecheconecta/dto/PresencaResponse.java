package com.crecheconecta.dto;

import com.crecheconecta.entity.Presenca;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PresencaResponse(UUID id, UUID turmaId, UUID alunoId, LocalDate data, Boolean presente, Instant atualizadoEm, Long versao) {

    public static PresencaResponse de(Presenca presenca) {

        return new PresencaResponse(
                presenca.getId(),
                presenca.getTurmaId(),
                presenca.getAlunoId(),
                presenca.getData(),
                presenca.isPresente(),
                presenca.getAtualizadoEm(),
                presenca.getVersao()
        );
    }
}