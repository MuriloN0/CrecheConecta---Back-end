package com.crecheconecta.exception;

import org.springframework.http.HttpStatus;

public enum ErroAutenticacao {

    CREDENCIAIS_INVALIDAS(
            HttpStatus.UNAUTHORIZED,
            "E-mail ou senha inválidos."
    ),

    ACAO_INVALIDA(
            HttpStatus.BAD_REQUEST,
            "Código ou autorização inválido, expirado ou indisponível."
    ),

    LIMITE_TENTATIVAS(
            HttpStatus.TOO_MANY_REQUESTS,
            "Limite de tentativas atingido. Tente novamente mais tarde."
    ),

    ENVIO_EMAIL_INDISPONIVEL(
            HttpStatus.SERVICE_UNAVAILABLE,
            "Não foi possível enviar o e-mail. Tente novamente mais tarde."
    );

    private final HttpStatus status;
    private final String mensagem;

    ErroAutenticacao(HttpStatus status, String mensagem) {
        this.status = status;
        this.mensagem = mensagem;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMensagem() {
        return mensagem;
    }

}
