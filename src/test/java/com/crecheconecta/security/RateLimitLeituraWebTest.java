package com.crecheconecta.security;

import com.crecheconecta.configuration.RateLimitConfig;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.service.RateLimitService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static com.crecheconecta.service.RateLimitService.Politica.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RateLimitLeituraWebTest {

    private RateLimitService limites;
    private MockMvc mvc;
    private UUID usuario;
    private Endpoints endpoints;

    @BeforeEach
    void preparar() {
        limites = mock(RateLimitService.class);
        endpoints = new Endpoints();
        usuario = UUID.randomUUID();

        autenticar(usuario);

        mvc = MockMvcBuilders.standaloneSetup(endpoints)
                .addInterceptors(new RateLimitConfig(limites))
                .setControllerAdvice(new RateLimitAdvice(limites))
                .build();
    }

    private void autenticar(UUID id) {
        var pessoa = new UsuarioAutenticado(
                id,
                UUID.randomUUID(),
                "Pessoa",
                "teste@example.invalid",
                Perfil.DIRECAO
        );

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        pessoa,
                        null,
                        List.of()
                )
        );
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @CsvSource({
            "/api/comunicados, LEITURA_MURAL",
            "/api/turmas/frequencia, LEITURA_FREQUENCIA",
            "/api/alunos, LEITURA_RECURSO",
            "/api/turmas, LEITURA_RECURSO",
            "/api/atividades/turmas, LEITURA_RECURSO",
            "/api/notificacoes, LEITURA_RECURSO",
            "/api/notificacoes/pendencias, SEM_LIMITE_ADICIONAL",
            "/api/termo/status, SEM_LIMITE_ADICIONAL",
            "/api/auth/me, SEM_LIMITE_ADICIONAL",
            "/api/chat/contatos, SEM_LIMITE_ADICIONAL"
    })
    void aplicaLimiteEspecificoSemRemoverLimiteGeral(
            String caminho,
            String politica
    ) throws Exception {
        mvc.perform(get(caminho))
                .andExpect(status().isOk());

        verify(limites).verificar(
                API_USUARIO,
                usuario.toString()
        );

        if ("SEM_LIMITE_ADICIONAL".equals(politica)) {
            verifyNoMoreInteractions(limites);
        } else {
            verify(limites).verificar(
                    RateLimitService.Politica.valueOf(politica),
                    usuario + "\n" + caminho
            );
        }
    }

    @Test
    void filtrosEPaginacaoNaoCriamOutroContador() throws Exception {
        mvc.perform(get("/api/comunicados")
                        .param("pagina", "0")
                        .param("busca", "a"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/comunicados")
                        .param("pagina", "1")
                        .param("busca", "b"))
                .andExpect(status().isOk());

        verify(limites, times(2)).verificar(
                LEITURA_MURAL,
                usuario + "\n/api/comunicados"
        );
    }

    @Test
    void mudarIdOuDataNaoContornaLimiteDoRecurso()
            throws Exception {
        mvc.perform(get(
                        "/api/turmas/"
                                + UUID.randomUUID()
                                + "/chamadas/2026-11-18"
                ))
                .andExpect(status().isOk());

        mvc.perform(get(
                        "/api/turmas/"
                                + UUID.randomUUID()
                                + "/chamadas/2026-11-17"
                ))
                .andExpect(status().isOk());

        verify(limites, times(2)).verificar(
                LEITURA_RECURSO,
                usuario + "\n/api/turmas/{turmaId}/chamadas/{data}"
        );
    }

    @Test
    void chatAutomaticoMantemSomenteLimiteGeral()
            throws Exception {
        mvc.perform(get(
                        "/api/chat/contatos/"
                                + UUID.randomUUID()
                                + "/mensagens"
                ))
                .andExpect(status().isOk());

        verify(limites).verificar(
                API_USUARIO,
                usuario.toString()
        );

        verifyNoMoreInteractions(limites);
    }

    @Test
    void usuariosDiferentesNaoCompartilhamContador()
            throws Exception {
        mvc.perform(get("/api/comunicados"))
                .andExpect(status().isOk());

        UUID outro = UUID.randomUUID();
        autenticar(outro);

        mvc.perform(get("/api/comunicados"))
                .andExpect(status().isOk());

        verify(limites).verificar(
                LEITURA_MURAL,
                usuario + "\n/api/comunicados"
        );

        verify(limites).verificar(
                LEITURA_MURAL,
                outro + "\n/api/comunicados"
        );
    }

    @Test
    void bloqueiaAntesDoControllerCom429ETempoRestante()
            throws Exception {
        doThrow(new RateLimitException(23))
                .when(limites)
                .verificar(
                        LEITURA_MURAL,
                        usuario + "\n/api/comunicados"
                );

        mvc.perform(get("/api/comunicados"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "23"))
                .andExpect(jsonPath("$.codigo").value(
                        "RATE_LIMIT_EXCEDIDO"
                ))
                .andExpect(jsonPath("$.segundosRestantes").value(23));

        assertEquals(0, endpoints.chamadas.get());
    }

    @Test
    void escritaMantemSuaPoliticaSemConsumirQuotaDeLeitura()
            throws Exception {
        mvc.perform(post("/api/alunos"))
                .andExpect(status().isOk());

        verify(limites).verificar(
                API_USUARIO,
                usuario.toString()
        );

        verify(limites).verificar(
                ESCRITA_USUARIO,
                usuario.toString()
        );

        verifyNoMoreInteractions(limites);
    }

    @RestController
    static class Endpoints {

        final AtomicInteger chamadas = new AtomicInteger();

        @GetMapping({
                "/api/comunicados",
                "/api/turmas/frequencia",
                "/api/alunos",
                "/api/turmas",
                "/api/atividades/turmas",
                "/api/notificacoes",
                "/api/notificacoes/pendencias",
                "/api/termo/status",
                "/api/auth/me",
                "/api/chat/contatos",
                "/api/chat/contatos/{contato}/mensagens",
                "/api/turmas/{turmaId}/chamadas/{data}"
        })
        public void consultar() {
            chamadas.incrementAndGet();
        }

        @PostMapping("/api/alunos")
        public void escrever() {
            chamadas.incrementAndGet();
        }
    }
}