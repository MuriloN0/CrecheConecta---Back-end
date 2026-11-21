package com.crecheconecta.controller;

import com.crecheconecta.entity.Aluno;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.repository.AlunoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AlunoAniversarioControllerIntegrationTest
        extends BaseControllerIntegrationTest {

    @Autowired
    private AlunoRepository alunos;

    private final String prefixo = "Aniversario-" + UUID.randomUUID();

    @Test
    void filtraMesIndependentementeDoAnoComNomeESituacao() {
        var primeiro = salvar("Ana", LocalDate.of(2023, 6, 10), true);
        var segundo = salvar("Bia", LocalDate.of(2025, 6, 20), true);
        var inativo = salvar("Caio", LocalDate.of(2024, 6, 5), false);

        salvar("Davi", LocalDate.of(2024, 5, 10), true);
        salvar("Anonimizado", null, false);

        String rota = ALUNOS + "?nome=" + prefixo + "&mesNascimento=6";

        var ativos = chamar(
                HttpMethod.GET, rota + "&ativo=true",
                tokenDirecao, null, Map.class
        );

        assertThat(ativos.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ids(ativos.getBody())).containsExactly(
                primeiro.getId().toString(),
                segundo.getId().toString()
        );

        var todos = chamar(
                HttpMethod.GET, rota, tokenDirecao, null, Map.class
        );

        assertThat(todos.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ids(todos.getBody()))
                .hasSize(3)
                .contains(inativo.getId().toString());

        var somenteNome = chamar(
                HttpMethod.GET,
                rota.replace(prefixo, prefixo + "Ana"),
                tokenDirecao,
                null,
                Map.class
        );

        assertThat(somenteNome.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ids(somenteNome.getBody()))
                .containsExactly(primeiro.getId().toString());

        var semMes = chamar(
                HttpMethod.GET,
                ALUNOS + "?nome=" + prefixo,
                tokenDirecao,
                null,
                Map.class
        );

        assertThat(semMes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ids(semMes.getBody())).hasSize(5);
    }

    @Test
    void paginaSomenteOsResultadosDoMesSelecionado() {
        salvar("A", LocalDate.of(2023, 12, 10), true);
        salvar("B", LocalDate.of(2024, 12, 20), true);
        var ultimo = salvar("C", LocalDate.of(2025, 12, 30), true);
        salvar("OutroMes", LocalDate.of(2024, 1, 1), true);

        String rota = ALUNOS + "?nome=" + prefixo
                + "&mesNascimento=12&tamanho=2";

        var primeira = chamar(
                HttpMethod.GET, rota + "&pagina=0",
                tokenDirecao, null, Map.class
        );

        assertThat(primeira.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ids(primeira.getBody())).hasSize(2);

        assertThat(
                ((Number) primeira.getBody().get("totalElements")).longValue()
        ).isEqualTo(3);

        assertThat(
                ((Number) primeira.getBody().get("totalPages")).intValue()
        ).isEqualTo(2);

        var segunda = chamar(
                HttpMethod.GET, rota + "&pagina=1",
                tokenDirecao, null, Map.class
        );

        assertThat(segunda.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ids(segunda.getBody()))
                .containsExactly(ultimo.getId().toString());
    }

    @Test
    void rejeitaMesInvalidoESolicitanteSemPermissao() {
        for (String mes : new String[]{"0", "13", "junho"}) {
            var resposta = chamar(
                    HttpMethod.GET,
                    ALUNOS + "?mesNascimento=" + mes,
                    tokenDirecao,
                    null,
                    Map.class
            );

            assertThat(resposta.getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }

        assertThat(chamar(
                HttpMethod.GET,
                ALUNOS + "?mesNascimento=6",
                null,
                null,
                Map.class
        ).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        String professor = autenticar(criarUsuario(Perfil.PROFESSOR));

        assertThat(chamar(
                HttpMethod.GET,
                ALUNOS + "?mesNascimento=6",
                professor,
                null,
                Map.class
        ).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private List<String> ids(Map<?, ?> pagina) {
        return ((List<?>) pagina.get("content")).stream()
                .map(item -> (String) ((Map<?, ?>) item).get("id"))
                .toList();
    }

    private Aluno salvar(
            String nome,
            LocalDate nascimento,
            boolean ativo
    ) {
        var aluno = new Aluno();
        aluno.setNome(prefixo + nome);
        aluno.setDataNascimento(nascimento);
        aluno.setAtivo(ativo);

        if (nascimento == null) {
            aluno.setAnoNascimento(2024);
            aluno.setAnonimizadoEm(Instant.now());
        }

        return alunos.saveAndFlush(aluno);
    }
}