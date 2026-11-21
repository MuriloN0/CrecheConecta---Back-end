package com.crecheconecta.controller;

import com.crecheconecta.security.UsuarioAtual;
import com.crecheconecta.security.UsuarioAutenticado;
import com.crecheconecta.service.TermoService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/perfil/termos")
@PreAuthorize("hasRole('RESPONSAVEL')")
public class RevogacaoTermoController {

    private final TermoService termos;
    private final boolean cookieSeguro;

    public RevogacaoTermoController(
            TermoService termos,
            @Value("${app.auth.refresh-cookie-secure:true}") boolean cookieSeguro
    ) {
        this.termos = termos;
        this.cookieSeguro = cookieSeguro;
    }

    @PostMapping("/revogar")
    public ResponseEntity<Void> revogar(
            @AuthenticationPrincipal UsuarioAutenticado autenticado
    ) {
        termos.revogarAceite(UsuarioAtual.de(autenticado));

        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .header(
                        HttpHeaders.SET_COOKIE,
                        removerCookie("crecheconecta_sessao", "/api/auth"),
                        removerCookie("crecheconecta_refresh", "/api/auth/refresh")
                )
                .build();
    }

    private String removerCookie(String nome, String caminho) {
        return ResponseCookie.from(nome, "")
                .path(caminho)
                .maxAge(0)
                .httpOnly(true)
                .secure(cookieSeguro)
                .sameSite("Strict")
                .build()
                .toString();
    }
}