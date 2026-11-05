package com.crecheconecta.dto;

import com.crecheconecta.entity.Aluno;
import com.crecheconecta.entity.Turma;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Dados do filho que o RESPONSAVEL vê no portal (Perfil da Criança).
 * turma vem null quando o aluno ainda não está matriculado.
 */
public record FilhoResponse(UUID id, String nome, LocalDate dataNascimento, TurmaResumo turma) {

    public record TurmaResumo(UUID id, String nome, String numeroSala) {

        public static TurmaResumo de(Turma turma) {
            return new TurmaResumo(turma.getId(), turma.getNome(), turma.getNumeroSala());
        }
    }

    public static FilhoResponse de(Aluno aluno, Turma turma) {
        return new FilhoResponse(aluno.getId(), aluno.getNome(), aluno.getDataNascimento(), turma == null ? null : TurmaResumo.de(turma));
    }
}
