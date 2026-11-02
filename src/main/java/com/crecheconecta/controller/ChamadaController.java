package com.crecheconecta.controller;

import com.crecheconecta.dto.ChamadaAlunoResponse;
import com.crecheconecta.dto.PresencaResponse;
import com.crecheconecta.dto.RegistrarPresencaRequest;
import com.crecheconecta.security.UsuarioAtual;
import com.crecheconecta.security.UsuarioAutenticado;
import com.crecheconecta.service.ChamadaService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/turmas/{turmaId}/chamadas")
public class ChamadaController {

    private final ChamadaService chamadas;

    public ChamadaController(ChamadaService chamadas) {

        this.chamadas = chamadas;
    }

    @GetMapping("/{data}")
    public ResponseEntity<Page<ChamadaAlunoResponse>> consultar(@PathVariable UUID turmaId, @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data, @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int tamanho, @AuthenticationPrincipal UsuarioAutenticado autenticado) {

        var resposta = chamadas.consultar(turmaId, data, pagina, tamanho, UsuarioAtual.de(autenticado));

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }

    @PutMapping("/{data}/alunos/{alunoId}")
    public ResponseEntity<PresencaResponse> registrar(@PathVariable UUID turmaId, @PathVariable UUID alunoId, @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data, @Valid @RequestBody RegistrarPresencaRequest request, @AuthenticationPrincipal UsuarioAutenticado autenticado) {

        var resposta = chamadas.registrar(turmaId, alunoId, data, request, UsuarioAtual.de(autenticado));

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }
}