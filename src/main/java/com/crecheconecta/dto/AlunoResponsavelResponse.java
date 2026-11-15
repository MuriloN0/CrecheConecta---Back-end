package com.crecheconecta.dto;

import com.crecheconecta.entity.AlunoResponsavel;
import com.crecheconecta.entity.Usuario;

import java.time.Instant;
import java.util.UUID;

public record AlunoResponsavelResponse(UUID id, UUID alunoId, UUID responsavelId, Instant criadoEm, Instant encerradoEm, String nome, String email, String telefone) {

    public static AlunoResponsavelResponse de(AlunoResponsavel vinculo, Usuario responsavel) {

        return new AlunoResponsavelResponse(
                vinculo.getId(),
                vinculo.getAlunoId(),
                vinculo.getResponsavelId(),
                vinculo.getCriadoEm(),
                vinculo.getEncerradoEm(),
                responsavel == null ? null : responsavel.getNome(),
                responsavel == null ? null : responsavel.getEmail(),
                responsavel == null ? null : responsavel.getTelefone()
        );
    }
}