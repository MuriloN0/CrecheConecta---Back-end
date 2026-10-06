package com.crecheconecta.controller;


import com.crecheconecta.dto.UsuarioLogadoResponse;
import com.crecheconecta.security.UsuarioAutenticado;
import com.crecheconecta.service.SessaoService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class SessaoController {

    private final SessaoService sessaoService;

    public SessaoController(SessaoService sessaoService) {
        this.sessaoService = sessaoService;
    }

    @GetMapping("/me")
    public ResponseEntity<UsuarioLogadoResponse> me(
            @AuthenticationPrincipal UsuarioAutenticado usuario
    ) {
        var resposta = new UsuarioLogadoResponse(
                usuario.usuarioId(),
                usuario.nome(),
                usuario.email(),
                usuario.perfil()
        );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(resposta);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal UsuarioAutenticado usuario
    ) {
        sessaoService.logout(usuario);

        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .build();
    }

}
