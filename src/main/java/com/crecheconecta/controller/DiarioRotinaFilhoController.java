package com.crecheconecta.controller;

import com.crecheconecta.dto.DiarioRotinaResponse;
import com.crecheconecta.security.UsuarioAtual;
import com.crecheconecta.security.UsuarioAutenticado;
import com.crecheconecta.service.DiarioRotinaFilhoService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/meus-filhos/{alunoId}/diario")
@PreAuthorize("hasRole('RESPONSAVEL')")
public class DiarioRotinaFilhoController {

    private final DiarioRotinaFilhoService diario;

    public DiarioRotinaFilhoController(DiarioRotinaFilhoService diario) {
        this.diario = diario;
    }

    // GET /api/meus-filhos/{alunoId}/diario?data=2026-11-02  (sem "data" = diário mais recente)
    @GetMapping
    public ResponseEntity<DiarioRotinaResponse> consultar(@PathVariable UUID alunoId, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data, @AuthenticationPrincipal UsuarioAutenticado autenticado) {
        var resposta = diario.consultar(alunoId, data, UsuarioAtual.de(autenticado));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }
}
