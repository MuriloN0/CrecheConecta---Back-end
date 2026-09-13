package com.crecheconecta.configuration;

import com.crecheconecta.escolar.application.exception.AlunoNaoEncontradoException;
import com.crecheconecta.escolar.application.exception.ConflitoVersaoException;
import com.crecheconecta.escolar.domain.exception.RegraNegocioException;
import com.crecheconecta.saude.application.exception.AcessoNegadoException;
import com.crecheconecta.saude.application.exception.FichaSaudeInvalidaException;
import com.crecheconecta.saude.application.exception.FichaSaudeNaoEncontradaException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.UUID;

@RestControllerAdvice
public class WebExceptionHandler {

    @ExceptionHandler(AlunoNaoEncontradoException.class)
    public ProblemDetail naoEncontrado(AlunoNaoEncontradoException exception) {
        return problema(
                HttpStatus.NOT_FOUND,
                "Aluno não encontrado",
                exception.getMessage()
        );
    }

    @ExceptionHandler(RegraNegocioException.class)
    public ProblemDetail regraNegocio(RegraNegocioException exception) {
        return problema(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Regra de negócio",
                exception.getMessage()
        );
    }

    @ExceptionHandler({
            ConflitoVersaoException.class,
            OptimisticLockingFailureException.class
    })
    public ProblemDetail conflitoVersao(Exception exception) {
        return problema(
                HttpStatus.CONFLICT,
                "Conflito de edição",
                "O cadastro foi alterado. Recarregue os dados e tente novamente."
        );
    }

    @ExceptionHandler(AcessoNegadoException.class)
    public ProblemDetail acessoNegado(AcessoNegadoException exception) {
        return problema(
                HttpStatus.FORBIDDEN,
                "Acesso negado",
                "Operação não permitida."
        );
    }

    @ExceptionHandler(FichaSaudeNaoEncontradaException.class)
    public ProblemDetail fichaNaoEncontrada(FichaSaudeNaoEncontradaException exception) {
        return problema(
                HttpStatus.NOT_FOUND,
                "Ficha não encontrada",
                "Ficha de saúde não encontrada."
        );
    }

    @ExceptionHandler(FichaSaudeInvalidaException.class)
    public ProblemDetail fichaInvalida(FichaSaudeInvalidaException exception) {
        return problema(
                HttpStatus.BAD_REQUEST,
                "Ficha inválida",
                exception.getMessage()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validacao(MethodArgumentNotValidException exception) {
        var erros = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(erro -> new ErroCampo(
                        erro.getField(),
                        erro.getDefaultMessage()
                ))
                .toList();

        var detalhe = problema(
                HttpStatus.BAD_REQUEST,
                "Requisição inválida",
                "Confira os campos enviados."
        );

        detalhe.setProperty("erros", erros);

        return detalhe;
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ProblemDetail formatoInvalido(Exception exception) {
        return problema(
                HttpStatus.BAD_REQUEST,
                "Formato inválido",
                "Confira o JSON, os identificadores e os parâmetros enviados."
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail integridade(DataIntegrityViolationException exception) {
        return problema(
                HttpStatus.CONFLICT,
                "Conflito de dados",
                "Não foi possível salvar o cadastro com os dados enviados."
        );
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail inesperado(Exception exception) {
        String correlacao = UUID.randomUUID().toString();
        var detalhe = problema(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Erro inesperado",
                "Ocorreu um erro inesperado. Informe o identificador ao suporte."
        );
        detalhe.setProperty("correlacao", correlacao);
        return detalhe;
    }

    private ProblemDetail problema(
            HttpStatus status,
            String titulo,
            String mensagem
    ) {
        var detalhe = ProblemDetail.forStatusAndDetail(status, mensagem);
        detalhe.setTitle(titulo);
        return detalhe;
    }

    public record ErroCampo(String campo, String mensagem) {
    }
}
