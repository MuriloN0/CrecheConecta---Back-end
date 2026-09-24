package com.crecheconecta.exception;

public class AtividadeNaoEncontradaException extends RuntimeException {
    public AtividadeNaoEncontradaException() {
        super("Atividade não encontrada.");
    }
}