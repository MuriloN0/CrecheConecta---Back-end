package com.crecheconecta.dto;

import com.crecheconecta.entity.Turma;

import java.util.UUID;

public record TurmaResponse(UUID id, String nome, String numeroSala, boolean ativo, Long versao) {

    public static TurmaResponse de(Turma turma) {

        return new TurmaResponse(turma.getId(), turma.getNome(), turma.getNumeroSala(), turma.isAtivo(), turma.getVersao());
    }
}