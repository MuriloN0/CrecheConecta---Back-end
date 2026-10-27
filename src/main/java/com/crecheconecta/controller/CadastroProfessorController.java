package com.crecheconecta.controller;

import com.crecheconecta.dto.ConcluirCadastroProfessorRequest;
import com.crecheconecta.dto.ConsultarConviteRequest;
import com.crecheconecta.service.CadastroProfessorService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cadastro/professores")
public class CadastroProfessorController {

    private final CadastroProfessorService service;

    public CadastroProfessorController(CadastroProfessorService service) {
        this.service = service;
    }

    @PostMapping("/consultar-convite")
    public ResponseEntity<CadastroProfessorService.DadosConviteProfessor> consultar(@Valid @RequestBody ConsultarConviteRequest request
    ) {
        var resposta = service.consultar(request.token());

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }

    @PostMapping("/concluir")
    public ResponseEntity<CadastroProfessorService.CadastroProfessorResultado> concluir(@Valid @RequestBody ConcluirCadastroProfessorRequest request) {
        var resposta = service.concluir(request);

        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(resposta);
    }
}