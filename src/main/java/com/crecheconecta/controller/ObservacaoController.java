package com.crecheconecta.controller;

import com.crecheconecta.dto.AtualizarObservacaoRequest;
import com.crecheconecta.dto.ObservacaoRequest;
import com.crecheconecta.dto.ObservacaoResponse;
import com.crecheconecta.security.UsuarioAtual;
import com.crecheconecta.security.UsuarioAutenticado;
import com.crecheconecta.service.ObservacaoService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/alunos/{alunoId}/observacoes")
@PreAuthorize("hasRole('RESPONSAVEL')")
public class ObservacaoController {

    private final ObservacaoService observacoes;

    public ObservacaoController(ObservacaoService observacoes) {
        this.observacoes = observacoes;
    }

    @PostMapping
    public ResponseEntity<Void> cadastrar(@PathVariable UUID alunoId, @Valid @RequestBody ObservacaoRequest request, @AuthenticationPrincipal UsuarioAutenticado autenticado) {
        UUID id = observacoes.cadastrar(alunoId, request.paraDominio(), UsuarioAtual.de(autenticado));
        return ResponseEntity.created(URI.create("/api/alunos/" + alunoId + "/observacoes/" + id)).cacheControl(CacheControl.noStore()).build();
    }

    @GetMapping
    public ResponseEntity<List<ObservacaoResponse>> listar(@PathVariable UUID alunoId, @AuthenticationPrincipal UsuarioAutenticado autenticado) {
        var resposta = observacoes.listar(alunoId, UsuarioAtual.de(autenticado));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }

    @GetMapping("/{observacaoId}")
    public ResponseEntity<ObservacaoResponse> visualizar(@PathVariable UUID alunoId, @PathVariable UUID observacaoId, @AuthenticationPrincipal UsuarioAutenticado autenticado) {
        var resposta = observacoes.visualizar(alunoId, observacaoId, UsuarioAtual.de(autenticado));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }

    @PutMapping("/{observacaoId}")
    public ResponseEntity<ObservacaoResponse> atualizar(@PathVariable UUID alunoId, @PathVariable UUID observacaoId, @Valid @RequestBody AtualizarObservacaoRequest request, @AuthenticationPrincipal UsuarioAutenticado autenticado) {
        var resposta = observacoes.atualizar(alunoId, observacaoId, request.paraDominio(), request.versao(), UsuarioAtual.de(autenticado));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }

    @DeleteMapping("/{observacaoId}")
    public ResponseEntity<Void> excluir(@PathVariable UUID alunoId, @PathVariable UUID observacaoId, @AuthenticationPrincipal UsuarioAutenticado autenticado) {
        observacoes.excluir(alunoId, observacaoId, UsuarioAtual.de(autenticado));
        return ResponseEntity.noContent().build();
    }
}
