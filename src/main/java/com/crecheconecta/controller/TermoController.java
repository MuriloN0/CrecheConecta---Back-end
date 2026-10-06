package com.crecheconecta.controller;

import com.crecheconecta.dto.TermoStatusResponse;
import com.crecheconecta.security.UsuarioAtual;
import com.crecheconecta.security.UsuarioAutenticado;
import com.crecheconecta.service.TermoService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/termo")
public class TermoController {

    private final TermoService service;

    public TermoController(TermoService service) {
        this.service = service;
    }

    @GetMapping("/status")
    public TermoStatusResponse status(
            @AuthenticationPrincipal UsuarioAutenticado autenticado
    ) {
        var usuario = UsuarioAtual.de(autenticado);

        return new TermoStatusResponse(
                service.precisaAceitar(usuario.id()),
                service.versaoAtual(),
                service.termoUso(),
                service.politicaPrivacidade()
        );
    }

    @PostMapping("/aceite")
    public ResponseEntity<Void> aceitar(
            @AuthenticationPrincipal UsuarioAutenticado autenticado
    ) {
        var usuario = UsuarioAtual.de(autenticado);

        service.registrarAceite(usuario.id());

        return ResponseEntity.noContent().build();
    }
}