package com.crecheconecta.exception;

public class ComunicadoNaoEncontradoException extends RuntimeException {

    public ComunicadoNaoEncontradoException() {

        super("Comunicado não encontrado.");
    }
}