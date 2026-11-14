package com.crecheconecta.controller;

import com.crecheconecta.dto.TermoStatusResponse;
import com.crecheconecta.security.UsuarioAtual;
import com.crecheconecta.security.UsuarioAutenticado;
import com.crecheconecta.service.TermoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    public ResponseEntity<TermoStatusResponse> status(@AuthenticationPrincipal UsuarioAutenticado autenticado) {

        var usuario = UsuarioAtual.de(autenticado);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.status(usuario.id()));
    }

    @PostMapping("/aceite")
    public ResponseEntity<Void> aceitar(@AuthenticationPrincipal UsuarioAutenticado autenticado, @Valid @RequestBody AceitarTermoRequest request) {

        var usuario = UsuarioAtual.de(autenticado);
        service.registrarAceite(usuario.id(), request.versao());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    public record AceitarTermoRequest(@NotBlank @Size(max = 40) String versao) {}
}