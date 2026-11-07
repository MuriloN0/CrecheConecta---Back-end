package com.crecheconecta.controller;

import com.crecheconecta.dto.AtenderSolicitacaoMaterialRequest;
import com.crecheconecta.dto.AtualizarSolicitacaoMaterialRequest;
import com.crecheconecta.dto.CancelarSolicitacaoMaterialRequest;
import com.crecheconecta.dto.CriarSolicitacaoMaterialRequest;
import com.crecheconecta.dto.EnviarMensagemSolicitacaoMaterialRequest;
import com.crecheconecta.dto.RecusarSolicitacaoMaterialRequest;
import com.crecheconecta.dto.SolicitacaoMaterialDetalheResponse;
import com.crecheconecta.dto.SolicitacaoMaterialResponse;
import com.crecheconecta.entity.PrioridadeSolicitacaoMaterial;
import com.crecheconecta.entity.StatusSolicitacaoMaterial;
import com.crecheconecta.security.UsuarioAtual;
import com.crecheconecta.security.UsuarioAutenticado;
import com.crecheconecta.service.SolicitacaoMaterialService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/solicitacoes-materiais")
public class SolicitacaoMaterialController {

    private final SolicitacaoMaterialService solicitacoes;

    public SolicitacaoMaterialController(SolicitacaoMaterialService solicitacoes) {

        this.solicitacoes = solicitacoes;
    }

    @PostMapping
    public ResponseEntity<SolicitacaoMaterialResponse> criar(
            @Valid @RequestBody CriarSolicitacaoMaterialRequest request,
            @AuthenticationPrincipal UsuarioAutenticado autenticado
    ) {

        SolicitacaoMaterialResponse resposta = solicitacoes.criar(request, UsuarioAtual.de(autenticado));

        return ResponseEntity.created(URI.create("/api/solicitacoes-materiais/" + resposta.id()))
                .cacheControl(CacheControl.noStore())
                .body(resposta);
    }

    @GetMapping
    public ResponseEntity<Page<SolicitacaoMaterialResponse>> listar(
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanho,
            @RequestParam(required = false) StatusSolicitacaoMaterial status,
            @RequestParam(required = false) PrioridadeSolicitacaoMaterial prioridade,
            @AuthenticationPrincipal UsuarioAutenticado autenticado
    ) {

        Page<SolicitacaoMaterialResponse> resposta = solicitacoes.listar(
                pagina,
                tamanho,
                status,
                prioridade,
                UsuarioAtual.de(autenticado)
        );

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SolicitacaoMaterialDetalheResponse> visualizar(
            @PathVariable UUID id,
            @AuthenticationPrincipal UsuarioAutenticado autenticado
    ) {

        SolicitacaoMaterialDetalheResponse resposta = solicitacoes.visualizar(id, UsuarioAtual.de(autenticado));

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }

    @PutMapping("/{id}")
    public ResponseEntity<SolicitacaoMaterialResponse> atualizar(
            @PathVariable UUID id,
            @Valid @RequestBody AtualizarSolicitacaoMaterialRequest request,
            @AuthenticationPrincipal UsuarioAutenticado autenticado
    ) {

        SolicitacaoMaterialResponse resposta = solicitacoes.atualizar(id, request, UsuarioAtual.de(autenticado));

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }

    @PostMapping("/{id}/mensagens")
    public ResponseEntity<SolicitacaoMaterialDetalheResponse> enviarMensagem(
            @PathVariable UUID id,
            @Valid @RequestBody EnviarMensagemSolicitacaoMaterialRequest request,
            @AuthenticationPrincipal UsuarioAutenticado autenticado
    ) {

        SolicitacaoMaterialDetalheResponse resposta = solicitacoes.enviarMensagem(id, request, UsuarioAtual.de(autenticado));

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }

    @PostMapping("/{id}/atender")
    public ResponseEntity<SolicitacaoMaterialResponse> atender(
            @PathVariable UUID id,
            @Valid @RequestBody AtenderSolicitacaoMaterialRequest request,
            @AuthenticationPrincipal UsuarioAutenticado autenticado
    ) {

        SolicitacaoMaterialResponse resposta = solicitacoes.atender(id, request, UsuarioAtual.de(autenticado));

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }

    @PostMapping("/{id}/recusar")
    public ResponseEntity<SolicitacaoMaterialResponse> recusar(
            @PathVariable UUID id,
            @Valid @RequestBody RecusarSolicitacaoMaterialRequest request,
            @AuthenticationPrincipal UsuarioAutenticado autenticado
    ) {

        SolicitacaoMaterialResponse resposta = solicitacoes.recusar(id, request, UsuarioAtual.de(autenticado));

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }

    @PostMapping("/{id}/cancelar")
    public ResponseEntity<SolicitacaoMaterialResponse> cancelar(
            @PathVariable UUID id,
            @Valid @RequestBody CancelarSolicitacaoMaterialRequest request,
            @AuthenticationPrincipal UsuarioAutenticado autenticado
    ) {

        SolicitacaoMaterialResponse resposta = solicitacoes.cancelar(id, request, UsuarioAtual.de(autenticado));

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }
}
