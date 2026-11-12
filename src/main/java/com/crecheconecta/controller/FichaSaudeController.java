package com.crecheconecta.controller;

import com.crecheconecta.dto.AtualizarFichaSaudeRequest;
import com.crecheconecta.dto.FichaSaudeRequest;
import com.crecheconecta.dto.FichaSaudeResponse;
import com.crecheconecta.dto.FichaSaudeResumoResponse;
import com.crecheconecta.security.UsuarioAtual;
import com.crecheconecta.security.UsuarioAutenticado;
import com.crecheconecta.service.FichaSaudeService;
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
 * Informações relevantes (ficha de saúde) do aluno.
 * Escrita: só o responsável. Leitura: responsável, direção e professor (a regra fina fica no service).
 */
@RestController
@RequestMapping("/api/alunos/{alunoId}/fichas-saude")
public class FichaSaudeController {

    private static final String LEITURA = "hasAnyRole('RESPONSAVEL', 'DIRECAO', 'PROFESSOR')";
    private static final String ESCRITA = "hasRole('RESPONSAVEL')";

    private final FichaSaudeService fichas;

    public FichaSaudeController(FichaSaudeService fichas) {
        this.fichas = fichas;
    }

    @PostMapping
    @PreAuthorize(ESCRITA)
    public ResponseEntity<Void> cadastrar(@PathVariable UUID alunoId, @Valid @RequestBody FichaSaudeRequest request, @AuthenticationPrincipal UsuarioAutenticado autenticado) {
        UUID id = fichas.cadastrar(alunoId, request.paraDominio(), UsuarioAtual.de(autenticado));
        return ResponseEntity.created(URI.create("/api/alunos/" + alunoId + "/fichas-saude/" + id)).cacheControl(CacheControl.noStore()).build();
    }

    @GetMapping
    @PreAuthorize(LEITURA)
    public ResponseEntity<List<FichaSaudeResumoResponse>> listar(@PathVariable UUID alunoId, @AuthenticationPrincipal UsuarioAutenticado autenticado) {
        var resposta = fichas.listar(alunoId, UsuarioAtual.de(autenticado));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }

    @GetMapping("/{fichaId}")
    @PreAuthorize(LEITURA)
    public ResponseEntity<FichaSaudeResponse> visualizar(@PathVariable UUID alunoId, @PathVariable UUID fichaId, @AuthenticationPrincipal UsuarioAutenticado autenticado) {
        var resposta = fichas.visualizar(alunoId, fichaId, UsuarioAtual.de(autenticado));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }

    @PutMapping("/{fichaId}")
    @PreAuthorize(ESCRITA)
    public ResponseEntity<FichaSaudeResponse> atualizar(
            @PathVariable UUID alunoId,
            @PathVariable UUID fichaId,
            @Valid @RequestBody AtualizarFichaSaudeRequest request,
            @AuthenticationPrincipal UsuarioAutenticado autenticado) {
        var resposta = fichas.atualizar(alunoId, fichaId, request.paraDominio(), request.versao(), UsuarioAtual.de(autenticado));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }

    @DeleteMapping("/{fichaId}")
    @PreAuthorize(ESCRITA)
    public ResponseEntity<Void> excluir(@PathVariable UUID alunoId, @PathVariable UUID fichaId, @AuthenticationPrincipal UsuarioAutenticado autenticado) {
        fichas.excluir(alunoId, fichaId, UsuarioAtual.de(autenticado));
        return ResponseEntity.noContent().build();
    }
}
