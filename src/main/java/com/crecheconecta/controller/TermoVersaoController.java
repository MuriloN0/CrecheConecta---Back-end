package com.crecheconecta.controller;

import com.crecheconecta.dto.AtualizarTermoVersaoRequest;
import com.crecheconecta.dto.PublicarTermoRequest;
import com.crecheconecta.dto.TermoVersaoRequest;
import com.crecheconecta.dto.TermoVersaoResponse;
import com.crecheconecta.security.UsuarioAtual;
import com.crecheconecta.security.UsuarioAutenticado;
import com.crecheconecta.service.TermoVersaoService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Gestão das versões dos termos de uso e da política de privacidade (só DIRECAO).
 * O usuário final lê a versão vigente em GET /api/termo/status (TermoController).
 */
@RestController
@RequestMapping("/api/termos")
@PreAuthorize("hasRole('DIRECAO')")
public class TermoVersaoController {

    private final TermoVersaoService termos;

    public TermoVersaoController(TermoVersaoService termos) {
        this.termos = termos;
    }

    @GetMapping
    public ResponseEntity<List<TermoVersaoResponse>> listar() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(termos.listar());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TermoVersaoResponse> visualizar(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(termos.visualizar(id));
    }

    @PostMapping
    public ResponseEntity<TermoVersaoResponse> criar(@Valid @RequestBody TermoVersaoRequest request, @AuthenticationPrincipal UsuarioAutenticado autenticado) {
        var resposta = termos.criar(request, UsuarioAtual.de(autenticado));
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(resposta);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TermoVersaoResponse> atualizar(@PathVariable UUID id, @Valid @RequestBody AtualizarTermoVersaoRequest request, @AuthenticationPrincipal UsuarioAutenticado autenticado) {
        var resposta = termos.atualizar(id, request, UsuarioAtual.de(autenticado));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id, @AuthenticationPrincipal UsuarioAutenticado autenticado) {
        termos.excluir(id, UsuarioAtual.de(autenticado));
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/{id}/publicar")
    public ResponseEntity<TermoVersaoResponse> publicar(@PathVariable UUID id, @Valid @RequestBody PublicarTermoRequest request, @AuthenticationPrincipal UsuarioAutenticado autenticado) {
        var resposta = termos.publicar(id, request.versao(), UsuarioAtual.de(autenticado));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }
}
