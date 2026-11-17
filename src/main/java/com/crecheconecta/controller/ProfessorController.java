package com.crecheconecta.controller;

import com.crecheconecta.dto.ConvidarProfessorRequest;
import com.crecheconecta.dto.ConviteCadastroResponse;
import com.crecheconecta.dto.DecidirCadastroProfessorRequest;
import com.crecheconecta.dto.ProfessorResponse;
import com.crecheconecta.entity.SituacaoCadastro;
import com.crecheconecta.security.UsuarioAtual;
import com.crecheconecta.security.UsuarioAutenticado;
import com.crecheconecta.service.ConviteProfessorService;
import com.crecheconecta.service.ProfessorService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/professores")
@PreAuthorize("hasRole('DIRECAO')")
public class ProfessorController {

    private final ConviteProfessorService convites;
    private final ProfessorService professores;

    public ProfessorController(ConviteProfessorService convites, ProfessorService professores) {
        this.convites = convites;
        this.professores = professores;
    }

    @PostMapping("/convites")
    public ResponseEntity<ConviteCadastroResponse> convidar(
            @Valid @RequestBody ConvidarProfessorRequest request,
            @AuthenticationPrincipal UsuarioAutenticado autenticado
    ) {
        var resposta = convites.convidar(request, UsuarioAtual.de(autenticado));
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(resposta);
    }

    @GetMapping
    public ResponseEntity<Page<ProfessorResponse>> listar(
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanho,
            @RequestParam(required = false) SituacaoCadastro situacao,
            @RequestParam(required = false) String busca,
            @AuthenticationPrincipal UsuarioAutenticado autenticado
    ) {
        var resposta = professores.listar(pagina, tamanho, situacao, busca, UsuarioAtual.de(autenticado));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }

    @GetMapping("/disponiveis")
    public ResponseEntity<Page<ProfessorResponse>> listarDisponiveis(
            @RequestParam UUID turmaId,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "5") int tamanho,
            @RequestParam(defaultValue = "") String nome,
            @AuthenticationPrincipal UsuarioAutenticado autenticado
    ) {
        var resposta = professores.listarDisponiveis(turmaId, pagina, tamanho, nome, UsuarioAtual.de(autenticado));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }

    @GetMapping("/{professorId}")
    public ResponseEntity<ProfessorResponse> visualizar(
            @PathVariable UUID professorId,
            @AuthenticationPrincipal UsuarioAutenticado autenticado
    ) {
        var resposta = professores.visualizar(professorId, UsuarioAtual.de(autenticado));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }

    @PostMapping("/{professorId}/inativar")
    public ResponseEntity<ProfessorResponse> inativar(
            @PathVariable UUID professorId,
            @Valid @RequestBody DecidirCadastroProfessorRequest request,
            @AuthenticationPrincipal UsuarioAutenticado autenticado
    ) {
        var resposta = professores.inativar(professorId, request.versao(), UsuarioAtual.de(autenticado));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }
}