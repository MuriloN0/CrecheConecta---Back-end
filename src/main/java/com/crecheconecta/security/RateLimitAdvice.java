package com.crecheconecta.security;

import com.crecheconecta.dto.ConfirmarLoginRequest;
import com.crecheconecta.dto.ConfirmarRecuperacaoRequest;
import com.crecheconecta.dto.LoginRequest;
import com.crecheconecta.dto.RedefinirSenhaRequest;
import com.crecheconecta.dto.SolicitarRecuperacaoRequest;
import com.crecheconecta.service.RateLimitService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import java.lang.reflect.Type;
import java.util.Locale;
import java.util.Set;

import static com.crecheconecta.service.RateLimitService.Politica.*;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(prefix = "app.rate-limit", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitAdvice extends RequestBodyAdviceAdapter {

    private static final Set<Class<?>> TIPOS = Set.of(LoginRequest.class, SolicitarRecuperacaoRequest.class, ConfirmarLoginRequest.class, ConfirmarRecuperacaoRequest.class, RedefinirSenhaRequest.class);

    private final RateLimitService limites;

    public RateLimitAdvice(RateLimitService limites) {

        this.limites = limites;
    }

    @Override
    public boolean supports(MethodParameter parametro, Type tipo, Class<? extends HttpMessageConverter<?>> conversor) {

        return TIPOS.contains(parametro.getParameterType());
    }

    @Override
    public Object afterBodyRead(Object corpo, HttpInputMessage mensagem, MethodParameter parametro, Type tipo, Class<? extends HttpMessageConverter<?>> conversor) {

        if (corpo instanceof LoginRequest dados && dados.email() != null) {

            limites.verificar(LOGIN_EMAIL, normalizar(dados.email()));

        } else if (corpo instanceof SolicitarRecuperacaoRequest dados && dados.email() != null) {

            limites.verificar(RECUPERACAO_EMAIL, normalizar(dados.email()));

        } else if (corpo instanceof ConfirmarLoginRequest dados && dados.acaoId() != null) {

            limites.verificar(ACAO_VERIFICACAO, "login:" + dados.acaoId());

        } else if (corpo instanceof ConfirmarRecuperacaoRequest dados && dados.acaoId() != null) {

            limites.verificar(ACAO_VERIFICACAO, "recuperacao:" + dados.acaoId());

        } else if (corpo instanceof RedefinirSenhaRequest dados && dados.redefinicaoId() != null) {

            limites.verificar(ACAO_VERIFICACAO, "redefinicao:" + dados.redefinicaoId());
        }

        return corpo;
    }

    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<ProblemDetail> tratar(RateLimitException exception) {

        var problema = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, exception.getMessage());

        problema.setProperty("codigo", "RATE_LIMIT_EXCEDIDO");
        problema.setProperty("segundosRestantes", exception.getSegundosRestantes());

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(exception.getSegundosRestantes()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(problema);
    }

    private String normalizar(String email) {

        return email.strip().toLowerCase(Locale.ROOT);
    }
}