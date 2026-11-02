package com.crecheconecta.dto;

import com.crecheconecta.entity.Aluno;

import java.time.LocalDate;
import java.util.UUID;

public record AlunoResponse(UUID id, String nome, LocalDate dataNascimento, boolean ativo, Long versao) {

    public static AlunoResponse de(Aluno aluno) {

        return new AlunoResponse(aluno.getId(), aluno.getNome(), aluno.getDataNascimento(), aluno.isAtivo(), aluno.getVersao());
    }
}