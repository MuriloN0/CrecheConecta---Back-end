package com.crecheconecta.saude.application.exception;

public final class AcessoNegadoException extends RuntimeException {
    public AcessoNegadoException () {
        super("Operação não permitida.");
    }

}

