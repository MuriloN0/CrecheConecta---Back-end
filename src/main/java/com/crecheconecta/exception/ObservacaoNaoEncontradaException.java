package com.crecheconecta.exception;

public class ObservacaoNaoEncontradaException extends RuntimeException {

    public ObservacaoNaoEncontradaException() {
        super("Observação não encontrada.");
    }
}
