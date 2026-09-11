package com.crecheconecta.configuration;

import com.crecheconecta.saude.application.exception.AcessoNegadoException;
import com.crecheconecta.saude.application.exception.FichaSaudeInvalidaException;
import com.crecheconecta.saude.application.exception.FichaSaudeNaoEncontradaException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;

@RestControllerAdvice
public class WebExceptionHandler {

    @ExceptionHandler(AcessoNegadoException.class)
    public ProblemDetail tratarAcessoNegado(AcessoNegadoException ex) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, "Operacao nao permitida.");
    }

    @ExceptionHandler(FichaSaudeNaoEncontradaException.class)
    public ProblemDetail tratarNaoEncontrada(FichaSaudeNaoEncontradaException ex) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, "Ficha de saude nao encontrada.");
    }

    @ExceptionHandler(FichaSaudeInvalidaException.class)
    public ProblemDetail tratarInvalida(FichaSaudeInvalidaException ex) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail tratarValidacao(MethodArgumentNotValidException ex) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Dados da ficha de saude invalidos.");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail tratarInesperada(Exception ex) {
        String correlacao = UUID.randomUUID().toString();
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocorreu um erro inesperado. Informe o identificador ao suporte.");
        problema.setProperty("correlacao", correlacao);
        return problema;
    }
}
