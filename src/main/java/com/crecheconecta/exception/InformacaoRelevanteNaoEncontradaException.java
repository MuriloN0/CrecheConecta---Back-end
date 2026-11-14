package com.crecheconecta.exception;

public class InformacaoRelevanteNaoEncontradaException extends RuntimeException {
    public InformacaoRelevanteNaoEncontradaException() {
        super("Informação relevante não encontrada.");
    }
}