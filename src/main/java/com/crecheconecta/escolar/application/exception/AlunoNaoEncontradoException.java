package com.crecheconecta.escolar.application.exception;

public class AlunoNaoEncontradoException extends RuntimeException {
    public AlunoNaoEncontradoException() {
        super("Aluno não encontrado.");
    }
}
