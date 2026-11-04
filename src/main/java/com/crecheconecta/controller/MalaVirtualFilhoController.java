package com.crecheconecta.controller;

import com.crecheconecta.dto.MalaVirtualResponse;
import com.crecheconecta.security.UsuarioAtual;
import com.crecheconecta.security.UsuarioAutenticado;
import com.crecheconecta.service.MalaVirtualFilhoService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/meus-filhos/{alunoId}/mala")
@PreAuthorize("hasRole('RESPONSAVEL')")
public class MalaVirtualFilhoController {

    private final MalaVirtualFilhoService malaVirtual;

    public MalaVirtualFilhoController(MalaVirtualFilhoService malaVirtual) {
        this.malaVirtual = malaVirtual;
    }

    @GetMapping
    public ResponseEntity<List<MalaVirtualResponse>> listar(@PathVariable UUID alunoId, @AuthenticationPrincipal UsuarioAutenticado autenticado) {
        var resposta = malaVirtual.listar(alunoId, UsuarioAtual.de(autenticado));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }
}
