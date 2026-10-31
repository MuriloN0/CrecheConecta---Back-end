package com.crecheconecta.controller.professor;

import com.crecheconecta.controller.base.IntegracaoTestBase;
import com.crecheconecta.dto.ProfessorResponse;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.SituacaoCadastro;
import com.crecheconecta.entity.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProfessorControllerSegurancaIntegrationTest extends IntegracaoTestBase {

    private static final String BASE_PATH = "/api/professores";

    private Usuario professor;

    @BeforeEach
    void cadastrarProfessor() {
        professor = criarUsuario(Perfil.PROFESSOR);
    }

    @Test
    void buscarProfessor_semLogin_deveRetornar401() {
        ResponseEntity<Map<String, Object>> response = client.get().uri(BASE_PATH + "/" + professor.getId())
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("codigo")).isEqualTo("NAO_AUTENTICADO");
    }

    @Test
    void buscarProfessor_comoProfessor_deveRetornar403() {
        String tokenProfessor = autenticar(criarUsuario(Perfil.PROFESSOR));

        ResponseEntity<Map<String, Object>> response = client.get().uri(BASE_PATH + "/" + professor.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenProfessor))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().get("codigo")).isEqualTo("ACESSO_NEGADO");
        assertThat(response.getBody().get("detail")).isEqualTo("Você não possui permissão para esta operação.");
    }

    @Test
    void buscarProfessor_comoDirecao_deveRetornar200() {
        String tokenDirecao = autenticar(criarUsuario(Perfil.DIRECAO));

        ResponseEntity<ProfessorResponse> response = client.get().uri(BASE_PATH + "/" + professor.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenDirecao))
                .retrieve()
                .toEntity(ProfessorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ProfessorResponse body = response.getBody();
        assertThat(body.id()).isEqualTo(professor.getId());
        assertThat(body.nome()).isEqualTo(professor.getNome());
        assertThat(body.email()).isEqualTo(professor.getEmail());
        assertThat(body.ativo()).isTrue();
        assertThat(body.situacaoCadastro()).isEqualTo(SituacaoCadastro.APROVADO);
    }
}
