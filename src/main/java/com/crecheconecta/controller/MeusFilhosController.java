package com.crecheconecta.controller;

import com.crecheconecta.dto.FilhoResponse;
import com.crecheconecta.security.UsuarioAtual;
import com.crecheconecta.security.UsuarioAutenticado;
import com.crecheconecta.service.MeusFilhosService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/meus-filhos")
@PreAuthorize("hasRole('RESPONSAVEL')")
public class MeusFilhosController {

    private final MeusFilhosService meusFilhos;

    public MeusFilhosController(MeusFilhosService meusFilhos) {
        this.meusFilhos = meusFilhos;
    }

    @GetMapping
    public ResponseEntity<List<FilhoResponse>> listar(@AuthenticationPrincipal UsuarioAutenticado autenticado) {
        var resposta = meusFilhos.listar(UsuarioAtual.de(autenticado));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }

    @GetMapping("/{alunoId}")
    public ResponseEntity<FilhoResponse> visualizar(@PathVariable UUID alunoId, @AuthenticationPrincipal UsuarioAutenticado autenticado) {
        var resposta = meusFilhos.visualizar(alunoId, UsuarioAtual.de(autenticado));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }
}
