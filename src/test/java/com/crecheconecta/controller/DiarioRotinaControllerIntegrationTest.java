package com.crecheconecta.controller;

import com.crecheconecta.dto.AlunoResponsavelResponse;
import com.crecheconecta.dto.CadastrarTurmaRequest;
import com.crecheconecta.dto.DiarioAlunoResponse;
import com.crecheconecta.dto.DiarioRegistroResponse;
import com.crecheconecta.dto.DiarioRotinaResponse;
import com.crecheconecta.dto.MatricularAlunoRequest;
import com.crecheconecta.dto.RegistrarDiarioRequest;
import com.crecheconecta.dto.TurmaResponse;
import com.crecheconecta.dto.VincularProfessorTurmaRequest;
import com.crecheconecta.dto.VincularResponsavelRequest;
import com.crecheconecta.entity.Alimentacao;
import com.crecheconecta.entity.Higiene;
import com.crecheconecta.entity.Humor;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.Sono;
import com.crecheconecta.entity.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DiarioRotinaControllerIntegrationTest extends BaseControllerIntegrationTest {

    @Test
    void registrar_diarioNovo_deveRetornarVersaoZeroEEditavel() {

        // Arrange
        Cenario c = criarCenario();

        // Act
        var resposta = chamar(HttpMethod.PUT, rota(c.turmaId(), hoje(), c.alunoId()), c.tokenProfessor(), corpo(null), DiarioRegistroResponse.class);

        // Assert
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getHeaders().getCacheControl()).contains("no-store");
        DiarioRegistroResponse diario = resposta.getBody();
        assertThat(diario).isNotNull();
        assertThat(diario.alunoId()).isEqualTo(c.alunoId());
        assertThat(diario.turmaId()).isEqualTo(c.turmaId());
        assertThat(diario.higiene()).isEqualTo(Higiene.HIGIENIZADO);
        assertThat(diario.editavel()).isTrue();
        assertThat(diario.versao()).isZero();
    }

    @Test
    void consultar_deveListarOsAlunosDaTurmaComESemDiario() {

        // Arrange
        Cenario c = criarCenario();
        UUID outroAluno = matricular(c.turmaId());
        registrarComSucesso(c, c.alunoId(), null);

        // Act
        var resposta = chamar(HttpMethod.GET, rotaTurma(c.turmaId(), hoje()), c.tokenProfessor(), null, DiarioAlunoResponse[].class);

        // Assert
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).hasSize(2);
        for (DiarioAlunoResponse linha : resposta.getBody()) {
            if (linha.alunoId().equals(c.alunoId())) {
                assertThat(linha.diario()).isNotNull();
            } else {
                assertThat(linha.alunoId()).isEqualTo(outroAluno);
                assertThat(linha.diario()).isNull();
            }
        }
    }

    @Test
    void editar_comVersaoCorreta_deveIncrementarAVersao() {

        // Arrange
        Cenario c = criarCenario();
        DiarioRegistroResponse criado = registrarComSucesso(c, c.alunoId(), null);
        var novo = new RegistrarDiarioRequest(Alimentacao.COMEU_POUCO, Sono.NAO_DORMIU, false, Humor.CHOROSO, Higiene.PARCIAL, "Ficou com saudade.", criado.versao());

        // Act
        var resposta = chamar(HttpMethod.PUT, rota(c.turmaId(), hoje(), c.alunoId()), c.tokenProfessor(), novo, DiarioRegistroResponse.class);

        // Assert
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).isNotNull();
        assertThat(resposta.getBody().id()).isEqualTo(criado.id());
        assertThat(resposta.getBody().versao()).isEqualTo(criado.versao() + 1);
        assertThat(resposta.getBody().humor()).isEqualTo(Humor.CHOROSO);
        assertThat(resposta.getBody().higiene()).isEqualTo(Higiene.PARCIAL);
        assertThat(resposta.getBody().observacaoProfessor()).isEqualTo("Ficou com saudade.");
    }

    @Test
    void editar_comVersaoDesatualizada_deveRetornar409() {

        // Arrange
        Cenario c = criarCenario();
        registrarComSucesso(c, c.alunoId(), null);

        // Act
        var resposta = chamar(HttpMethod.PUT, rota(c.turmaId(), hoje(), c.alunoId()), c.tokenProfessor(), corpo(99L), ErroApi.class);

        // Assert
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resposta.getBody()).isNotNull();
        assertThat(resposta.getBody().codigo()).isEqualTo("CONFLITO_VERSAO");
    }

    @Test
    void registrar_diarioNovoComVersaoInformada_deveRetornar409() {

        // Arrange
        Cenario c = criarCenario();

        // Act
        var resposta = chamar(HttpMethod.PUT, rota(c.turmaId(), hoje(), c.alunoId()), c.tokenProfessor(), corpo(0L), ErroApi.class);

        // Assert
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resposta.getBody()).isNotNull();
        assertThat(resposta.getBody().codigo()).isEqualTo("CONFLITO_VERSAO");
    }

    @Test
    void registrar_comDataFutura_deveRetornar400() {

        // Arrange
        Cenario c = criarCenario();

        // Act
        var resposta = chamar(HttpMethod.PUT, rota(c.turmaId(), hoje().plusDays(1), c.alunoId()), c.tokenProfessor(), corpo(null), ErroApi.class);

        // Assert
        verificarErro(resposta, HttpStatus.BAD_REQUEST, "REGRA_NEGOCIO", "A data do diário não pode ser futura.");
    }

    @Test
    void registrar_comDiaAnterior_devePermitir() {

        // Arrange
        Cenario c = criarCenario();

        // Act
        var resposta = chamar(HttpMethod.PUT, rota(c.turmaId(), hoje().minusDays(1), c.alunoId()), c.tokenProfessor(), corpo(null), DiarioRegistroResponse.class);

        // Assert
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).isNotNull();
        assertThat(resposta.getBody().data()).isEqualTo(hoje().minusDays(1));
    }

    @Test
    void registrar_alunoSemMatriculaNaTurma_deveRetornar400() {

        // Arrange
        Cenario c = criarCenario();
        UUID alunoDeFora = criarAluno().id();

        // Act
        var resposta = chamar(HttpMethod.PUT, rota(c.turmaId(), hoje(), alunoDeFora), c.tokenProfessor(), corpo(null), ErroApi.class);

        // Assert
        verificarErro(resposta, HttpStatus.BAD_REQUEST, "REGRA_NEGOCIO", "O aluno não possui matrícula atual nesta turma.");
    }

    @Test
    void registrar_professorSemVinculoComATurma_deveRetornar403() {

        // Arrange
        Cenario c = criarCenario();
        String tokenSemVinculo = autenticar(criarUsuario(Perfil.PROFESSOR));

        // Act
        var resposta = chamar(HttpMethod.PUT, rota(c.turmaId(), hoje(), c.alunoId()), tokenSemVinculo, corpo(null), ErroApi.class);

        // Assert
        verificarErro(resposta, HttpStatus.FORBIDDEN, "ACESSO_NEGADO", "Você não possui permissão para esta operação.");
    }

    @Test
    void editar_diarioDeOutroProfessorDaMesmaTurma_deveRetornar403() {

        // Arrange
        Cenario c = criarCenario();
        DiarioRegistroResponse criado = registrarComSucesso(c, c.alunoId(), null);
        String tokenColega = professorVinculado(c.turmaId());

        // Act
        var resposta = chamar(HttpMethod.PUT, rota(c.turmaId(), hoje(), c.alunoId()), tokenColega, corpo(criado.versao()), ErroApi.class);

        // Assert
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resposta.getBody()).isNotNull();
        assertThat(resposta.getBody().codigo()).isEqualTo("ACESSO_NEGADO");
    }

    @Test
    void excluir_peloAutor_deveRetornar204EDepoisSumirDaLista() {

        // Arrange
        Cenario c = criarCenario();
        registrarComSucesso(c, c.alunoId(), null);

        // Act
        var exclusao = chamar(HttpMethod.DELETE, rota(c.turmaId(), hoje(), c.alunoId()), c.tokenProfessor(), null, Void.class);
        var lista = chamar(HttpMethod.GET, rotaTurma(c.turmaId(), hoje()), c.tokenProfessor(), null, DiarioAlunoResponse[].class);
        var repetida = chamar(HttpMethod.DELETE, rota(c.turmaId(), hoje(), c.alunoId()), c.tokenProfessor(), null, ErroApi.class);

        // Assert
        assertThat(exclusao.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(lista.getBody()).hasSize(1);
        assertThat(lista.getBody()[0].diario()).isNull();
        verificarErro(repetida, HttpStatus.NOT_FOUND, "DIARIO_ROTINA_NAO_ENCONTRADO", "Diário de rotina não encontrado.");
    }

    @Test
    void excluir_diarioDeOutroProfessor_deveRetornar403() {

        // Arrange
        Cenario c = criarCenario();
        registrarComSucesso(c, c.alunoId(), null);
        String tokenColega = professorVinculado(c.turmaId());

        // Act
        var resposta = chamar(HttpMethod.DELETE, rota(c.turmaId(), hoje(), c.alunoId()), tokenColega, null, ErroApi.class);

        // Assert
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void registrar_comPerfilDirecao_deveRetornar403() {

        // Arrange
        Cenario c = criarCenario();

        // Act
        var resposta = chamar(HttpMethod.PUT, rota(c.turmaId(), hoje(), c.alunoId()), tokenDirecao, corpo(null), ErroApi.class);

        // Assert
        verificarErro(resposta, HttpStatus.FORBIDDEN, "ACESSO_NEGADO", "Você não possui permissão para esta operação.");
    }

    @Test
    void registrar_semCamposObrigatorios_deveRetornar400() {

        // Arrange
        Cenario c = criarCenario();
        Map<String, Object> incompleto = Map.of("alimentacao", "COMEU_TUDO");

        // Act
        var resposta = chamar(HttpMethod.PUT, rota(c.turmaId(), hoje(), c.alunoId()), c.tokenProfessor(), incompleto, ErroApi.class);

        // Assert
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void responsavel_deveVerODiarioComHigieneRegistradoPeloProfessor() {

        // Arrange
        Cenario c = criarCenario();
        Usuario pai = criarUsuario(Perfil.RESPONSAVEL);
        var vinculo = chamar(HttpMethod.POST, ALUNOS + "/" + c.alunoId() + "/responsaveis", tokenDirecao, new VincularResponsavelRequest(pai.getId()), AlunoResponsavelResponse.class);
        assertThat(vinculo.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String tokenPai = autenticar(pai);
        registrarComSucesso(c, c.alunoId(), null);

        // Act
        var resposta = chamar(HttpMethod.GET, "/api/meus-filhos/" + c.alunoId() + "/diario?data=" + hoje(), tokenPai, null, DiarioRotinaResponse.class);

        // Assert
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).isNotNull();
        assertThat(resposta.getBody().higiene()).isEqualTo(Higiene.HIGIENIZADO);
        assertThat(resposta.getBody().observacaoProfessor()).isEqualTo("Brincou bastante.");
    }

    // ---------- helpers ----------

    /** Uma turma com um aluno matriculado e um professor vinculado (já autenticado). */
    private record Cenario(UUID turmaId, UUID alunoId, String tokenProfessor) {
    }

    private Cenario criarCenario() {
        var turma = chamar(HttpMethod.POST, "/api/turmas", tokenDirecao, new CadastrarTurmaRequest("Turma " + UUID.randomUUID().toString().substring(0, 8), "S1"), TurmaResponse.class);
        assertThat(turma.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID turmaId = turma.getBody().id();

        UUID alunoId = matricular(turmaId);
        String tokenProfessor = professorVinculado(turmaId);

        return new Cenario(turmaId, alunoId, tokenProfessor);
    }

    /** Cria um aluno e matricula na turma. */
    private UUID matricular(UUID turmaId) {
        UUID alunoId = criarAluno().id();
        var matricula = chamar(HttpMethod.POST, "/api/turmas/" + turmaId + "/matriculas", tokenDirecao, new MatricularAlunoRequest(alunoId), Void.class);
        assertThat(matricula.getStatusCode().is2xxSuccessful()).isTrue();
        return alunoId;
    }

    /** Cria um professor, vincula à turma e devolve o token dele. */
    private String professorVinculado(UUID turmaId) {
        Usuario professor = criarUsuario(Perfil.PROFESSOR);
        var vinculo = chamar(HttpMethod.POST, "/api/turmas/" + turmaId + "/professores", tokenDirecao, new VincularProfessorTurmaRequest(professor.getId()), Void.class);
        assertThat(vinculo.getStatusCode().is2xxSuccessful()).isTrue();
        return autenticar(professor);
    }

    private DiarioRegistroResponse registrarComSucesso(Cenario c, UUID alunoId, Long versao) {
        var resposta = chamar(HttpMethod.PUT, rota(c.turmaId(), hoje(), alunoId), c.tokenProfessor(), corpo(versao), DiarioRegistroResponse.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resposta.getBody();
    }

    private RegistrarDiarioRequest corpo(Long versao) {
        return new RegistrarDiarioRequest(Alimentacao.COMEU_TUDO, Sono.DORMIU_BEM, true, Humor.FELIZ, Higiene.HIGIENIZADO, "Brincou bastante.", versao);
    }

    // Mesmo fuso que o service usa para decidir o que é "hoje".
    private LocalDate hoje() {
        return LocalDate.now(ZoneId.of("America/Sao_Paulo"));
    }

    private String rotaTurma(UUID turmaId, LocalDate data) {
        return "/api/turmas/" + turmaId + "/diarios/" + data;
    }

    private String rota(UUID turmaId, LocalDate data, UUID alunoId) {
        return rotaTurma(turmaId, data) + "/alunos/" + alunoId;
    }
}
