package com.crecheconecta.dto;

import com.crecheconecta.entity.AlunoResponsavel;

import java.time.Instant;
import java.util.UUID;

public record AlunoResponsavelResponse(UUID id, UUID alunoId, UUID responsavelId, Instant criadoEm, Instant encerradoEm) {

    public static AlunoResponsavelResponse de(AlunoResponsavel vinculo) {

        return new AlunoResponsavelResponse(vinculo.getId(), vinculo.getAlunoId(), vinculo.getResponsavelId(), vinculo.getCriadoEm(), vinculo.getEncerradoEm());
    }
}