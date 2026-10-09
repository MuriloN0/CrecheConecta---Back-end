package com.crecheconecta.controller.atividades;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "saude.criptografia.chave=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "app.email.resend.api-key=test-api-key",
        "app.email.resend.remetente=test@example.com",
        "app.seguranca.hmac-chave-base64=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "spring.datasource.url=jdbc:postgresql://localhost:5433/CrecheConecta",
        "spring.datasource.username=postgres",
        "spring.datasource.password=admin",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.autoconfigure.exclude=org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"
})
@Transactional
class AtividadeAPIIntegracaoTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void postCriaCom201() throws Exception {
        // Arrange
        UUID turmaId = UUID.randomUUID();
        UUID professorId = UUID.randomUUID();
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
        UUID turmaId = UUID.randomUUID();
        String corpo = """
                {
                  "professorId": "%s",
                  "tipo": "DIA",
                  "titulo": "",
                  "descricao": "Invalido"
                }
                """.formatted(UUID.randomUUID());

        // Act
        ResultActions resultado = mockMvc.perform(
                post("/api/atividades")
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
        ResultActions resultado = mockMvc.perform(get("/api/atividades/{id}", idInexistente));

        // Assert
        resultado
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("ATIVIDADE_NAO_ENCONTRADA"))
                .andExpect(jsonPath("$.mensagem").value("Atividade não encontrada."));
    }

    @Test
    void criarEConsultar() throws Exception {
        // Arrange
        UUID turmaId = UUID.randomUUID();
        UUID professorId = UUID.randomUUID();
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
                                .header("turmaUUID", turmaId.toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(corpo)
                )
                .andExpect(status().isCreated())
                .andReturn();

        String location = criacao.getResponse().getHeader("Location");
        String id = location.substring(location.lastIndexOf('/') + 1);

        ResultActions consulta = mockMvc.perform(get("/api/atividades/{id}", id));

        // Assert
        consulta
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Fluxo completo"))
                .andExpect(jsonPath("$.tipo").value("CASA"))
                .andExpect(jsonPath("$.turmaId").value(turmaId.toString()));
    }
}
