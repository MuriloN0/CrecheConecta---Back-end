package com.crecheconecta.controller;

import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.Turma;
import com.crecheconecta.entity.TurmaProfessor;
import com.crecheconecta.entity.Usuario;
import com.crecheconecta.repository.TurmaProfessorRepository;
import com.crecheconecta.repository.TurmaRepository;
import com.crecheconecta.repository.UsuarioRepository;
import com.crecheconecta.security.UsuarioAutenticado;
import com.crecheconecta.service.SessaoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Perfil "test": H2 em memoria, sem depender de PostgreSQL local nem de senha no codigo
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Tag("integration")
class AtividadeAPIIntegracaoTest {

    private static final String TOKEN_TESTE =
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private TurmaRepository turmaRepository;

    @Autowired
    private TurmaProfessorRepository turmaProfessorRepository;

    @MockitoBean
    private SessaoService sessaoService;

    private UUID professorId;

    private UUID turmaId;

    @BeforeEach
    void autenticacaoDeTeste() {
        // O AtividadeAcessoService confere o usuario no banco e exige que o professor leciona na turma,
        // entao o professor e a turma precisam existir de verdade (gravados na transacao do teste).
        Usuario professor = new Usuario();
        professor.setNome("Professor Teste");
        professor.setEmail("professor." + System.nanoTime() + "@teste.local");
        professor.setSenhaHash("hash-nao-usado-no-teste");
        professor.setPerfil(Perfil.PROFESSOR);
        professorId = usuarioRepository.saveAndFlush(professor).getId();

        Turma turma = new Turma();
        turma.setNome("Turma API " + System.nanoTime());
        turma.setNumeroSala("S1");
        turmaId = turmaRepository.saveAndFlush(turma).getId();

        TurmaProfessor vinculo = new TurmaProfessor();
        vinculo.setTurmaId(turmaId);
        vinculo.setProfessorId(professorId);
        vinculo.setCriadoEm(Instant.now());
        turmaProfessorRepository.saveAndFlush(vinculo);

        when(sessaoService.autenticar(anyString())).thenAnswer(invocation -> {
            String token = invocation.getArgument(0);
            if (!TOKEN_TESTE.equals(token)) {
                return Optional.empty();
            }
            return Optional.of(
                    new UsuarioAutenticado(
                            professorId,
                            UUID.randomUUID(),
                            "Professor Teste",
                            professor.getEmail(),
                            Perfil.PROFESSOR
                    )
            );
        });
    }

    @Test
    void postCriaCom201() throws Exception {
        // Arrange
        String corpo = """
                {
                  "professorId": "%s",
                  "tipo": "DIA",
                  "titulo": "Atividade integracao",
                  "descricao": "Teste API"
                }
                """.formatted(professorId);

        // Act
        ResultActions resultado = mockMvc.perform(
                post("/api/atividades")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN_TESTE)
                        .header("turmaUUID", turmaId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo)
        );

        // Assert
        resultado
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/atividades/")));
    }

    @Test
    void postTituloVazioRetorna400() throws Exception {
        // Arrange
        String corpo = """
                {
                  "professorId": "%s",
                  "tipo": "DIA",
                  "titulo": "",
                  "descricao": "Invalido"
                }
                """.formatted(professorId);

        // Act
        ResultActions resultado = mockMvc.perform(
                post("/api/atividades")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN_TESTE)
                        .header("turmaUUID", turmaId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo)
        );

        // Assert
        resultado
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").exists())
                .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"));
    }

    @Test
    void getInexistenteRetorna404() throws Exception {
        // Arrange
        UUID idInexistente = UUID.randomUUID();

        // Act
        ResultActions resultado = mockMvc.perform(
                get("/api/atividades/{id}", idInexistente)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN_TESTE)
        );

        // Assert
        resultado
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("ATIVIDADE_NAO_ENCONTRADA"))
                .andExpect(jsonPath("$.mensagem").value("Atividade não encontrada."));
    }

    @Test
    void criarEConsultar() throws Exception {
        // Arrange
        String corpo = """
                {
                  "professorId": "%s",
                  "tipo": "CASA",
                  "titulo": "Fluxo completo",
                  "descricao": "Integracao",
                  "prazoConclusao": "2026-12-31"
                }
                """.formatted(professorId);

        // Act
        MvcResult criacao = mockMvc.perform(
                        post("/api/atividades")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN_TESTE)
                                .header("turmaUUID", turmaId.toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(corpo)
                )
                .andExpect(status().isCreated())
                .andReturn();

        String location = criacao.getResponse().getHeader("Location");
        String id = location.substring(location.lastIndexOf('/') + 1);

        ResultActions consulta = mockMvc.perform(
                get("/api/atividades/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN_TESTE)
        );

        // Assert
        consulta
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Fluxo completo"))
                .andExpect(jsonPath("$.tipo").value("CASA"))
                .andExpect(jsonPath("$.turmaId").value(turmaId.toString()));
    }
}
