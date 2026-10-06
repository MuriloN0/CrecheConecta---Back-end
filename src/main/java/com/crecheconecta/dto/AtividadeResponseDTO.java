package com.crecheconecta.dto;

import com.crecheconecta.entity.AtividadeEntity;
import com.crecheconecta.model.TipoAtividade;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AtividadeResponseDTO(
        UUID id,
        UUID turmaId,
        UUID professorId,
        TipoAtividade tipo,
        String titulo,
        String descricao,
        Instant dataCriacao,
        LocalDate prazoConclusao
) {

    public static AtividadeResponseDTO fromEntity(AtividadeEntity atividade) {
        return new AtividadeResponseDTO(
                atividade.getId(),
                atividade.getTurmaId(),
                atividade.getProfessorId(),
                atividade.getTipo(),
                atividade.getTitulo(),
                atividade.getDescricao(),
                atividade.getDataCriacao(),
                atividade.getPrazoConclusao()
        );
    }
}