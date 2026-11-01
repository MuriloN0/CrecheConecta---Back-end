package com.crecheconecta.controller;

import com.crecheconecta.dto.TurmaProfessorResponse;
import com.crecheconecta.dto.VincularProfessorTurmaRequest;
import com.crecheconecta.security.UsuarioAtual;
import com.crecheconecta.security.UsuarioAutenticado;
import com.crecheconecta.service.TurmaProfessorService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/turmas/{turmaId}/professores")
@PreAuthorize("hasRole('DIRECAO')")
public class TurmaProfessorController {

    private final TurmaProfessorService service;

    public TurmaProfessorController(TurmaProfessorService service) {

        this.service = service;
    }

    @PostMapping
    public ResponseEntity<TurmaProfessorResponse> vincular(@PathVariable UUID turmaId, @Valid @RequestBody VincularProfessorTurmaRequest request, @AuthenticationPrincipal UsuarioAutenticado autenticado) {

        var resposta = service.vincular(turmaId, request.professorId(), UsuarioAtual.de(autenticado));

        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(resposta);
    }

    @GetMapping
    public ResponseEntity<Page<TurmaProfessorResponse>> listar(@PathVariable UUID turmaId, @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int tamanho, @RequestParam(defaultValue = "false") boolean incluirEncerrados, @AuthenticationPrincipal UsuarioAutenticado autenticado) {

        var resposta = service.listar(turmaId, pagina, tamanho, incluirEncerrados, UsuarioAtual.de(autenticado));

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }

    @PostMapping("/{vinculoId}/encerrar")
    public ResponseEntity<TurmaProfessorResponse> encerrar(@PathVariable UUID turmaId, @PathVariable UUID vinculoId, @AuthenticationPrincipal UsuarioAutenticado autenticado) {

        var resposta = service.encerrar(turmaId, vinculoId, UsuarioAtual.de(autenticado));

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }
}