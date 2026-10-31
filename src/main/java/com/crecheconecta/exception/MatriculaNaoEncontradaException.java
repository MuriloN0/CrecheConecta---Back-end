package com.crecheconecta.exception;

public class MatriculaNaoEncontradaException extends RuntimeException {

    public MatriculaNaoEncontradaException() {
        super("Matrícula não encontrada para esta turma.");
    }
}