package com.crecheconecta.controller;

import com.crecheconecta.dto.ChamadaMesResponse;
import com.crecheconecta.security.UsuarioAtual;
import com.crecheconecta.security.UsuarioAutenticado;
import com.crecheconecta.service.ChamadaFilhoService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.UUID;

@RestController
@RequestMapping("/api/meus-filhos/{alunoId}/chamada")
@PreAuthorize("hasRole('RESPONSAVEL')")
public class ChamadaFilhoController {

    private final ChamadaFilhoService chamada;

    public ChamadaFilhoController(ChamadaFilhoService chamada) {
        this.chamada = chamada;
    }

    // GET /api/meus-filhos/{alunoId}/chamada?mes=2026-11  (sem "mes" = mês atual)
    @GetMapping
    public ResponseEntity<ChamadaMesResponse> consultar(@PathVariable UUID alunoId, @RequestParam(required = false) YearMonth mes, @AuthenticationPrincipal UsuarioAutenticado autenticado) {
        var resposta = chamada.consultarMes(alunoId, mes, UsuarioAtual.de(autenticado));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }
}
