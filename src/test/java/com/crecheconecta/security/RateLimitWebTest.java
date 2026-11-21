package com.crecheconecta.security;

import com.crecheconecta.configuration.LoginProxyConfig;
import com.crecheconecta.configuration.RateLimitConfig;
import com.crecheconecta.dto.LoginRequest;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.exception.ApiExceptionHandler;
import com.crecheconecta.service.AuditoriaService;
import com.crecheconecta.service.RateLimitService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static com.crecheconecta.service.RateLimitService.Politica.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RateLimitWebTest {

    private RateLimitService limites;
    private Endpoints endpoints;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        limites = mock(RateLimitService.class);
        endpoints = new Endpoints();

        mvc = MockMvcBuilders.standaloneSetup(endpoints)
                .addInterceptors(
                        new LoginProxyConfig(
                                new ClienteIpConfiavel(false, "")
                        ),
                        new RateLimitConfig(limites)
                )
                .setControllerAdvice(
                        new RateLimitAdvice(limites),
                        new ApiExceptionHandler(
                                mock(AuditoriaService.class)
                        )
                )
                .build();
    }

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejeitaAntesDoControllerENormalizaEmail() throws Exception {
        doThrow(new RateLimitException(37))
                .when(limites)
                .verificar(LOGIN_EMAIL, "pessoa@example.invalid");

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"  PESSOA@example.invalid  ","senha":"incorreta"}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "37"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.codigo").value("RATE_LIMIT_EXCEDIDO"))
                .andExpect(jsonPath("$.segundosRestantes").value(37));

        assertEquals(0, endpoints.chamadas.get());

        verify(limites).verificar(
                AUTENTICACAO_GLOBAL,
                "autenticacao"
        );
    }

    @Test
    void tetoGlobalRejeitaInclusiveAntesDeLerJsonInvalido() throws Exception {
        doThrow(new RateLimitException(12))
                .when(limites)
                .verificar(AUTENTICACAO_GLOBAL, "autenticacao");

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "12"));

        assertEquals(0, endpoints.chamadas.get());

        verify(limites).verificar(
                AUTENTICACAO_GLOBAL,
                "autenticacao"
        );

        verifyNoMoreInteractions(limites);
    }

    @Test
    void perfilCompartilhaLimiteDeSenhaEntreOperacoes() throws Exception {
        UUID id = UUID.randomUUID();

        var usuario = new UsuarioAutenticado(
                id,
                UUID.randomUUID(),
                "Pessoa",
                "pessoa@example.invalid",
                Perfil.RESPONSAVEL
        );

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        usuario,
                        null,
                        List.of()
                )
        );

        mvc.perform(post("/api/perfil/email/solicitar"))
                .andExpect(status().isOk());

        doThrow(new RateLimitException(250))
                .when(limites)
                .verificar(SENHA_PERFIL, id.toString());

        mvc.perform(post("/api/perfil/inativar"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.segundosRestantes").value(250));

        assertEquals(1, endpoints.chamadas.get());

        verify(limites, times(2)).verificar(
                SENHA_PERFIL,
                id.toString()
        );
    }

    @RestController
    static class Endpoints {

        final AtomicInteger chamadas = new AtomicInteger();

        @PostMapping("/api/auth/login")
        public void login(@RequestBody LoginRequest dados) {
            chamadas.incrementAndGet();
        }

        @PostMapping({
                "/api/perfil/email/solicitar",
                "/api/perfil/inativar"
        })
        public void perfil() {
            chamadas.incrementAndGet();
        }
    }
}