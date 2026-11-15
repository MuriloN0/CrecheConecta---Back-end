package com.crecheconecta.controller;

import com.crecheconecta.dto.MalaVirtualTurmaResponse;
import com.crecheconecta.security.UsuarioAtual;
import com.crecheconecta.security.UsuarioAutenticado;
import com.crecheconecta.service.MalaVirtualPaginacaoService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class MalaVirtualPaginacaoController {

    private final MalaVirtualPaginacaoService malas;

    public MalaVirtualPaginacaoController(MalaVirtualPaginacaoService malas) {

        this.malas = malas;
    }

    @GetMapping("/api/turmas/{turmaId}/malas/paginadas")
    public ResponseEntity<PaginaMalas> listar(@PathVariable UUID turmaId, @RequestParam(defaultValue = "0") int pagina, @AuthenticationPrincipal UsuarioAutenticado autenticado) {

        var resultado = malas.listar(turmaId, pagina, UsuarioAtual.de(autenticado));

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new PaginaMalas(resultado.getContent(), resultado.getTotalPages()));
    }

    public record PaginaMalas(List<MalaVirtualTurmaResponse> content, int totalPages) {}
}