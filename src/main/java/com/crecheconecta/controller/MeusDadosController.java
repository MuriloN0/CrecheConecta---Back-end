package com.crecheconecta.controller;

import com.crecheconecta.dto.MeusDadosResponse;
import com.crecheconecta.security.UsuarioAtual;
import com.crecheconecta.security.UsuarioAutenticado;
import com.crecheconecta.service.MeusDadosService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** LGPD: o responsável baixa uma cópia dos dados dele e das crianças vinculadas. */
@RestController
@RequestMapping("/api/meus-dados")
@PreAuthorize("hasRole('RESPONSAVEL')")
public class MeusDadosController {

    private final MeusDadosService meusDados;

    public MeusDadosController(MeusDadosService meusDados) {
        this.meusDados = meusDados;
    }

    @GetMapping
    public ResponseEntity<MeusDadosResponse> exportar(@AuthenticationPrincipal UsuarioAutenticado autenticado) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(meusDados.exportar(UsuarioAtual.de(autenticado)));
    }
}
