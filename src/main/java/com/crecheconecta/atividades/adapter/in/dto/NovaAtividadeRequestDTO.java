package com.crecheconecta.atividades.adapter.in.dto;

import com.crecheconecta.atividades.domain.model.TipoAtividade;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record NovaAtividadeRequestDTO(
        @NotNull(message = "O ID do professor é obrigatório (Temporário)")
        UUID professorId, // Adicionado temporariamente até o login existir

        @NotNull(message = "O tipo da atividade é obrigatório")
        TipoAtividade tipo,

        @NotBlank(message = "O título é obrigatório")
        @Size(max = 150, message = "O título deve ter no máximo 150 caracteres")
        String titulo,

        String descricao,

        LocalDate prazoConclusao
) {}