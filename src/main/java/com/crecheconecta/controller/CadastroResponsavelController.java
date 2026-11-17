package com.crecheconecta.controller;

import com.crecheconecta.dto.ConcluirCadastroResponsavelRequest;
import com.crecheconecta.dto.ConsultarConviteRequest;
import com.crecheconecta.dto.ConvidarProfessorRequest;
import com.crecheconecta.dto.ConviteCadastroResponse;
import com.crecheconecta.security.UsuarioAtual;
import com.crecheconecta.security.UsuarioAutenticado;
import com.crecheconecta.service.CadastroResponsavelService;
import com.crecheconecta.service.ConviteProfessorService;
import com.crecheconecta.service.FiltroLinguagem;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CadastroResponsavelController {

    private final CadastroResponsavelService service;
    private final ConviteProfessorService convites;

    public CadastroResponsavelController(CadastroResponsavelService service, ConviteProfessorService convites) {
        this.service = service;
        this.convites = convites;
    }

    @PostMapping("/api/alunos/convites")
    @PreAuthorize("hasRole('DIRECAO')")
    public ResponseEntity<ConviteCadastroResponse> convidar(
            @Valid @RequestBody ConvidarProfessorRequest request,
            @AuthenticationPrincipal UsuarioAutenticado autenticado
    ) {
        FiltroLinguagem.validarNomeResponsavel(request.nome());

        var resposta = convites.convidarResponsavel(request, UsuarioAtual.de(autenticado));
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(resposta);
    }

    @PostMapping("/api/cadastro/responsavel/consultar-convite")
    public ResponseEntity<CadastroResponsavelService.DadosConviteResponsavel> consultar(
            @Valid @RequestBody ConsultarConviteRequest request
    ) {
        var resposta = service.consultar(request.token());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }

    @PostMapping("/api/cadastro/responsavel/concluir")
    public ResponseEntity<CadastroResponsavelService.CadastroResponsavelResultado> concluir(
            @Valid @RequestBody ConcluirCadastroResponsavelRequest request
    ) {
        FiltroLinguagem.validarNomeResponsavel(request.nome());
        FiltroLinguagem.validarNomeAluno(request.nomeAluno());

        var resposta = service.concluir(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(resposta);
    }
}