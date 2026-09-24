package com.crecheconecta.dto;

import com.crecheconecta.model.TipoAtividade;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record AtualizarAtividadeRequestDTO(
        TipoAtividade tipo,
        @Size(max = 150, message = "O título deve ter no máximo 150 caracteres")
        String titulo,
        String descricao,
        LocalDate prazoConclusao
) {
}