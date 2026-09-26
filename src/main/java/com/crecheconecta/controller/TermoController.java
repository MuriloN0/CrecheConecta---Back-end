package com.crecheconecta.controller;

import com.crecheconecta.dto.TermoStatusResponse;
import com.crecheconecta.security.Perfil;
import com.crecheconecta.security.UsuarioAtual;
import com.crecheconecta.service.TermoService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
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

    private UsuarioAtual usuarioFake() {
        return new UsuarioAtual(
                UUID.fromString("22222222-2222-2222-2222-222222222222"), Perfil.RESPONSAVEL);
    }

    @GetMapping("/status")
    public TermoStatusResponse status() {
        UsuarioAtual usuario = usuarioFake();
        return new TermoStatusResponse(
                service.precisaAceitar(usuario.id()),
                service.versaoAtual(),
                service.termoUso(),
                service.politicaPrivacidade());
    }

    @PostMapping("/aceite")
    public ResponseEntity<Void> aceitar() {
        UsuarioAtual usuario = usuarioFake();
        service.registrarAceite(usuario.id());
        return ResponseEntity.noContent().build();
    }
}