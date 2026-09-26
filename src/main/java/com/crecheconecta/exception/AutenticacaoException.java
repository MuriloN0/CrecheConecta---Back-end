package com.crecheconecta.exception;

public class AutenticacaoException extends RuntimeException {

    private final ErroAutenticacao erro;

    public AutenticacaoException(ErroAutenticacao erro) {
        super(erro.getMensagem());
        this.erro = erro;
    }

    public ErroAutenticacao getErro() {
        return erro;
    }

}
