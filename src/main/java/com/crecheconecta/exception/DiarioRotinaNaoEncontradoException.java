package com.crecheconecta.exception;

public class DiarioRotinaNaoEncontradoException extends RuntimeException {

    public DiarioRotinaNaoEncontradoException() {
        super("Diário de rotina não encontrado.");
    }
}
