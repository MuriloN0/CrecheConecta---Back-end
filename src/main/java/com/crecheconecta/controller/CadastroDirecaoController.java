package com.crecheconecta.controller;

import com.crecheconecta.dto.ConcluirCadastroDirecaoRequest;
import com.crecheconecta.dto.ConsultarConviteRequest;
import com.crecheconecta.service.CadastroDirecaoService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cadastro/direcao")
public class CadastroDirecaoController {

    private final CadastroDirecaoService cadastros;

    public CadastroDirecaoController(CadastroDirecaoService cadastros) {

        this.cadastros = cadastros;
    }

    @PostMapping("/consultar-convite")
    public ResponseEntity<CadastroDirecaoService.DadosConviteDirecao> consultar(@Valid @RequestBody ConsultarConviteRequest request) {

        var resposta = cadastros.consultar(request.token());

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }

    @PostMapping("/concluir")
    public ResponseEntity<CadastroDirecaoService.CadastroDirecaoResultado> concluir(@Valid @RequestBody ConcluirCadastroDirecaoRequest request) {

        var resposta = cadastros.concluir(request);

        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(resposta);
    }
}