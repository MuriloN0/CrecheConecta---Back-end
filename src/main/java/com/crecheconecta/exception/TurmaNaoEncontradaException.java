package com.crecheconecta.exception;

public class TurmaNaoEncontradaException extends RuntimeException {

    public TurmaNaoEncontradaException() {
        
        super("Turma não encontrada.");
    }
}