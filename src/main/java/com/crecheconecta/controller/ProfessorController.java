package com.crecheconecta.controller;

import com.crecheconecta.dto.ConvidarProfessorRequest;
import com.crecheconecta.dto.ConviteCadastroResponse;
import com.crecheconecta.security.UsuarioAtual;
import com.crecheconecta.security.UsuarioAutenticado;
import com.crecheconecta.service.ConviteProfessorService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/professores")
public class ProfessorController {

    private final ConviteProfessorService convites;

    public ProfessorController(ConviteProfessorService convites) {

        this.convites = convites;
    }

    @PostMapping("/convites")
    @PreAuthorize("hasRole('DIRECAO')")
    public ResponseEntity<ConviteCadastroResponse> convidar(@Valid @RequestBody ConvidarProfessorRequest request, @AuthenticationPrincipal UsuarioAutenticado autenticado) {
        var resposta = convites.convidar(request, UsuarioAtual.de(autenticado));

        return ResponseEntity.status(HttpStatus.CREATED).body(resposta);
    }
}