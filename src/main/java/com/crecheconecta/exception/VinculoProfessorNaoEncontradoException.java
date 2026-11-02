package com.crecheconecta.exception;

public class VinculoProfessorNaoEncontradoException extends RuntimeException {

    public VinculoProfessorNaoEncontradoException() {

        super("Vínculo de professor não encontrado para esta turma.");
    }
}