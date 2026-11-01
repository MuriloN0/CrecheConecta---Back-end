package com.crecheconecta.controller;

import com.crecheconecta.dto.AtualizarTurmaRequest;
import com.crecheconecta.dto.CadastrarTurmaRequest;
import com.crecheconecta.dto.InativarTurmaRequest;
import com.crecheconecta.dto.TurmaResponse;
import com.crecheconecta.security.UsuarioAtual;
import com.crecheconecta.security.UsuarioAutenticado;
import com.crecheconecta.service.TurmaService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/turmas")
@PreAuthorize("hasRole('DIRECAO')")
public class TurmaController {

    private final TurmaService turmas;

    public TurmaController(TurmaService turmas) {

        this.turmas = turmas;
    }

    @PostMapping
    public ResponseEntity<TurmaResponse> cadastrar(@Valid @RequestBody CadastrarTurmaRequest request, @AuthenticationPrincipal UsuarioAutenticado autenticado) {

        var resposta = turmas.cadastrar(request, UsuarioAtual.de(autenticado));

        return ResponseEntity.created(URI.create("/api/turmas/" + resposta.id())).cacheControl(CacheControl.noStore()).body(resposta);
    }

    @GetMapping
    public ResponseEntity<Page<TurmaResponse>> listar(@RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int tamanho, @RequestParam(required = false) Boolean ativo, @AuthenticationPrincipal UsuarioAutenticado autenticado) {

        var resposta = turmas.listar(pagina, tamanho, ativo, UsuarioAtual.de(autenticado));

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }

    @GetMapping("/{turmaId}")
    public ResponseEntity<TurmaResponse> visualizar(@PathVariable UUID turmaId, @AuthenticationPrincipal UsuarioAutenticado autenticado) {

        var resposta = turmas.visualizar(turmaId, UsuarioAtual.de(autenticado));

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }

    @PutMapping("/{turmaId}")
    public ResponseEntity<TurmaResponse> atualizar(@PathVariable UUID turmaId, @Valid @RequestBody AtualizarTurmaRequest request, @AuthenticationPrincipal UsuarioAutenticado autenticado) {

        var resposta = turmas.atualizar(turmaId, request, UsuarioAtual.de(autenticado));

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }

    @PostMapping("/{turmaId}/inativar")
    public ResponseEntity<TurmaResponse> inativar(@PathVariable UUID turmaId, @Valid @RequestBody InativarTurmaRequest request, @AuthenticationPrincipal UsuarioAutenticado autenticado) {

        var resposta = turmas.inativar(turmaId, request.versao(), UsuarioAtual.de(autenticado));

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }
}