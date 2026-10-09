package com.crecheconecta.controller;

import com.crecheconecta.configuration.SecurityConfig;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.exception.ApiExceptionHandler;
import com.crecheconecta.security.UsuarioAutenticado;
import com.crecheconecta.service.AuditoriaService;
import com.crecheconecta.service.FichaSaudeService;
import com.crecheconecta.service.SessaoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testes de INTEGRACAO web do FichaSaudeController.
 *
 * Sobe a camada web (controller + Spring Security + tratamento de erros)
 * com @WebMvcTest. O service e mockado: aqui testamos o contrato HTTP
 * (status, corpo, seguranca por role), nao a regra de negocio.
 *
 * A autenticacao real e por token Bearer; nos testes injetamos
 * diretamente um UsuarioAutenticado como principal, com a authority
 * ROLE_DIRECAO ou ROLE_PROFESSOR, para exercitar o controle de acesso.
 */
@WebMvcTest(FichaSaudeController.class)
@Import({SecurityConfig.class, ApiExceptionHandler.class})
@ActiveProfiles("test")
class FichaSaudeControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FichaSaudeService service;

    // Dependencias exigidas pelos beans importados (SecurityConfig e handler).
    @MockitoBean
    private SessaoService sessaoService;

    @MockitoBean
    private AuditoriaService auditoriaService;

    private final UUID alunoId = UUID.randomUUID();

    private UsuarioAutenticado usuario(Perfil perfil) {
        return new UsuarioAutenticado(
                UUID.randomUUID(), UUID.randomUUID(), "Fulano", "fulano@teste.com", perfil);
    }

    private RequestPostProcessor comoDirecao() {
        return authentication(new UsernamePasswordAuthenticationToken(
                usuario(Perfil.DIRECAO), null,
                List.of(new SimpleGrantedAuthority("ROLE_DIRECAO"))));
    }

    private RequestPostProcessor comoProfessor() {
        return authentication(new UsernamePasswordAuthenticationToken(
                usuario(Perfil.PROFESSOR), null,
                List.of(new SimpleGrantedAuthority("ROLE_PROFESSOR"))));
    }

    // ---------- CAMINHO FELIZ: 201 ----------

    @Test
    void deveRetornar201ComLocationAoCadastrarFicha() throws Exception {
        // Arrange
        UUID novoId = UUID.randomUUID();
        when(service.cadastrar(eq(alunoId), any(), any())).thenReturn(novoId);

        // Act + Assert
        mockMvc.perform(post("/api/alunos/{alunoId}/fichas-saude", alunoId)
                        .with(comoDirecao())
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Maria\",\"observacoes\":\"Alergia a amendoim\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        "/api/alunos/" + alunoId + "/fichas-saude/" + novoId));

        verify(service).cadastrar(eq(alunoId), any(), any());
    }

    // ---------- ERRO 4xx: 400 (corpo invalido) ----------

    @Test
    void deveRetornar400QuandoNomeForVazio() throws Exception {
        // Act + Assert: nome em branco viola @NotBlank -> 400 antes de chamar o service
        mockMvc.perform(post("/api/alunos/{alunoId}/fichas-saude", alunoId)
                        .with(comoDirecao())
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"\",\"observacoes\":\"x\"}"))
                .andExpect(status().isBadRequest());

        verify(service, never()).cadastrar(any(), any(), any());
    }

    // ---------- ERRO 4xx: 403 (sem permissao) ----------

    @Test
    void deveRetornar403QuandoProfessorTentaCadastrar() throws Exception {
        // Act + Assert: so DIRECAO pode; PROFESSOR recebe 403
        mockMvc.perform(post("/api/alunos/{alunoId}/fichas-saude", alunoId)
                        .with(comoProfessor())
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Maria\",\"observacoes\":\"x\"}"))
                .andExpect(status().isForbidden());

        verify(service, never()).cadastrar(any(), any(), any());
    }

    // ---------- CAMINHO FELIZ: 200 (listar) ----------

    @Test
    void deveListarFichasComDirecaoAutenticada() throws Exception {
        // Arrange
        when(service.listar(eq(alunoId), any())).thenReturn(List.of());

        // Act + Assert
        mockMvc.perform(get("/api/alunos/{alunoId}/fichas-saude", alunoId)
                        .with(comoDirecao()))
                .andExpect(status().isOk());

        verify(service).listar(eq(alunoId), any());
    }
}
