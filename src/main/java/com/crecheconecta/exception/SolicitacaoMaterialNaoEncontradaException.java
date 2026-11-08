package com.crecheconecta.exception;

public class SolicitacaoMaterialNaoEncontradaException extends RuntimeException {

    public SolicitacaoMaterialNaoEncontradaException() {

        super("Solicitação de material não encontrada.");
    }
}
