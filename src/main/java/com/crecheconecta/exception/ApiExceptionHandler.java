package com.crecheconecta.exception;

import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.UUID;
import org.slf4j.Logger;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(AutenticacaoException.class)
    public ResponseEntity<ProblemDetail> tratarAutenticacao(
            AutenticacaoException exception
    ) {
        var erro = exception.getErro();

        return resposta(
                erro.getStatus(),
                erro.name(),
                erro.getMensagem()
        );
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        var problema = problema(
                status,
                "DADOS_INVALIDOS",
                "Confira os campos enviados."
        );

        var campos = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(erro -> erro.getField())
                .distinct()
                .sorted()
                .toList();

        problema.setProperty("campos", campos);

        return handleExceptionInternal(
                exception,
                problema,
                headers,
                status,
                request
        );
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        var problema = problema(
                status,
                "REQUISICAO_INVALIDA",
                "O corpo da requisição está ausente ou possui formato inválido."
        );

        return handleExceptionInternal(
                exception,
                problema,
                headers,
                status,
                request
        );
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> tratarNaoAutenticado(
            AuthenticationException exception
    ) {
        return resposta(
                HttpStatus.UNAUTHORIZED,
                "NAO_AUTENTICADO",
                "Autenticação necessária."
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> tratarAcessoNegado(
            AccessDeniedException exception
    ) {
        return resposta(
                HttpStatus.FORBIDDEN,
                "ACESSO_NEGADO",
                "Você não possui permissão para esta operação."
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> tratarErroInesperado(
            Exception exception
    ) {
        String erroId = UUID.randomUUID().toString();

        log.error(
                "Erro inesperado. erroId={}, tipo={}",
                erroId,
                exception.getClass().getName()
        );

        var problema = problema(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "ERRO_INTERNO",
                "Não foi possível concluir a operação."
        );

        problema.setProperty("erroId", erroId);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(problema);
    }

    private ResponseEntity<ProblemDetail> resposta(
            HttpStatus status,
            String codigo,
            String mensagem
    ) {
        return ResponseEntity
                .status(status)
                .body(problema(status, codigo, mensagem));
    }

    private ProblemDetail problema(
            HttpStatusCode status,
            String codigo,
            String mensagem
    ) {
        var problema = ProblemDetail.forStatusAndDetail(
                status,
                mensagem
        );

        problema.setProperty("codigo", codigo);

        return problema;
    }

}
