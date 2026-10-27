package com.crecheconecta.exception;

public class AlunoNaoEncontradoException extends RuntimeException {

    public AlunoNaoEncontradoException() {
        super("Aluno não encontrado.");
    }
}