package com.crecheconecta.controller;

import com.crecheconecta.dto.AlunoResponsavelResponse;
import com.crecheconecta.dto.MalaVirtualResponse;
import com.crecheconecta.dto.VincularResponsavelRequest;
import com.crecheconecta.entity.ItemMala;
import com.crecheconecta.entity.MalaVirtual;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.Usuario;
import com.crecheconecta.repository.ItemMalaRepository;
import com.crecheconecta.repository.MalaVirtualRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MalaVirtualFilhoControllerIntegrationTest extends BaseControllerIntegrationTest {

    @Autowired
    private MalaVirtualRepository malas;

    @Autowired
    private ItemMalaRepository itens;

    @Test
    void listar_comMalas_deveRetornarMaisRecentePrimeiroComItensEPendentes() {

        // Arrange
        Familia familia = criarFamilia();
        Familia outraFamilia = criarFamilia();

        UUID antiga = salvarMala(familia.alunoId(), LocalDate.of(2026, 10, 28), null);
        salvarItem(antiga, "Toalha", 1, true);

        UUID recente = salvarMala(familia.alunoId(), LocalDate.of(2026, 11, 2), "Mandar até sexta.");
        salvarItem(recente, "pomada", 1, false);
        salvarItem(recente, "Fralda", 5, true);
        salvarItem(recente, "Muda de roupa", 2, false);

        salvarItem(salvarMala(outraFamilia.alunoId(), LocalDate.of(2026, 11, 2), null), "Item de outro aluno", 1, false);

        // Act
        var resposta = chamar(HttpMethod.GET, rota(familia.alunoId()), familia.token(), null, MalaVirtualResponse[].class);

        // Assert
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getHeaders().getCacheControl()).contains("no-store");
        assertThat(resposta.getBody()).isNotNull()
                .extracting(MalaVirtualResponse::id)
                .containsExactly(recente, antiga);

        MalaVirtualResponse maisRecente = resposta.getBody()[0];
        assertThat(maisRecente.data()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(maisRecente.observacao()).isEqualTo("Mandar até sexta.");
        assertThat(maisRecente.itensPendentes()).isEqualTo(2);
        // itens em ordem alfabética, sem diferenciar maiúscula/minúscula
        assertThat(maisRecente.itens())
                .extracting(MalaVirtualResponse.Item::nome)
                .containsExactly("Fralda", "Muda de roupa", "pomada");
        assertThat(maisRecente.itens().get(0).quantidade()).isEqualTo(5);
        assertThat(maisRecente.itens().get(0).enviado()).isTrue();

        assertThat(resposta.getBody()[1].itensPendentes()).isZero();
    }

    @Test
    void listar_comMaisDe20Malas_deveRetornarSoAs20MaisRecentes() {

        // Arrange
        Familia familia = criarFamilia();
        LocalDate inicio = LocalDate.of(2026, 10, 1);
        for (int dia = 0; dia < 21; dia++) {
            salvarMala(familia.alunoId(), inicio.plusDays(dia), null);
        }

        // Act
        var resposta = chamar(HttpMethod.GET, rota(familia.alunoId()), familia.token(), null, MalaVirtualResponse[].class);

        // Assert
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).isNotNull().hasSize(20);
        assertThat(resposta.getBody()[0].data()).isEqualTo(inicio.plusDays(20));
        assertThat(resposta.getBody())
                .extracting(MalaVirtualResponse::data)
                .doesNotContain(inicio);
    }

    @Test
    void listar_semMalas_deveRetornar200ComListaVazia() {

        // Arrange
        Familia familia = criarFamilia();

        // Act
        var resposta = chamar(HttpMethod.GET, rota(familia.alunoId()), familia.token(), null, MalaVirtualResponse[].class);

        // Assert
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).isNotNull().isEmpty();
    }

    @Test
    void listar_comFilhoDeOutroResponsavel_deveRetornar404() {

        // Arrange
        Familia familiaA = criarFamilia();
        Familia familiaB = criarFamilia();
        salvarMala(familiaB.alunoId(), LocalDate.of(2026, 11, 2), null);

        // Act
        var resposta = chamar(HttpMethod.GET, rota(familiaB.alunoId()), familiaA.token(), null, ErroApi.class);

        // Assert
        verificarErro(resposta, HttpStatus.NOT_FOUND, "ALUNO_NAO_ENCONTRADO", "Aluno não encontrado.");
    }

    @Test
    void listar_comPerfilDirecao_deveRetornar403() {

        // Arrange
        Familia familia = criarFamilia();

        // Act
        var resposta = chamar(HttpMethod.GET, rota(familia.alunoId()), tokenDirecao, null, ErroApi.class);

        // Assert
        verificarErro(resposta, HttpStatus.FORBIDDEN, "ACESSO_NEGADO", "Você não possui permissão para esta operação.");
    }

    // ---------- helpers ----------

    /** Um pai autenticado + um filho vinculado a ele. */
    private record Familia(String token, UUID alunoId) {
    }

    private Familia criarFamilia() {
        Usuario pai = criarUsuario(Perfil.RESPONSAVEL);
        UUID alunoId = criarAluno().id();

        var vinculo = chamar(HttpMethod.POST, ALUNOS + "/" + alunoId + "/responsaveis", tokenDirecao, new VincularResponsavelRequest(pai.getId()), AlunoResponsavelResponse.class);
        assertThat(vinculo.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        return new Familia(autenticar(pai), alunoId);
    }

    /** Grava a mala direto no banco: o lado do professor (montar a mala) ainda não existe. */
    private UUID salvarMala(UUID alunoId, LocalDate data, String observacao) {
        var mala = new MalaVirtual();
        mala.setAlunoId(alunoId);
        mala.setTurmaId(UUID.randomUUID());
        mala.setProfessorId(UUID.randomUUID());
        mala.setData(data);
        mala.setObservacao(observacao);
        mala.setCriadoEm(Instant.now());
        return malas.saveAndFlush(mala).getId();
    }

    private void salvarItem(UUID malaId, String nome, int quantidade, boolean enviado) {
        var item = new ItemMala();
        item.setMalaId(malaId);
        item.setNome(nome);
        item.setQuantidade(quantidade);
        item.setEnviado(enviado);
        itens.saveAndFlush(item);
    }

    private String rota(UUID alunoId) {
        return "/api/meus-filhos/" + alunoId + "/mala";
    }
}
