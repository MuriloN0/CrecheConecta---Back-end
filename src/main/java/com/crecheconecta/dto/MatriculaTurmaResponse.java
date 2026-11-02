package com.crecheconecta.dto;

import com.crecheconecta.entity.MatriculaTurma;

import java.time.Instant;
import java.util.UUID;

public record MatriculaTurmaResponse(UUID id, UUID alunoId, UUID turmaId, Instant criadoEm, Instant encerradoEm) {

    public static MatriculaTurmaResponse de(MatriculaTurma matricula) {

        return new MatriculaTurmaResponse(matricula.getId(), matricula.getAlunoId(), matricula.getTurmaId(), matricula.getCriadoEm(), matricula.getEncerradoEm());
    }
}