package com.crecheconecta.controller;

import com.crecheconecta.dto.DiarioAlunoResponse;
import com.crecheconecta.dto.DiarioRegistroResponse;
import com.crecheconecta.dto.RegistrarDiarioRequest;
import com.crecheconecta.security.UsuarioAtual;
import com.crecheconecta.security.UsuarioAutenticado;
import com.crecheconecta.service.DiarioRotinaService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Diário de Rotina Digital, lado do professor.
 */
@RestController
@RequestMapping("/api/turmas/{turmaId}/diarios")
public class DiarioRotinaController {

    private final DiarioRotinaService diarios;

    public DiarioRotinaController(DiarioRotinaService diarios) {
        this.diarios = diarios;
    }

    // GET /api/turmas/{turmaId}/diarios/2026-11-07  -> alunos da turma com o diário do dia
    @GetMapping("/{data}")
    public ResponseEntity<List<DiarioAlunoResponse>> consultar(
            @PathVariable UUID turmaId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            @AuthenticationPrincipal UsuarioAutenticado autenticado) {

        var resposta = diarios.consultar(turmaId, data, UsuarioAtual.de(autenticado));

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }

    // PUT /api/turmas/{turmaId}/diarios/2026-11-07/alunos/{alunoId}  -> cria ou edita
    @PutMapping("/{data}/alunos/{alunoId}")
    public ResponseEntity<DiarioRegistroResponse> registrar(
            @PathVariable UUID turmaId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            @PathVariable UUID alunoId,
            @Valid @RequestBody RegistrarDiarioRequest request,
            @AuthenticationPrincipal UsuarioAutenticado autenticado) {

        var resposta = diarios.registrar(turmaId, alunoId, data, request, UsuarioAtual.de(autenticado));

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }

    @DeleteMapping("/{data}/alunos/{alunoId}")
    public ResponseEntity<Void> excluir(
            @PathVariable UUID turmaId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            @PathVariable UUID alunoId,
            @AuthenticationPrincipal UsuarioAutenticado autenticado) {

        diarios.excluir(turmaId, alunoId, data, UsuarioAtual.de(autenticado));

        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
