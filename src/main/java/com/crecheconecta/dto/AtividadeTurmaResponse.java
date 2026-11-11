package com.crecheconecta.dto;

import com.crecheconecta.entity.Turma;

import java.util.UUID;

public record AtividadeTurmaResponse(UUID id, String nome, String numeroSala) {

    public static AtividadeTurmaResponse de(Turma turma) {

        return new AtividadeTurmaResponse(turma.getId(), turma.getNome(), turma.getNumeroSala());
    }
}