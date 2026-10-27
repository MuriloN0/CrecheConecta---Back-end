package com.crecheconecta.dto;

import java.time.Instant;
import java.util.UUID;

public record ConviteCadastroResponse(UUID id, String email, Instant expiraEm, String mensagem) {

}
