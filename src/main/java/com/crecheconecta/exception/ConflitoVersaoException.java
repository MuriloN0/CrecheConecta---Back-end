package com.crecheconecta.exception;

public class ConflitoVersaoException extends RuntimeException {
    public ConflitoVersaoException() {
        super("Cadastro alterado por outra pessoa. Recarregue os dados.");
    }
}