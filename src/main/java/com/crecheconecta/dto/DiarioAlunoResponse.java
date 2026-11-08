package com.crecheconecta.dto;

import java.util.UUID;

/**
 * Uma linha da tela do professor: o aluno da turma e o diário do dia ("diario" é null se ainda não foi preenchido).
 */
public record DiarioAlunoResponse(UUID alunoId, String nome, DiarioRegistroResponse diario) {
}
