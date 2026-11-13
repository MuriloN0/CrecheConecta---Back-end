package com.crecheconecta.controller;

import com.crecheconecta.dto.AtualizarMalaVirtualRequest;
import com.crecheconecta.dto.CriarMalaVirtualRequest;
import com.crecheconecta.dto.MalaVirtualTurmaResponse;
import com.crecheconecta.dto.ProgressoMalaAlunoResponse;
import com.crecheconecta.security.UsuarioAtual;
import com.crecheconecta.security.UsuarioAutenticado;
import com.crecheconecta.service.MalaVirtualService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/turmas/{turmaId}/malas")
public class MalaVirtualController {

    private final MalaVirtualService malas;

    public MalaVirtualController(MalaVirtualService malas) {

        this.malas = malas;
    }

    @PostMapping
    public ResponseEntity<MalaVirtualTurmaResponse> criar(@PathVariable UUID turmaId, @Valid @RequestBody CriarMalaVirtualRequest request, @AuthenticationPrincipal UsuarioAutenticado autenticado) {

        var resposta = malas.criar(turmaId, request, UsuarioAtual.de(autenticado));

        return ResponseEntity.created(URI.create("/api/turmas/" + turmaId + "/malas/" + resposta.id())).cacheControl(CacheControl.noStore()).body(resposta);
    }

    @GetMapping
    public ResponseEntity<List<MalaVirtualTurmaResponse>> listar(@PathVariable UUID turmaId, @AuthenticationPrincipal UsuarioAutenticado autenticado) {

        var resposta = malas.listar(turmaId, UsuarioAtual.de(autenticado));

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }

    @GetMapping("/{malaId}")
    public ResponseEntity<MalaVirtualTurmaResponse> visualizar(@PathVariable UUID turmaId, @PathVariable UUID malaId, @AuthenticationPrincipal UsuarioAutenticado autenticado) {

        var resposta = malas.visualizar(turmaId, malaId, UsuarioAtual.de(autenticado));

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }

    @GetMapping("/{malaId}/progresso")
    public ResponseEntity<List<ProgressoMalaAlunoResponse>> progresso(@PathVariable UUID turmaId, @PathVariable UUID malaId, @AuthenticationPrincipal UsuarioAutenticado autenticado) {

        var resposta = malas.progresso(turmaId, malaId, UsuarioAtual.de(autenticado));

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }

    @PutMapping("/{malaId}")
    public ResponseEntity<MalaVirtualTurmaResponse> atualizar(
            @PathVariable UUID turmaId,
            @PathVariable UUID malaId,
            @Valid @RequestBody AtualizarMalaVirtualRequest request,
            @AuthenticationPrincipal UsuarioAutenticado autenticado) {

        var resposta = malas.atualizar(turmaId, malaId, request, UsuarioAtual.de(autenticado));

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }

    @DeleteMapping("/{malaId}")
    public ResponseEntity<Void> excluir(@PathVariable UUID turmaId, @PathVariable UUID malaId, @AuthenticationPrincipal UsuarioAutenticado autenticado) {

        malas.excluir(turmaId, malaId, UsuarioAtual.de(autenticado));

        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
