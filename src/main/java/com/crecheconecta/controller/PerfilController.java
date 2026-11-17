package com.crecheconecta.controller;

import com.crecheconecta.security.UsuarioAtual;
import com.crecheconecta.security.UsuarioAutenticado;
import com.crecheconecta.service.PerfilService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/perfil")
@PreAuthorize("hasAnyRole('PROFESSOR', 'RESPONSAVEL')")
public class PerfilController {

    private final PerfilService perfil;

    public PerfilController(PerfilService perfil) {
        this.perfil = perfil;
    }

    @GetMapping
    public ResponseEntity<PerfilService.DadosPerfil> consultar(
            @AuthenticationPrincipal UsuarioAutenticado autenticado
    ) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(perfil.consultar(UsuarioAtual.de(autenticado)));
    }

    @PutMapping("/telefone")
    public ResponseEntity<PerfilService.DadosPerfil> atualizarTelefone(
            @AuthenticationPrincipal UsuarioAutenticado autenticado,
            @Valid @RequestBody TelefoneRequest dados
    ) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(perfil.atualizarTelefone(UsuarioAtual.de(autenticado), dados.telefone()));
    }

    @PostMapping("/email/solicitar")
    @PreAuthorize("hasRole('RESPONSAVEL')")
    public ResponseEntity<Void> solicitarEmail(
            @AuthenticationPrincipal UsuarioAutenticado autenticado,
            @Valid @RequestBody SolicitarEmailRequest dados
    ) {
        perfil.solicitarTrocaEmail(UsuarioAtual.de(autenticado), dados.email(), dados.senhaAtual());
        return semConteudo();
    }

    @PostMapping("/email/confirmar")
    @PreAuthorize("hasRole('RESPONSAVEL')")
    public ResponseEntity<Void> confirmarEmail(
            @AuthenticationPrincipal UsuarioAutenticado autenticado,
            @Valid @RequestBody ConfirmarEmailRequest dados
    ) {
        perfil.confirmarTrocaEmail(UsuarioAtual.de(autenticado), dados.codigo());
        return semConteudo();
    }

    @PostMapping("/inativar")
    @PreAuthorize("hasRole('RESPONSAVEL')")
    public ResponseEntity<Void> inativar(
            @AuthenticationPrincipal UsuarioAutenticado autenticado,
            @Valid @RequestBody InativarRequest dados
    ) {
        perfil.inativarConta(UsuarioAtual.de(autenticado), dados.senhaAtual());
        return semConteudo();
    }

    private ResponseEntity<Void> semConteudo() {
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    public record TelefoneRequest(
            @NotBlank @Pattern(regexp = "[1-9][0-9]{9,10}") String telefone
    ) {}

    public record SolicitarEmailRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 72) String senhaAtual
    ) {
        @Override
        public String toString() {
            return "SolicitarEmailRequest[dados omitidos]";
        }
    }

    public record ConfirmarEmailRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String codigo
    ) {
        @Override
        public String toString() {
            return "ConfirmarEmailRequest[dados omitidos]";
        }
    }

    public record InativarRequest(@NotBlank @Size(max = 72) String senhaAtual) {
        @Override
        public String toString() {
            return "InativarRequest[dados omitidos]";
        }
    }
}