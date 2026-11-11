package com.crecheconecta.dto;

import com.crecheconecta.entity.Alimentacao;
import com.crecheconecta.entity.Higiene;
import com.crecheconecta.entity.Humor;
import com.crecheconecta.entity.Sono;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Corpo do PUT do professor. "versao" é null ao criar o diário e obrigatório ao editar (controle de edição simultânea).
 */
public record RegistrarDiarioRequest(
        @NotNull Alimentacao alimentacao,
        @NotNull Sono sono,
        @NotNull Boolean evacuacao,
        @NotNull Humor humor,
        @NotNull Higiene higiene,
        @Size(max = 1000) String observacaoProfessor,
        @PositiveOrZero Long versao
) {
}
