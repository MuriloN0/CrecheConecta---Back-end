package com.crecheconecta.atividades.adapter.in.dto;

import com.crecheconecta.atividades.domain.model.Atividade;
import com.crecheconecta.atividades.domain.model.TipoAtividade;
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
    public static AtividadeResponseDTO fromDomain(Atividade atividade) {
        return new AtividadeResponseDTO(
                atividade.id(),
                atividade.turmaId(),
                atividade.professorId(),
                atividade.tipo(),
                atividade.titulo(),
                atividade.descricao(),
                atividade.dataCriacao(),
                atividade.prazoConclusao()
        );
    }
}