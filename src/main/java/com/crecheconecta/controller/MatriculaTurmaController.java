package com.crecheconecta.controller;

import com.crecheconecta.dto.MatricularAlunoRequest;
import com.crecheconecta.dto.MatriculaTurmaResponse;
import com.crecheconecta.security.UsuarioAtual;
import com.crecheconecta.security.UsuarioAutenticado;
import com.crecheconecta.service.MatriculaTurmaService;
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
@RequestMapping("/api/turmas/{turmaId}/matriculas")
@PreAuthorize("hasRole('DIRECAO')")
public class MatriculaTurmaController {

    private final MatriculaTurmaService service;

    public MatriculaTurmaController(MatriculaTurmaService service) {

        this.service = service;
    }

    @PostMapping
    public ResponseEntity<MatriculaTurmaResponse> matricular(@PathVariable UUID turmaId, @Valid @RequestBody MatricularAlunoRequest request, @AuthenticationPrincipal UsuarioAutenticado autenticado) {

        var resposta = service.matricular(turmaId, request.alunoId(), UsuarioAtual.de(autenticado));

        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(resposta);
    }

    @GetMapping
    public ResponseEntity<Page<MatriculaTurmaResponse>> listar(@PathVariable UUID turmaId, @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int tamanho, @RequestParam(defaultValue = "false") boolean incluirEncerradas, @AuthenticationPrincipal UsuarioAutenticado autenticado) {

        var resposta = service.listar(turmaId, pagina, tamanho, incluirEncerradas, UsuarioAtual.de(autenticado));

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }

    @PostMapping("/{matriculaId}/encerrar")
    public ResponseEntity<MatriculaTurmaResponse> encerrar(@PathVariable UUID turmaId, @PathVariable UUID matriculaId, @AuthenticationPrincipal UsuarioAutenticado autenticado) {

        var resposta = service.encerrar(turmaId, matriculaId, UsuarioAtual.de(autenticado));

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }
}