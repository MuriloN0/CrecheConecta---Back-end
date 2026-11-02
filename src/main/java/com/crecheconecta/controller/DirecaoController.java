package com.crecheconecta.controller;

import com.crecheconecta.dto.DirecaoResponse;
import com.crecheconecta.dto.InativarDirecaoRequest;
import com.crecheconecta.security.UsuarioAtual;
import com.crecheconecta.security.UsuarioAutenticado;
import com.crecheconecta.service.DirecaoService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/direcao")
@PreAuthorize("hasRole('DIRECAO')")
public class DirecaoController {

    private final DirecaoService direcao;

    public DirecaoController(DirecaoService direcao) {

        this.direcao = direcao;
    }

    @GetMapping("/me")
    public ResponseEntity<DirecaoResponse> consultarMinhaConta(@AuthenticationPrincipal UsuarioAutenticado autenticado) {

        var resposta = direcao.consultarMinhaConta(UsuarioAtual.de(autenticado));

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(resposta);
    }

    @PostMapping("/me/inativar")
    public ResponseEntity<DirecaoResponse> inativarMinhaConta(@Valid @RequestBody InativarDirecaoRequest request, @AuthenticationPrincipal UsuarioAutenticado autenticado) {

        var resposta = direcao.inativarMinhaConta(request.versao(), UsuarioAtual.de(autenticado));

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(resposta);
    }
}