package com.crecheconecta.escolar.domain.model;

import com.crecheconecta.escolar.domain.exception.RegraNegocioException;

import java.util.UUID;

public record Aluno(UUID id, DadosAluno dadosAluno, boolean ativo, Long versao) {

    public Aluno {
        if(id == null || dadosAluno == null) {
            throw new RegraNegocioException("Identificador e dados do aluno são obrigatórios.");
        }

        if(versao != null && versao < 0) {
            throw new RegraNegocioException("Versão inválida.");
        }
    }

    public static Aluno cadastrar(DadosAluno dadosAluno) {
        return new Aluno(UUID.randomUUID(), dadosAluno, true, null);
    }

    public Aluno atualizar(DadosAluno novosDadosAluno) {
        if (!ativo) {
            throw new RegraNegocioException("Aluno inativo não pode ser atualizado.");
        }
        return new Aluno(id, novosDadosAluno, true, versao);
    }

    public Aluno inativar(){
        return  new Aluno(id, dadosAluno, false, versao);
    }
}
