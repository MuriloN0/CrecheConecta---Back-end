package com.crecheconecta.controller;

import com.crecheconecta.dto.AlunoResponse;
import com.crecheconecta.dto.AtualizarAlunoRequest;
import com.crecheconecta.dto.CadastrarAlunoRequest;
import com.crecheconecta.dto.InativarAlunoRequest;
import com.crecheconecta.security.UsuarioAtual;
import com.crecheconecta.security.UsuarioAutenticado;
import com.crecheconecta.service.AlunoService;
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
@RequestMapping("/api/alunos")
@PreAuthorize("hasRole('DIRECAO')")
public class AlunoController {

    private final AlunoService alunos;

    public AlunoController(AlunoService alunos) {

        this.alunos = alunos;
    }

    @PostMapping
    public ResponseEntity<AlunoResponse> cadastrar(@Valid @RequestBody CadastrarAlunoRequest request, @AuthenticationPrincipal UsuarioAutenticado autenticado) {

        var resposta = alunos.cadastrar(request, UsuarioAtual.de(autenticado));

        return ResponseEntity.created(URI.create("/api/alunos/" + resposta.id())).cacheControl(CacheControl.noStore()).body(resposta);
    }

    @GetMapping
    public ResponseEntity<Page<AlunoResponse>> listar(@RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int tamanho, @RequestParam(required = false) Boolean ativo, @AuthenticationPrincipal UsuarioAutenticado autenticado) {

        var resposta = alunos.listar(pagina, tamanho, ativo, UsuarioAtual.de(autenticado));

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }

    @GetMapping("/{alunoId}")
    public ResponseEntity<AlunoResponse> visualizar(@PathVariable UUID alunoId, @AuthenticationPrincipal UsuarioAutenticado autenticado) {

        var resposta = alunos.visualizar(alunoId, UsuarioAtual.de(autenticado));

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }

    @PutMapping("/{alunoId}")
    public ResponseEntity<AlunoResponse> atualizar(@PathVariable UUID alunoId, @Valid @RequestBody AtualizarAlunoRequest request, @AuthenticationPrincipal UsuarioAutenticado autenticado) {

        var resposta = alunos.atualizar(alunoId, request, UsuarioAtual.de(autenticado));

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }

    @PostMapping("/{alunoId}/inativar")
    public ResponseEntity<AlunoResponse> inativar(@PathVariable UUID alunoId, @Valid @RequestBody InativarAlunoRequest request, @AuthenticationPrincipal UsuarioAutenticado autenticado) {

        var resposta = alunos.inativar(alunoId, request.versao(), UsuarioAtual.de(autenticado));

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }
}