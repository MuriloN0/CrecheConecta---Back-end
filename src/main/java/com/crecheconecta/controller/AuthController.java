package com.crecheconecta.controller;

import com.crecheconecta.dto.ConfirmarLoginRequest;
import com.crecheconecta.dto.LoginRequest;
import com.crecheconecta.dto.LoginResponse;
import com.crecheconecta.dto.SessaoResponse;
import com.crecheconecta.service.AuthService;
import com.crecheconecta.service.ConfirmacaoLoginService;
import com.crecheconecta.service.SessaoService;
import com.crecheconecta.service.TokenSessaoService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String COOKIE_RENOVACAO = "crecheconecta_sessao";
    private static final String COOKIE_LEGADO = "crecheconecta_refresh";
    private static final String CAMINHO_COOKIE = "/api/auth";
    private static final String CAMINHO_LEGADO = "/api/auth/refresh";

    private final AuthService authService;
    private final ConfirmacaoLoginService confirmacaoLoginService;
    private final SessaoService sessaoService;
    private final TokenSessaoService tokens;
    private final boolean cookieSeguro;

    public AuthController(
            AuthService authService,
            ConfirmacaoLoginService confirmacaoLoginService,
            SessaoService sessaoService,
            TokenSessaoService tokens,
            @Value("${app.auth.refresh-cookie-secure:true}") boolean cookieSeguro
    ) {
        this.authService = authService;
        this.confirmacaoLoginService = confirmacaoLoginService;
        this.sessaoService = sessaoService;
        this.tokens = tokens;
        this.cookieSeguro = cookieSeguro;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(authService.iniciarLogin(request));
    }

    @PostMapping("/confirmar-login")
    public ResponseEntity<SessaoResponse> confirmarLogin(
            @Valid @RequestBody ConfirmarLoginRequest request,
            @RequestHeader(value = "X-CrecheConecta-Refresh", required = false) String renovacao,
            @CookieValue(value = COOKIE_RENOVACAO, required = false) String refreshAnterior
    ) {
        if (!"1".equals(renovacao)) {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(confirmacaoLoginService.confirmar(request));
        }

        String refreshToken = tokens.gerar();
        SessaoResponse sessao = confirmacaoLoginService.confirmar(request, refreshToken, refreshAnterior);

        return comCookieRenovado(sessao, refreshToken);
    }

    @PostMapping("/refresh")
    public ResponseEntity<SessaoResponse> renovar(
            @RequestHeader(value = "X-CrecheConecta-Refresh", required = false) String renovacao,
            @CookieValue(value = COOKIE_RENOVACAO, required = false) String refreshToken,
            @CookieValue(value = COOKIE_LEGADO, required = false) String refreshLegado
    ) {
        if (!"1".equals(renovacao)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .cacheControl(CacheControl.noStore())
                    .build();
        }

        boolean migrarCookie = refreshToken == null && refreshLegado != null;
        var sessao = sessaoService.renovar(migrarCookie ? refreshLegado : refreshToken);

        if (sessao.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .cacheControl(CacheControl.noStore())
                    .build();
        }

        if (migrarCookie) {
            return comCookieRenovado(sessao.get(), refreshLegado);
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(sessao.get());
    }

    private ResponseEntity<SessaoResponse> comCookieRenovado(SessaoResponse sessao, String refreshToken) {
        String novo = cookie(COOKIE_RENOVACAO, refreshToken, CAMINHO_COOKIE).build().toString();
        String removerLegado = cookie(COOKIE_LEGADO, "", CAMINHO_LEGADO).maxAge(0).build().toString();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, novo, removerLegado)
                .body(sessao);
    }

    private ResponseCookie.ResponseCookieBuilder cookie(String nome, String valor, String caminho) {
        return ResponseCookie.from(nome, valor)
                .httpOnly(true)
                .secure(cookieSeguro)
                .sameSite("Strict")
                .path(caminho);
    }
}