package com.crecheconecta.atividades.domain.model;

import com.crecheconecta.atividades.domain.model.TipoAtividade;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record Atividade(
        UUID id,
        UUID turmaId,
        UUID professorId,
        TipoAtividade tipo,
        String titulo,
        String descricao,
        Instant dataCriacao,
        LocalDate prazoConclusao
) {
    public Atividade {
        if (id == null || turmaId == null || professorId == null || tipo == null || dataCriacao == null) {
            throw new IllegalArgumentException("Campos obrigatórios ausentes na atividade");
        }

        if (titulo == null || titulo.isBlank()) {
            throw new IllegalArgumentException("Título é obrigatório");
        }

        titulo = titulo.trim();

        if (titulo.length() > 150) {
            throw new IllegalArgumentException("Título excedeu 150 caracteres");
        }

        if (tipo == TipoAtividade.CASA && prazoConclusao == null) {
            throw new IllegalArgumentException("Prazo de conclusão é obrigatório para atividade de casa");
        }
    }
}