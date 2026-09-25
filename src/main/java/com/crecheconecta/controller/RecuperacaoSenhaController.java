package com.crecheconecta.controller;

import com.crecheconecta.dto.ConfirmarRecuperacaoRequest;
import com.crecheconecta.dto.ConfirmarRecuperacaoResponse;
import com.crecheconecta.dto.SolicitarRecuperacaoRequest;
import com.crecheconecta.dto.SolicitarRecuperacaoResponse;
import com.crecheconecta.service.ConfirmacaoRecuperacaoService;
import com.crecheconecta.service.RecuperacaoSenhaService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.crecheconecta.dto.RedefinirSenhaRequest;
import com.crecheconecta.service.RedefinicaoSenhaService;

@RestController
@RequestMapping("/api/auth")
public class RecuperacaoSenhaController {

    private final RecuperacaoSenhaService recuperacao;
    private final ConfirmacaoRecuperacaoService confirmacao;
    private final RedefinicaoSenhaService redefinicao;

    public RecuperacaoSenhaController(
            RecuperacaoSenhaService recuperacao,
            ConfirmacaoRecuperacaoService confirmacao,
            RedefinicaoSenhaService redefinicao
    ) {
        this.recuperacao = recuperacao;
        this.confirmacao = confirmacao;
        this.redefinicao = redefinicao;
    }

    @PostMapping("/esqueci-senha")
    public ResponseEntity<SolicitarRecuperacaoResponse> solicitar(
            @Valid @RequestBody SolicitarRecuperacaoRequest request
    ) {
        return ResponseEntity.accepted()
                .cacheControl(CacheControl.noStore())
                .body(recuperacao.solicitar(request));
    }

    @PostMapping("/confirmar-recuperacao")
    public ResponseEntity<ConfirmarRecuperacaoResponse> confirmar(
            @Valid @RequestBody ConfirmarRecuperacaoRequest request
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(confirmacao.confirmar(request));
    }

    @PostMapping("/redefinir-senha")
    public ResponseEntity<Void> redefinir(
            @Valid @RequestBody RedefinirSenhaRequest request
    ) {
        redefinicao.redefinir(request);

        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .build();
    }
}