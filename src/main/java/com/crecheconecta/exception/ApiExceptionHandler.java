package com.crecheconecta.exception;

import com.crecheconecta.service.AuditoriaService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final AuditoriaService auditoria;

    public ApiExceptionHandler(AuditoriaService auditoria) {
        this.auditoria = auditoria;
    }

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

    @ExceptionHandler({
            AccessDeniedException.class,
            AcessoNegadoException.class
    })
    public ResponseEntity<ProblemDetail> tratarAcessoNegado(
            Exception exception
    ) {
        registrarAuditoria("ERRO_ACESSO_NEGADO");

        return resposta(
                HttpStatus.FORBIDDEN,
                "ACESSO_NEGADO",
                "Você não possui permissão para esta operação."
        );
    }

    @ExceptionHandler(RegraNegocioException.class)
    public ResponseEntity<ProblemDetail> tratarRegraNegocio(
            RegraNegocioException exception
    ) {
        registrarAuditoria("ERRO_REGRA_NEGOCIO");

        return resposta(
                HttpStatus.BAD_REQUEST,
                "REGRA_NEGOCIO",
                exception.getMessage()
        );
    }

    @ExceptionHandler(FichaSaudeNaoEncontradaException.class)
    public ResponseEntity<ProblemDetail> tratarFichaNaoEncontrada(
            FichaSaudeNaoEncontradaException exception
    ) {
        registrarAuditoria("ERRO_NAO_ENCONTRADA");

        return resposta(
                HttpStatus.NOT_FOUND,
                "FICHA_SAUDE_NAO_ENCONTRADA",
                "Ficha de saúde não encontrada."
        );
    }

    @ExceptionHandler(AtividadeNaoEncontradaException.class)
    public ResponseEntity<ProblemDetail> tratarAtividadeNaoEncontrada(
            AtividadeNaoEncontradaException exception
    ) {
        registrarAuditoria("ERRO_NAO_ENCONTRADA");

        return resposta(
                HttpStatus.NOT_FOUND,
                "ATIVIDADE_NAO_ENCONTRADA",
                "Atividade não encontrada."
        );
    }

    @ExceptionHandler({
            ConflitoVersaoException.class,
            OptimisticLockingFailureException.class
    })
    public ResponseEntity<ProblemDetail> tratarConflitoVersao(
            Exception exception
    ) {
        registrarAuditoria("ERRO_CONFLITO_VERSAO");

        return resposta(
                HttpStatus.CONFLICT,
                "CONFLITO_VERSAO",
                "Cadastro alterado por outra pessoa. Recarregue os dados."
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> tratarIntegridade(
            DataIntegrityViolationException exception
    ) {
        registrarAuditoria("ERRO_INTEGRIDADE");

        return resposta(
                HttpStatus.CONFLICT,
                "CONFLITO_DADOS",
                "Não foi possível salvar o cadastro com os dados enviados."
        );
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        registrarAuditoria("ERRO_ENTRADA_INVALIDA");

        var detalhe = problema(
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

        detalhe.setProperty("campos", campos);

        return handleExceptionInternal(
                exception, detalhe, headers, status, request
        );
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        var detalhe = problema(
                status,
                "REQUISICAO_INVALIDA",
                "O corpo da requisição está ausente ou possui formato inválido."
        );

        return handleExceptionInternal(
                exception, detalhe, headers, status, request
        );
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        var detalhe = problema(
                status,
                "PARAMETRO_INVALIDO",
                "Confira os identificadores e os parâmetros enviados."
        );

        return handleExceptionInternal(
                exception, detalhe, headers, status, request
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

        var detalhe = problema(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "ERRO_INTERNO",
                "Não foi possível concluir a operação."
        );

        detalhe.setProperty("erroId", erroId);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(detalhe);
    }

    private void registrarAuditoria(String acao) {
        try {
            auditoria.registrar(acao, null, null);
        } catch (RuntimeException exception) {
            // Uma falha de auditoria não deve substituir o erro original.
            log.error(
                    "Falha ao registrar auditoria. acao={}, tipo={}",
                    acao,
                    exception.getClass().getName()
            );
        }
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
        var detalhe = ProblemDetail.forStatusAndDetail(status, mensagem);

        detalhe.setProperty("codigo", codigo);

        // Compatibilidade com clientes que liam "mensagem" na develop.
        detalhe.setProperty("mensagem", mensagem);

        return detalhe;
    }
}