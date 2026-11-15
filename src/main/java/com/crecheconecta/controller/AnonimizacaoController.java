package com.crecheconecta.controller;

import com.crecheconecta.dto.AnonimizacaoResponse;
import com.crecheconecta.security.UsuarioAtual;
import com.crecheconecta.security.UsuarioAutenticado;
import com.crecheconecta.service.AnonimizacaoService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** LGPD: a direção anonimiza os dados de um aluno que saiu da creche (e do responsável dele). */
@RestController
@RequestMapping("/api/alunos/{alunoId}/anonimizar")
@PreAuthorize("hasRole('DIRECAO')")
public class AnonimizacaoController {

    private final AnonimizacaoService anonimizacao;

    public AnonimizacaoController(AnonimizacaoService anonimizacao) {
        this.anonimizacao = anonimizacao;
    }

    @PostMapping
    public ResponseEntity<AnonimizacaoResponse> anonimizar(@PathVariable UUID alunoId, @AuthenticationPrincipal UsuarioAutenticado autenticado) {
        var resposta = anonimizacao.anonimizarAluno(alunoId, UsuarioAtual.de(autenticado));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }
}
