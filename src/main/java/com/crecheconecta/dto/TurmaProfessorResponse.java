package com.crecheconecta.dto;

import com.crecheconecta.entity.TurmaProfessor;

import java.time.Instant;
import java.util.UUID;

public record TurmaProfessorResponse(UUID id, UUID turmaId, UUID professorId, Instant criadoEm, Instant encerradoEm) {

    public static TurmaProfessorResponse de(TurmaProfessor vinculo) {

        return new TurmaProfessorResponse(vinculo.getId(), vinculo.getTurmaId(), vinculo.getProfessorId(), vinculo.getCriadoEm(), vinculo.getEncerradoEm());
    }
}