package com.crecheconecta.dto;

import java.time.Instant;
import java.util.UUID;

public record SolicitarRecuperacaoResponse(UUID acaoId, Instant expiraEm, String mensagem) {



}
