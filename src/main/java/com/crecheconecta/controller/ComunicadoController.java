package com.crecheconecta.controller;

import com.crecheconecta.dto.*;
import com.crecheconecta.security.UsuarioAtual;
import com.crecheconecta.security.UsuarioAutenticado;
import com.crecheconecta.service.ComunicadoService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/comunicados")
public class ComunicadoController {

    private final ComunicadoService comunicados;

    public ComunicadoController(ComunicadoService comunicados) {

        this.comunicados = comunicados;
    }

    @PostMapping
    public ResponseEntity<ComunicadoResponse> criar(@Valid @RequestBody CriarComunicadoRequest request, @AuthenticationPrincipal UsuarioAutenticado autenticado) {

        var resposta = comunicados.criar(request, UsuarioAtual.de(autenticado));

        return ResponseEntity.created(URI.create("/api/comunicados/" + resposta.id())).cacheControl(CacheControl.noStore()).body(resposta);
    }

    @GetMapping
    public ResponseEntity<Page<ComunicadoResponse>> listar(@RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int tamanho, @AuthenticationPrincipal UsuarioAutenticado autenticado) {

        var resposta = comunicados.listar(pagina, tamanho, UsuarioAtual.de(autenticado));

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ComunicadoResponse> visualizar(@PathVariable UUID id, @AuthenticationPrincipal UsuarioAutenticado autenticado) {

        var resposta = comunicados.visualizar(id, UsuarioAtual.de(autenticado));

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ComunicadoResponse> atualizar(@PathVariable UUID id, @Valid @RequestBody AtualizarComunicadoRequest request, @AuthenticationPrincipal UsuarioAutenticado autenticado) {

        var resposta = comunicados.atualizar(id, request, UsuarioAtual.de(autenticado));

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }

    @PostMapping("/{id}/inativar")
    public ResponseEntity<ComunicadoResponse> inativar(@PathVariable UUID id, @Valid @RequestBody InativarComunicadoRequest request, @AuthenticationPrincipal UsuarioAutenticado autenticado) {

        var resposta = comunicados.inativar(id, request.versao(), UsuarioAtual.de(autenticado));

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }
}