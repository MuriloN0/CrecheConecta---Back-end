package com.crecheconecta.atividades.domain.model;

import java.time.Instant;
import java.util.UUID;

public record ConclusaoAtividade(
        UUID id,
        UUID atividadeId,
        UUID alunoId,
        StatusConclusao status,
        Instant marcadaEm
) {
    public ConclusaoAtividade {
        if (id == null || atividadeId == null || alunoId == null || status == null) {
            throw new IllegalArgumentException("Campos obrigatórios ausentes na conclusão");
        }

        if (status == StatusConclusao.CONCLUIDA && marcadaEm == null) {
            throw new IllegalArgumentException("Data de marcação ausente para atividade concluída");
        }
    }
}