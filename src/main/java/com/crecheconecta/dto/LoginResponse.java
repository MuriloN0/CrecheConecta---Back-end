package com.crecheconecta.dto;

import java.time.Instant;
import java.util.UUID;

public record LoginResponse(UUID acaoId, Instant expiraEm, String mensagem) {

}
