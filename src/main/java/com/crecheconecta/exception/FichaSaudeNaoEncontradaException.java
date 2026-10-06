package com.crecheconecta.exception;

public class FichaSaudeNaoEncontradaException extends RuntimeException {
    public FichaSaudeNaoEncontradaException() {
        super("Ficha de saúde não encontrada.");
    }
}