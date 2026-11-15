package com.crecheconecta.controller;

import com.crecheconecta.dto.AtualizarInformacaoRelevanteRequest;
import com.crecheconecta.dto.InformacaoRelevanteRequest;
import com.crecheconecta.dto.InformacaoRelevanteResponse;
import com.crecheconecta.dto.InformacaoRelevanteResumoResponse;
import com.crecheconecta.security.UsuarioAtual;
import com.crecheconecta.security.UsuarioAutenticado;
import com.crecheconecta.service.InformacaoRelevanteService;
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

/**
 * Informações relevantes (alergias, medicamentos e outros cuidados) do aluno.
 * Escrita: só o responsável. Leitura: responsável, direção e professor (a regra fina fica no service).
 */
@RestController
@RequestMapping("/api/alunos/{alunoId}/informacoes-relevantes")
public class InformacaoRelevanteController {

    private static final String LEITURA = "hasAnyRole('RESPONSAVEL', 'DIRECAO', 'PROFESSOR')";
    private static final String ESCRITA = "hasRole('RESPONSAVEL')";

    private final InformacaoRelevanteService informacoes;

    public InformacaoRelevanteController(InformacaoRelevanteService informacoes) {
        this.informacoes = informacoes;
    }

    @PostMapping
    @PreAuthorize(ESCRITA)
    public ResponseEntity<Void> cadastrar(@PathVariable UUID alunoId, @Valid @RequestBody InformacaoRelevanteRequest request, @AuthenticationPrincipal UsuarioAutenticado autenticado) {
        UUID id = informacoes.cadastrar(alunoId, request.paraDominio(), UsuarioAtual.de(autenticado));
        return ResponseEntity.created(URI.create("/api/alunos/" + alunoId + "/informacoes-relevantes/" + id)).cacheControl(CacheControl.noStore()).build();
    }

    @GetMapping
    @PreAuthorize(LEITURA)
    public ResponseEntity<List<InformacaoRelevanteResumoResponse>> listar(@PathVariable UUID alunoId, @AuthenticationPrincipal UsuarioAutenticado autenticado) {
        var resposta = informacoes.listar(alunoId, UsuarioAtual.de(autenticado));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }

    @GetMapping("/{informacaoId}")
    @PreAuthorize(LEITURA)
    public ResponseEntity<InformacaoRelevanteResponse> visualizar(@PathVariable UUID alunoId, @PathVariable UUID informacaoId, @AuthenticationPrincipal UsuarioAutenticado autenticado) {
        var resposta = informacoes.visualizar(alunoId, informacaoId, UsuarioAtual.de(autenticado));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }

    @PutMapping("/{informacaoId}")
    @PreAuthorize(ESCRITA)
    public ResponseEntity<InformacaoRelevanteResponse> atualizar(@PathVariable UUID alunoId, @PathVariable UUID informacaoId, @Valid @RequestBody AtualizarInformacaoRelevanteRequest request, @AuthenticationPrincipal UsuarioAutenticado autenticado) {
        var resposta = informacoes.atualizar(alunoId, informacaoId, request.paraDominio(), request.versao(), UsuarioAtual.de(autenticado));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }

    @DeleteMapping("/{informacaoId}")
    @PreAuthorize(ESCRITA)
    public ResponseEntity<Void> excluir(@PathVariable UUID alunoId, @PathVariable UUID informacaoId, @AuthenticationPrincipal UsuarioAutenticado autenticado) {
        informacoes.excluir(alunoId, informacaoId, UsuarioAtual.de(autenticado));
        return ResponseEntity.noContent().build();
    }
}
