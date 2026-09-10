package com.crecheconecta.escolar.application.exception;

public class ConflitoVersaoException extends RuntimeException {
    public ConflitoVersaoException() {
        super("O cadastro foi alterado. Recarregue a página e tente novamente.");
    }
}
