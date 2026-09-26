package com.crecheconecta.controller;

import com.crecheconecta.dto.AtualizarFichaSaudeRequest;
import com.crecheconecta.dto.FichaSaudeRequest;
import com.crecheconecta.dto.FichaSaudeResponse;
import com.crecheconecta.dto.FichaSaudeResumoResponse;
import com.crecheconecta.security.UsuarioAtual;
import com.crecheconecta.security.UsuarioAutenticado;
import com.crecheconecta.service.FichaSaudeService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/alunos/{alunoId}/fichas-saude")
public class FichaSaudeController {

    private final FichaSaudeService service;

    public FichaSaudeController(FichaSaudeService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Void> cadastrar(
            @PathVariable UUID alunoId,
            @Valid @RequestBody FichaSaudeRequest dto,
            @AuthenticationPrincipal UsuarioAutenticado autenticado
    ) {
        var usuario = UsuarioAtual.de(autenticado);

        UUID id = service.cadastrar(
                alunoId,
                dto.paraDominio(),
                usuario
        );

        return ResponseEntity.created(
                URI.create("/api/alunos/" + alunoId + "/fichas-saude/" + id)
        ).build();
    }

    @GetMapping
    public List<FichaSaudeResumoResponse> listar(
            @PathVariable UUID alunoId,
            @AuthenticationPrincipal UsuarioAutenticado autenticado
    ) {
        return service.listar(
                alunoId,
                UsuarioAtual.de(autenticado)
        );
    }

    @GetMapping("/{fichaId}")
    public FichaSaudeResponse visualizar(
            @PathVariable UUID alunoId,
            @PathVariable UUID fichaId,
            @AuthenticationPrincipal UsuarioAutenticado autenticado
    ) {
        return service.visualizar(
                alunoId,
                fichaId,
                UsuarioAtual.de(autenticado)
        );
    }

    @PutMapping("/{fichaId}")
    public FichaSaudeResponse atualizar(
            @PathVariable UUID alunoId,
            @PathVariable UUID fichaId,
            @Valid @RequestBody AtualizarFichaSaudeRequest dto,
            @AuthenticationPrincipal UsuarioAutenticado autenticado
    ) {
        return service.atualizar(
                alunoId,
                fichaId,
                dto.paraDominio(),
                dto.versao(),
                UsuarioAtual.de(autenticado)
        );
    }

    @DeleteMapping("/{fichaId}")
    public ResponseEntity<Void> excluir(
            @PathVariable UUID alunoId,
            @PathVariable UUID fichaId,
            @AuthenticationPrincipal UsuarioAutenticado autenticado
    ) {
        service.excluir(
                alunoId,
                fichaId,
                UsuarioAtual.de(autenticado)
        );

        return ResponseEntity.noContent().build();
    }
}