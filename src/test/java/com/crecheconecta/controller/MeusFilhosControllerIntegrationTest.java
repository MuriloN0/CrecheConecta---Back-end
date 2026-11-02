package com.crecheconecta.controller;

import com.crecheconecta.dto.AlunoResponse;
import com.crecheconecta.dto.CadastrarTurmaRequest;
import com.crecheconecta.dto.FilhoResponse;
import com.crecheconecta.dto.InativarAlunoRequest;
import com.crecheconecta.dto.MatricularAlunoRequest;
import com.crecheconecta.dto.MatriculaTurmaResponse;
import com.crecheconecta.dto.TurmaResponse;
import com.crecheconecta.dto.VincularResponsavelRequest;
import com.crecheconecta.dto.AlunoResponsavelResponse;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MeusFilhosControllerIntegrationTest extends BaseControllerIntegrationTest {

    private static final String MEUS_FILHOS = "/api/meus-filhos";

    @Test
    void listar_comFilhoVinculadoEMatriculado_deveRetornar200ComTurma() {

        // Arrange
        Usuario pai = criarUsuario(Perfil.RESPONSAVEL);
        AlunoResponse aluno = criarAluno();
        vincular(aluno.id(), pai.getId());
        TurmaResponse turma = criarTurma();
        matricular(turma.id(), aluno.id());
        String tokenPai = autenticar(pai);

        // Act
        var resposta = chamar(HttpMethod.GET, MEUS_FILHOS, tokenPai, null, FilhoResponse[].class);

        // Assert
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getHeaders().getCacheControl()).contains("no-store");
        assertThat(resposta.getBody()).isNotNull().hasSize(1);

        FilhoResponse filho = resposta.getBody()[0];

        assertThat(filho.id()).isEqualTo(aluno.id());
        assertThat(filho.nome()).isEqualTo(aluno.nome());
        assertThat(filho.dataNascimento()).isEqualTo(NASCIMENTO);
        assertThat(filho.turma()).isNotNull();
        assertThat(filho.turma().id()).isEqualTo(turma.id());
        assertThat(filho.turma().nome()).isEqualTo(turma.nome());
        assertThat(filho.turma().numeroSala()).isEqualTo(turma.numeroSala());
    }

    @Test
    void listar_semFilhoVinculado_deveRetornar200ComListaVazia() {

        // Arrange
        String tokenPai = autenticar(criarUsuario(Perfil.RESPONSAVEL));

        // Act
        var resposta = chamar(HttpMethod.GET, MEUS_FILHOS, tokenPai, null, FilhoResponse[].class);

        // Assert
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).isNotNull().isEmpty();
    }

    @Test
    void visualizar_comFilhoSemMatricula_deveRetornar200ComTurmaNula() {

        // Arrange
        Usuario pai = criarUsuario(Perfil.RESPONSAVEL);
        AlunoResponse aluno = criarAluno();
        vincular(aluno.id(), pai.getId());
        String tokenPai = autenticar(pai);

        // Act
        var resposta = chamar(HttpMethod.GET, MEUS_FILHOS + "/" + aluno.id(), tokenPai, null, FilhoResponse.class);

        // Assert
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).isNotNull();
        assertThat(resposta.getBody().id()).isEqualTo(aluno.id());
        assertThat(resposta.getBody().turma()).isNull();
    }

    @Test
    void visualizar_comFilhoDeOutroResponsavel_deveRetornar404() {

        // Arrange
        Usuario paiA = criarUsuario(Perfil.RESPONSAVEL);
        Usuario paiB = criarUsuario(Perfil.RESPONSAVEL);
        AlunoResponse filhoDoB = criarAluno();
        vincular(filhoDoB.id(), paiB.getId());
        String tokenPaiA = autenticar(paiA);

        // Act
        var resposta = chamar(HttpMethod.GET, MEUS_FILHOS + "/" + filhoDoB.id(), tokenPaiA, null, ErroApi.class);

        // Assert
        verificarErro(resposta, HttpStatus.NOT_FOUND, "ALUNO_NAO_ENCONTRADO", "Aluno não encontrado.");
    }

    @Test
    void visualizar_comAlunoInexistente_deveRetornar404() {

        // Arrange
        String tokenPai = autenticar(criarUsuario(Perfil.RESPONSAVEL));

        // Act
        var resposta = chamar(HttpMethod.GET, MEUS_FILHOS + "/" + UUID.randomUUID(), tokenPai, null, ErroApi.class);

        // Assert
        verificarErro(resposta, HttpStatus.NOT_FOUND, "ALUNO_NAO_ENCONTRADO", "Aluno não encontrado.");
    }

    @Test
    void listarEVisualizar_comFilhoInativo_naoDevemRetornarOAluno() {

        // Arrange
        Usuario pai = criarUsuario(Perfil.RESPONSAVEL);
        AlunoResponse aluno = criarAluno();
        vincular(aluno.id(), pai.getId());
        inativar(aluno);
        String tokenPai = autenticar(pai);

        // Act
        var lista = chamar(HttpMethod.GET, MEUS_FILHOS, tokenPai, null, FilhoResponse[].class);
        var detalhe = chamar(HttpMethod.GET, MEUS_FILHOS + "/" + aluno.id(), tokenPai, null, ErroApi.class);

        // Assert
        assertThat(lista.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(lista.getBody()).isNotNull().isEmpty();
        verificarErro(detalhe, HttpStatus.NOT_FOUND, "ALUNO_NAO_ENCONTRADO", "Aluno não encontrado.");
    }

    @Test
    void listar_comPerfilDirecao_deveRetornar403() {

        // Act
        var resposta = chamar(HttpMethod.GET, MEUS_FILHOS, tokenDirecao, null, ErroApi.class);

        // Assert
        verificarErro(resposta, HttpStatus.FORBIDDEN, "ACESSO_NEGADO", "Você não possui permissão para esta operação.");
    }

    @Test
    void listar_comPerfilProfessor_deveRetornar403() {

        // Arrange
        String tokenProfessor = autenticar(criarUsuario(Perfil.PROFESSOR));

        // Act
        var resposta = chamar(HttpMethod.GET, MEUS_FILHOS, tokenProfessor, null, ErroApi.class);

        // Assert
        verificarErro(resposta, HttpStatus.FORBIDDEN, "ACESSO_NEGADO", "Você não possui permissão para esta operação.");
    }

    @Test
    void listar_semToken_deveRetornar401() {

        // Act
        var resposta = chamar(HttpMethod.GET, MEUS_FILHOS, null, null, ErroApi.class);

        // Assert
        verificarErro(resposta, HttpStatus.UNAUTHORIZED, "NAO_AUTENTICADO", "Autenticação necessária ou sessão inválida.");
    }

    // ---------- helpers (montam o cenário usando os endpoints da direção) ----------

    private void vincular(UUID alunoId, UUID responsavelId) {
        var resposta = chamar(HttpMethod.POST, ALUNOS + "/" + alunoId + "/responsaveis", tokenDirecao, new VincularResponsavelRequest(responsavelId), AlunoResponsavelResponse.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private TurmaResponse criarTurma() {
        var resposta = chamar(HttpMethod.POST, "/api/turmas", tokenDirecao, new CadastrarTurmaRequest("Turma " + UUID.randomUUID().toString().substring(0, 8), "Sala 1"), TurmaResponse.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resposta.getBody()).isNotNull();
        return resposta.getBody();
    }

    private void matricular(UUID turmaId, UUID alunoId) {
        var resposta = chamar(HttpMethod.POST, "/api/turmas/" + turmaId + "/matriculas", tokenDirecao, new MatricularAlunoRequest(alunoId), MatriculaTurmaResponse.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private void inativar(AlunoResponse aluno) {
        var resposta = chamar(HttpMethod.POST, ALUNOS + "/" + aluno.id() + "/inativar", tokenDirecao, new InativarAlunoRequest(aluno.versao()), AlunoResponse.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
