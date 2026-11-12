package com.crecheconecta.controller;

import com.crecheconecta.dto.AlunoResponse;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/alunos")
@PreAuthorize("hasRole('DIRECAO')")
public class AlunoController {

    private final AlunoService alunos;

    public AlunoController(AlunoService alunos) {

        this.alunos = alunos;
    }


    @GetMapping
    public ResponseEntity<Page<AlunoResponse>> listar(
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanho,
            @RequestParam(required = false) Boolean ativo,
            @AuthenticationPrincipal UsuarioAutenticado autenticado) {

        var resposta = alunos.listar(pagina, tamanho, ativo, UsuarioAtual.de(autenticado));

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }

    @GetMapping("/disponiveis")
    public ResponseEntity<Page<AlunoResponse>> listarDisponiveis(
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanho,
            @AuthenticationPrincipal UsuarioAutenticado autenticado) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(alunos.listarDisponiveis(pagina, tamanho, UsuarioAtual.de(autenticado)));
    }

    @GetMapping("/{alunoId}")
    public ResponseEntity<AlunoResponse> visualizar(@PathVariable UUID alunoId, @AuthenticationPrincipal UsuarioAutenticado autenticado) {

        var resposta = alunos.visualizar(alunoId, UsuarioAtual.de(autenticado));

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }


    @PostMapping("/{alunoId}/inativar")
    public ResponseEntity<AlunoResponse> inativar(@PathVariable UUID alunoId, @Valid @RequestBody InativarAlunoRequest request, @AuthenticationPrincipal UsuarioAutenticado autenticado) {

        var resposta = alunos.inativar(alunoId, request.versao(), UsuarioAtual.de(autenticado));

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }
}