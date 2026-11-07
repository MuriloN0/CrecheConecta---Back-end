package com.crecheconecta.controller;

import com.crecheconecta.dto.AlunoResponsavelResponse;
import com.crecheconecta.dto.CadastrarTurmaRequest;
import com.crecheconecta.dto.MalaVirtualResponse;
import com.crecheconecta.dto.MarcarItemMalaRequest;
import com.crecheconecta.dto.MatriculaTurmaResponse;
import com.crecheconecta.dto.MatricularAlunoRequest;
import com.crecheconecta.dto.TurmaResponse;
import com.crecheconecta.dto.VincularResponsavelRequest;
import com.crecheconecta.entity.ItemMala;
import com.crecheconecta.entity.MalaVirtual;
import com.crecheconecta.entity.MarcacaoItemMala;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.Usuario;
import com.crecheconecta.repository.ItemMalaRepository;
import com.crecheconecta.repository.MalaVirtualRepository;
import com.crecheconecta.repository.MarcacaoItemMalaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MalaVirtualFilhoControllerIntegrationTest extends BaseControllerIntegrationTest {

    @Autowired
    private MalaVirtualRepository malas;

    @Autowired
    private ItemMalaRepository itens;

    @Autowired
    private MarcacaoItemMalaRepository marcacoes;

    private UUID professorId;

    private LocalDate amanha;

    @BeforeEach
    void prepararProfessor() {
        professorId = criarUsuario(Perfil.PROFESSOR).getId();
        amanha = LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(1);
    }

    @Test
    void listar_comMalas_deveRetornarMaisRecentePrimeiroComItensEPendentes() {

        // Arrange
        Familia familia = criarFamilia();
        Familia outraFamilia = criarFamilia();

        UUID antiga = salvarMala(familia.turmaId(), LocalDate.of(2026, 10, 28), null);
        marcar(familia, salvarItem(antiga, "Toalha", 1), true);

        UUID recente = salvarMala(familia.turmaId(), LocalDate.of(2026, 11, 2), "Mandar até sexta.");
        salvarItem(recente, "pomada", 1);
        marcar(familia, salvarItem(recente, "Fralda", 5), true);
        salvarItem(recente, "Muda de roupa", 2);

        salvarItem(salvarMala(outraFamilia.turmaId(), LocalDate.of(2026, 11, 2), null), "Item de outra turma", 1);

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
        assertThat(maisRecente.itens())
                .extracting(MalaVirtualResponse.Item::nome)
                .containsExactly("Fralda", "Muda de roupa", "pomada");
        assertThat(maisRecente.itens().get(0).quantidade()).isEqualTo(5);
        assertThat(maisRecente.itens().get(0).enviado()).isTrue();

        assertThat(resposta.getBody()[1].itensPendentes()).isZero();
    }

    @Test
    void listar_marcacaoDeOutroAluno_naoDeveAparecerParaOFilho() {

        // Arrange
        Familia familia = criarFamilia();
        Familia colega = criarFamilia(familia.turmaId());

        UUID mala = salvarMala(familia.turmaId(), amanha, null);
        marcar(colega, salvarItem(mala, "Avental", 1), true);

        // Act
        var resposta = chamar(HttpMethod.GET, rota(familia.alunoId()), familia.token(), null, MalaVirtualResponse[].class);

        // Assert
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).isNotNull().hasSize(1);
        assertThat(resposta.getBody()[0].itensPendentes()).isEqualTo(1);
        assertThat(resposta.getBody()[0].itens().getFirst().enviado()).isFalse();
    }

    @Test
    void listar_comMaisDe20Malas_deveRetornarSoAs20MaisRecentes() {

        // Arrange
        Familia familia = criarFamilia();
        LocalDate inicio = LocalDate.of(2026, 10, 1);
        for (int dia = 0; dia < 21; dia++) {
            salvarMala(familia.turmaId(), inicio.plusDays(dia), null);
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
        salvarMala(familiaB.turmaId(), LocalDate.of(2026, 11, 2), null);

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

    @Test
    void marcarItem_comObservacao_deveRetornarMalaAtualizada() {

        // Arrange
        Familia familia = criarFamilia();
        UUID mala = salvarMala(familia.turmaId(), amanha, null);
        UUID avental = salvarItem(mala, "Avental", 1);
        salvarItem(mala, "Tinta", 2);

        // Act
        var resposta = chamar(HttpMethod.PUT, rotaItem(familia.alunoId(), mala, avental), familia.token(), new MarcarItemMalaRequest(true, "  Vai na mochila "), MalaVirtualResponse.class);

        // Assert
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).isNotNull();
        assertThat(resposta.getBody().itensPendentes()).isEqualTo(1);
        assertThat(resposta.getBody().itens().getFirst().enviado()).isTrue();
        assertThat(resposta.getBody().itens().getFirst().observacao()).isEqualTo("Vai na mochila");
    }

    @Test
    void marcarItem_depoisDaDataDaAula_deveRetornar400() {

        // Arrange
        Familia familia = criarFamilia();
        UUID mala = salvarMala(familia.turmaId(), amanha.minusDays(2), null);
        UUID avental = salvarItem(mala, "Avental", 1);

        // Act
        var resposta = chamar(HttpMethod.PUT, rotaItem(familia.alunoId(), mala, avental), familia.token(), new MarcarItemMalaRequest(true, null), ErroApi.class);

        // Assert
        verificarErro(resposta, HttpStatus.BAD_REQUEST, "REGRA_NEGOCIO", "Não é possível marcar itens depois da data da aula.");
    }

    @Test
    void marcarItem_deMalaDeOutraTurma_deveRetornar404() {

        // Arrange
        Familia familia = criarFamilia();
        Familia outraFamilia = criarFamilia();
        UUID mala = salvarMala(outraFamilia.turmaId(), amanha, null);
        UUID item = salvarItem(mala, "Avental", 1);

        // Act
        var resposta = chamar(HttpMethod.PUT, rotaItem(familia.alunoId(), mala, item), familia.token(), new MarcarItemMalaRequest(true, null), ErroApi.class);

        // Assert
        verificarErro(resposta, HttpStatus.NOT_FOUND, "MALA_VIRTUAL_NAO_ENCONTRADA", "Mala virtual não encontrada.");
    }

    @Test
    void marcarItem_inexistente_deveRetornar404() {

        // Arrange
        Familia familia = criarFamilia();
        UUID mala = salvarMala(familia.turmaId(), amanha, null);

        // Act
        var resposta = chamar(HttpMethod.PUT, rotaItem(familia.alunoId(), mala, UUID.randomUUID()), familia.token(), new MarcarItemMalaRequest(true, null), ErroApi.class);

        // Assert
        verificarErro(resposta, HttpStatus.NOT_FOUND, "ITEM_MALA_NAO_ENCONTRADO", "Item da mala não encontrado.");
    }

    // ---------- helpers ----------

    /** Um pai autenticado + um filho vinculado a ele e matriculado numa turma. */
    private record Familia(String token, UUID responsavelId, UUID alunoId, UUID turmaId) {
    }

    private Familia criarFamilia() {
        return criarFamilia(criarTurma());
    }

    private Familia criarFamilia(UUID turmaId) {
        Usuario pai = criarUsuario(Perfil.RESPONSAVEL);
        UUID alunoId = criarAluno().id();

        var vinculo = chamar(HttpMethod.POST, ALUNOS + "/" + alunoId + "/responsaveis", tokenDirecao, new VincularResponsavelRequest(pai.getId()), AlunoResponsavelResponse.class);
        assertThat(vinculo.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        var matricula = chamar(HttpMethod.POST, "/api/turmas/" + turmaId + "/matriculas", tokenDirecao, new MatricularAlunoRequest(alunoId), MatriculaTurmaResponse.class);
        assertThat(matricula.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        return new Familia(autenticar(pai), pai.getId(), alunoId, turmaId);
    }

    private UUID criarTurma() {
        var resposta = chamar(HttpMethod.POST, "/api/turmas", tokenDirecao, new CadastrarTurmaRequest("Turma " + UUID.randomUUID().toString().substring(0, 8), "Sala 1"), TurmaResponse.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resposta.getBody()).isNotNull();
        return resposta.getBody().id();
    }

    private UUID salvarMala(UUID turmaId, LocalDate data, String observacao) {
        var mala = new MalaVirtual();
        mala.setTurmaId(turmaId);
        mala.setProfessorId(professorId);
        mala.setTitulo("Mala de " + data);
        mala.setData(data);
        mala.setObservacao(observacao);
        mala.setCriadoEm(Instant.now());
        mala.setAtualizadoEm(Instant.now());
        return malas.saveAndFlush(mala).getId();
    }

    private UUID salvarItem(UUID malaId, String nome, int quantidade) {
        var item = new ItemMala();
        item.setMalaId(malaId);
        item.setNome(nome);
        item.setQuantidade(quantidade);
        return itens.saveAndFlush(item).getId();
    }

    private void marcar(Familia familia, UUID itemId, boolean enviado) {
        var marcacao = new MarcacaoItemMala();
        marcacao.setItemId(itemId);
        marcacao.setAlunoId(familia.alunoId());
        marcacao.setEnviado(enviado);
        marcacao.setMarcadoPor(familia.responsavelId());
        marcacao.setAtualizadoEm(Instant.now());
        marcacoes.saveAndFlush(marcacao);
    }

    private String rota(UUID alunoId) {
        return "/api/meus-filhos/" + alunoId + "/mala";
    }

    private String rotaItem(UUID alunoId, UUID malaId, UUID itemId) {
        return rota(alunoId) + "/" + malaId + "/itens/" + itemId;
    }
}
