package com.crecheconecta.controller;

import com.crecheconecta.dto.AtualizarObservacaoRequest;
import com.crecheconecta.dto.ObservacaoRequest;
import com.crecheconecta.dto.ObservacaoResponse;
import com.crecheconecta.security.UsuarioAtual;
import com.crecheconecta.security.UsuarioAutenticado;
import com.crecheconecta.service.ObservacaoService;
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
@RequestMapping("/api/alunos/{alunoId}/observacoes")
public class ObservacaoController {

    private final ObservacaoService service;

    public ObservacaoController(ObservacaoService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Void> cadastrar(
            @PathVariable UUID alunoId,
            @Valid @RequestBody ObservacaoRequest dto,
            @AuthenticationPrincipal UsuarioAutenticado autenticado
    ) {
        UUID id = service.cadastrar(
                alunoId,
                dto.paraDominio(),
                UsuarioAtual.de(autenticado)
        );

        return ResponseEntity.created(
                URI.create("/api/alunos/" + alunoId + "/observacoes/" + id)
        ).build();
    }

    @GetMapping
    public List<ObservacaoResponse> listar(
            @PathVariable UUID alunoId,
            @AuthenticationPrincipal UsuarioAutenticado autenticado
    ) {
        return service.listar(alunoId, UsuarioAtual.de(autenticado));
    }

    @GetMapping("/{observacaoId}")
    public ObservacaoResponse visualizar(
            @PathVariable UUID alunoId,
            @PathVariable UUID observacaoId,
            @AuthenticationPrincipal UsuarioAutenticado autenticado
    ) {
        return service.visualizar(alunoId, observacaoId, UsuarioAtual.de(autenticado));
    }

    @PutMapping("/{observacaoId}")
    public ObservacaoResponse atualizar(
            @PathVariable UUID alunoId,
            @PathVariable UUID observacaoId,
            @Valid @RequestBody AtualizarObservacaoRequest dto,
            @AuthenticationPrincipal UsuarioAutenticado autenticado
    ) {
        return service.atualizar(
                alunoId,
                observacaoId,
                dto.paraDominio(),
                dto.versao(),
                UsuarioAtual.de(autenticado)
        );
    }

    @DeleteMapping("/{observacaoId}")
    public ResponseEntity<Void> excluir(
            @PathVariable UUID alunoId,
            @PathVariable UUID observacaoId,
            @AuthenticationPrincipal UsuarioAutenticado autenticado
    ) {
        service.excluir(alunoId, observacaoId, UsuarioAtual.de(autenticado));
        return ResponseEntity.noContent().build();
    }
}
