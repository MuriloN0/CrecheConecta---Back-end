package com.crecheconecta.dto;

import java.util.UUID;

/** Resumo do que a anonimização fez (para a tela da direção confirmar o resultado). */
public record AnonimizacaoResponse(
        UUID alunoId,
        int responsaveisAnonimizados,
        int informacoesRelevantesRemovidas,
        int observacoesRemovidas) {
}
