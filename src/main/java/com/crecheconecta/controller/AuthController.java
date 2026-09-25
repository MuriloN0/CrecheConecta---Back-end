package com.crecheconecta.controller;


import com.crecheconecta.dto.ConfirmarLoginRequest;
import com.crecheconecta.dto.LoginRequest;
import com.crecheconecta.dto.LoginResponse;
import com.crecheconecta.dto.SessaoResponse;
import com.crecheconecta.service.AuthService;
import com.crecheconecta.service.ConfirmacaoLoginService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final ConfirmacaoLoginService confirmacaoLoginService;

    public AuthController(
            AuthService authService,
            ConfirmacaoLoginService confirmacaoLoginService
    ) {
        this.authService = authService;
        this.confirmacaoLoginService = confirmacaoLoginService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(authService.iniciarLogin(request));
    }

    @PostMapping("/confirmar-login")
    public ResponseEntity<SessaoResponse> confirmarLogin(
            @Valid @RequestBody ConfirmarLoginRequest request
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(confirmacaoLoginService.confirmar(request));
    }

}
