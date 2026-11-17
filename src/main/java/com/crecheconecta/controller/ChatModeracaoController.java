package com.crecheconecta.controller;

import com.crecheconecta.security.UsuarioAtual;
import com.crecheconecta.security.UsuarioAutenticado;
import com.crecheconecta.service.ChatModeracaoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/chat/moderacao")
@PreAuthorize("hasRole('DIRECAO')")
public class ChatModeracaoController {

    private final ChatModeracaoService moderacao;

    public ChatModeracaoController(ChatModeracaoService moderacao) {
        this.moderacao = moderacao;
    }

    @GetMapping("/{responsavelId}")
    public ResponseEntity<ChatModeracaoService.Situacao> consultar(@PathVariable UUID responsavelId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(moderacao.consultar(responsavelId));
    }

    @PutMapping("/{responsavelId}")
    public ResponseEntity<ChatModeracaoService.Situacao> bloquear(
            @PathVariable UUID responsavelId,
            @AuthenticationPrincipal UsuarioAutenticado autenticado,
            @Valid @RequestBody BloquearChatRequest request
    ) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
                moderacao.bloquear(
                        UsuarioAtual.de(autenticado), responsavelId, request.permanente(), request.minutos()
                )
        );
    }

    @DeleteMapping("/{responsavelId}")
    public ResponseEntity<ChatModeracaoService.Situacao> desbloquear(
            @PathVariable UUID responsavelId,
            @AuthenticationPrincipal UsuarioAutenticado autenticado
    ) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
                moderacao.desbloquear(UsuarioAtual.de(autenticado), responsavelId)
        );
    }

    public record BloquearChatRequest(
            @NotNull Boolean permanente,
            @Min(1) @Max(43200) Integer minutos
    ) {}
}