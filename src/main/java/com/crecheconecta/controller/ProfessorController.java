package com.crecheconecta.controller;

import com.crecheconecta.dto.ConvidarProfessorRequest;
import com.crecheconecta.dto.ConviteCadastroResponse;
import com.crecheconecta.dto.DecidirCadastroProfessorRequest;
import com.crecheconecta.dto.ProfessorResponse;
import com.crecheconecta.security.UsuarioAtual;
import com.crecheconecta.security.UsuarioAutenticado;
import com.crecheconecta.service.AprovacaoProfessorService;
import com.crecheconecta.service.ConviteProfessorService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.crecheconecta.dto.AtualizarProfessorRequest;
import com.crecheconecta.entity.SituacaoCadastro;
import com.crecheconecta.service.ProfessorService;

import java.util.UUID;

@RestController
@RequestMapping("/api/professores")
@PreAuthorize("hasRole('DIRECAO')")
public class ProfessorController {

    private final ConviteProfessorService convites;
    private final AprovacaoProfessorService aprovacoes;
    private final ProfessorService professores;

    public ProfessorController(ConviteProfessorService convites, AprovacaoProfessorService aprovacoes,  ProfessorService professores) {
        this.professores = professores;
        this.convites = convites;
        this.aprovacoes = aprovacoes;
    }

    @PostMapping("/convites")
    public ResponseEntity<ConviteCadastroResponse> convidar(@Valid @RequestBody ConvidarProfessorRequest request, @AuthenticationPrincipal UsuarioAutenticado autenticado) {

        var resposta = convites.convidar(request, UsuarioAtual.de(autenticado));

        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(resposta);
    }

    @GetMapping("/pendentes")
    public ResponseEntity<Page<ProfessorResponse>> listarPendentes(@RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int tamanho, @AuthenticationPrincipal UsuarioAutenticado autenticado) {

        var resposta = aprovacoes.listarPendentes(pagina, tamanho, UsuarioAtual.de(autenticado));

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }

    @PostMapping("/{professorId}/aprovar")
    public ResponseEntity<ProfessorResponse> aprovar(@PathVariable UUID professorId, @Valid @RequestBody DecidirCadastroProfessorRequest request, @AuthenticationPrincipal UsuarioAutenticado autenticado) {

        var resposta = aprovacoes.aprovar(professorId, request.versao(), UsuarioAtual.de(autenticado));

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }

    @PostMapping("/{professorId}/rejeitar")
    public ResponseEntity<ProfessorResponse> rejeitar(@PathVariable UUID professorId, @Valid @RequestBody DecidirCadastroProfessorRequest request, @AuthenticationPrincipal UsuarioAutenticado autenticado) {

        var resposta = aprovacoes.rejeitar(professorId, request.versao(), UsuarioAtual.de(autenticado));

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }

    @GetMapping
    public ResponseEntity<Page<ProfessorResponse>> listar(@RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int tamanho, @RequestParam(required = false) SituacaoCadastro situacao, @AuthenticationPrincipal UsuarioAutenticado autenticado) {

        var resposta = professores.listar(pagina, tamanho, situacao, UsuarioAtual.de(autenticado));

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }

    @GetMapping("/{professorId}")
    public ResponseEntity<ProfessorResponse> visualizar(@PathVariable UUID professorId, @AuthenticationPrincipal UsuarioAutenticado autenticado) {

        var resposta = professores.visualizar(professorId, UsuarioAtual.de(autenticado));

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }

    @PutMapping("/{professorId}")
    public ResponseEntity<ProfessorResponse> atualizar(@PathVariable UUID professorId, @Valid @RequestBody AtualizarProfessorRequest request, @AuthenticationPrincipal UsuarioAutenticado autenticado) {

        var resposta = professores.atualizar(professorId, request, UsuarioAtual.de(autenticado));

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }

    @PostMapping("/{professorId}/inativar")
    public ResponseEntity<ProfessorResponse> inativar(@PathVariable UUID professorId, @Valid @RequestBody DecidirCadastroProfessorRequest request, @AuthenticationPrincipal UsuarioAutenticado autenticado) {

        var resposta = professores.inativar(professorId, request.versao(), UsuarioAtual.de(autenticado));

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }
}