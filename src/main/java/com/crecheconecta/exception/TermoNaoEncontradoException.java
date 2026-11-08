package com.crecheconecta.exception;

public class TermoNaoEncontradoException extends RuntimeException {

    public TermoNaoEncontradoException() {
        super("Versão dos termos não encontrada.");
    }
}
