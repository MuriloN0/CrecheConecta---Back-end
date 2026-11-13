package com.crecheconecta.controller;

import com.crecheconecta.security.UsuarioAtual;
import com.crecheconecta.security.UsuarioAutenticado;
import com.crecheconecta.service.NotificacaoService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/notificacoes")
public class NotificacaoController {

    private final NotificacaoService notificacoes;

    public NotificacaoController(NotificacaoService notificacoes) {

        this.notificacoes = notificacoes;
    }

    @GetMapping
    public ResponseEntity<NotificacaoService.Pagina> listar(@AuthenticationPrincipal UsuarioAutenticado usuario, @RequestParam(defaultValue = "0") int pagina) {

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(notificacoes.listar(UsuarioAtual.de(usuario), pagina));
    }

    @GetMapping("/pendencias")
    public ResponseEntity<NotificacaoService.Pendencias> pendencias(@AuthenticationPrincipal UsuarioAutenticado usuario) {

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(notificacoes.pendencias(UsuarioAtual.de(usuario)));
    }

    @PutMapping("/{comunicado}/leitura")
    public ResponseEntity<Void> marcarLida(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable UUID comunicado) {

        notificacoes.marcarLida(UsuarioAtual.de(usuario), comunicado);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}