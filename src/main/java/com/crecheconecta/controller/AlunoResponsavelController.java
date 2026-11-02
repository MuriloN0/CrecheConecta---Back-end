package com.crecheconecta.controller;

import com.crecheconecta.dto.AlunoResponsavelResponse;
import com.crecheconecta.dto.VincularResponsavelRequest;
import com.crecheconecta.security.UsuarioAtual;
import com.crecheconecta.security.UsuarioAutenticado;
import com.crecheconecta.service.AlunoResponsavelService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/alunos/{alunoId}/responsaveis")
@PreAuthorize("hasRole('DIRECAO')")
public class AlunoResponsavelController {

    private final AlunoResponsavelService service;

    public AlunoResponsavelController(AlunoResponsavelService service) {

        this.service = service;
    }

    @PostMapping
    public ResponseEntity<AlunoResponsavelResponse> vincular(@PathVariable UUID alunoId, @Valid @RequestBody VincularResponsavelRequest request, @AuthenticationPrincipal UsuarioAutenticado autenticado) {

        var resposta = service.vincular(alunoId, request.responsavelId(), UsuarioAtual.de(autenticado));

        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(resposta);
    }

    @GetMapping
    public ResponseEntity<List<AlunoResponsavelResponse>> listar(@PathVariable UUID alunoId, @AuthenticationPrincipal UsuarioAutenticado autenticado) {

        var resposta = service.listar(alunoId, UsuarioAtual.de(autenticado));

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }

    @PostMapping("/{vinculoId}/encerrar")
    public ResponseEntity<AlunoResponsavelResponse> encerrar(@PathVariable UUID alunoId, @PathVariable UUID vinculoId, @AuthenticationPrincipal UsuarioAutenticado autenticado) {

        var resposta = service.encerrar(alunoId, vinculoId, UsuarioAtual.de(autenticado));

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }
}