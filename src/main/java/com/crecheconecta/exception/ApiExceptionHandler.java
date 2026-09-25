package com.crecheconecta.exception;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    private ResponseEntity<Object> erro(int status, String mensagem) {
        return ResponseEntity.status(status).body(Map.of("status", status, "mensagem", mensagem));
    }

    @ExceptionHandler(RegraNegocioException.class)
    ResponseEntity<Object> regra(RegraNegocioException e) {
        return erro(400, e.getMessage());
    }

    @ExceptionHandler(AcessoNegadoException.class)
    ResponseEntity<Object> negado(AcessoNegadoException e) {
        return erro(403, e.getMessage());
    }

    @ExceptionHandler(FichaSaudeNaoEncontradaException.class)
    ResponseEntity<Object> naoEncontrada(FichaSaudeNaoEncontradaException e) {
        return erro(404, e.getMessage());
    }

    @ExceptionHandler({ConflitoVersaoException.class, ObjectOptimisticLockingFailureException.class})
    ResponseEntity<Object> conflito(RuntimeException e) {
        return erro(409, "Cadastro alterado por outra pessoa. Recarregue os dados.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Object> entradaInvalida(MethodArgumentNotValidException e) {
        return erro(400, "Dados de entrada inválidos.");
    }
}