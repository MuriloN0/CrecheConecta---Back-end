package com.crecheconecta.dto;

import java.util.UUID;

public record ChamadaAlunoResponse(UUID alunoId, String nome, Boolean presente, Long versao) {
}