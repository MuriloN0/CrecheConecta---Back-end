package com.crecheconecta.security;

import com.crecheconecta.configuration.LoginProxyConfig;
import com.crecheconecta.configuration.RateLimitConfig;
import com.crecheconecta.service.RateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.crecheconecta.service.RateLimitService.Politica.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LoginProxyIpTest {

    private static final String SEGREDO = "a".repeat(64);

    private RateLimitService limites;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        limites = mock(RateLimitService.class);

        mvc = MockMvcBuilders.standaloneSetup(new Endpoints())
                .addInterceptors(
                        new LoginProxyConfig(
                                new ClienteIpConfiavel(true, SEGREDO)
                        ),
                        new RateLimitConfig(limites)
                )
                .setControllerAdvice(new RateLimitAdvice(limites))
                .build();
    }

    @Test
    void exigeProxyEIpNasCincoRotasAntesDeConsumirLimites()
            throws Exception {
        for (String caminho : ClienteIpConfiavel.ROTAS) {
            mvc.perform(post(caminho)
                            .header(
                                    "X-CrecheConecta-Client-IP",
                                    "203.0.113.10"
                            )
                            .header(
                                    "X-Forwarded-For",
                                    "198.51.100.20"
                            ))
                    .andExpect(status().isForbidden());

            mvc.perform(post(caminho)
                            .header(
                                    "X-CrecheConecta-Login-Proxy",
                                    SEGREDO
                            ))
                    .andExpect(status().isForbidden());
        }

        verifyNoInteractions(limites);
    }

    @Test
    void usaOMesmoIpNasCincoRotas() throws Exception {
        for (String caminho : ClienteIpConfiavel.ROTAS) {
            mvc.perform(post(caminho)
                            .header("X-CrecheConecta-Login-Proxy", SEGREDO)
                            .header("X-CrecheConecta-Client-IP", "203.0.113.10"))
                    .andExpect(status().isOk());
        }

        verify(limites, times(5)).verificar(AUTENTICACAO_IP, "ipv4:203.0.113.10");

        verify(limites, times(5)).verificar(AUTENTICACAO_IP_HORA, "ipv4:203.0.113.10");

        verify(limites, times(5)).verificar(AUTENTICACAO_GLOBAL, "autenticacao");
    }

    @Test
    void retorna429ComTempoAntesDeProcessarCorpoInvalido()
            throws Exception {
        doThrow(new RateLimitException(42))
                .when(limites)
                .verificar(AUTENTICACAO_IP, "ipv4:203.0.113.10");

        mvc.perform(post("/api/auth/login")
                        .header(
                                "X-CrecheConecta-Login-Proxy",
                                SEGREDO
                        )
                        .header(
                                "X-CrecheConecta-Client-IP",
                                "203.0.113.10"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "42"))
                .andExpect(jsonPath("$.codigo").value(
                        "RATE_LIMIT_EXCEDIDO"
                ));

        verify(limites).verificar(AUTENTICACAO_IP, "ipv4:203.0.113.10");

        verify(limites).verificar(AUTENTICACAO_GLOBAL, "autenticacao");

        verifyNoMoreInteractions(limites);
    }

    @RestController
    static class Endpoints {

        @PostMapping({
                "/api/auth/login",
                "/api/auth/confirmar-login",
                "/api/auth/esqueci-senha",
                "/api/auth/confirmar-recuperacao",
                "/api/auth/redefinir-senha"
        })
        public void autenticar() {}
    }
}