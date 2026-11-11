package com.crecheconecta.controller;

import com.crecheconecta.dto.AlunoResponsavelResponse;
import com.crecheconecta.dto.AtividadeResponseDTO;
import com.crecheconecta.dto.CadastrarTurmaRequest;
import com.crecheconecta.dto.MatriculaTurmaResponse;
import com.crecheconecta.dto.MatricularAlunoRequest;
import com.crecheconecta.dto.TurmaResponse;
import com.crecheconecta.dto.VincularResponsavelRequest;
import com.crecheconecta.entity.AtividadeEntity;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.Usuario;
import com.crecheconecta.model.TipoAtividade;
import com.crecheconecta.repository.AtividadeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AtividadesCasaControllerIntegrationTest extends BaseControllerIntegrationTest {

    @Autowired
    private AtividadeRepository atividades;

    @Test
    void listar_comFilhoMatriculado_deveRetornarSoAtividadesDeCasaDaTurmaDoFilhoOrdenadasPorPrazo() {

        // Arrange
        Familia familia = criarFamilia();
        UUID turmaDoFilho = criarTurma();
        UUID outraTurma = criarTurma();
        matricular(turmaDoFilho, familia.alunoId());

        UUID prazoLonge = salvarAtividade(turmaDoFilho, TipoAtividade.CASA, "Desenhar a família", LocalDate.now().plusDays(5));
        UUID prazoPerto = salvarAtividade(turmaDoFilho, TipoAtividade.CASA, "Trazer uma folha seca", LocalDate.now().plusDays(2));
        UUID semPrazo = salvarAtividade(turmaDoFilho, TipoAtividade.CASA, "Ler uma história", null);
        salvarAtividade(turmaDoFilho, TipoAtividade.DIA, "Pintura em sala", null);
        salvarAtividade(outraTurma, TipoAtividade.CASA, "Atividade de outra turma", LocalDate.now().plusDays(1));

        // Act
        var resposta = chamar(HttpMethod.GET, rota(familia.alunoId()), familia.token(), null, AtividadeResponseDTO[].class);

        // Assert
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getHeaders().getCacheControl()).contains("no-store");
        assertThat(resposta.getBody()).isNotNull()
                .extracting(AtividadeResponseDTO::id)
                .containsExactly(prazoPerto, prazoLonge, semPrazo);
        assertThat(resposta.getBody())
                .extracting(AtividadeResponseDTO::tipo)
                .containsOnly(TipoAtividade.CASA);
        assertThat(resposta.getBody())
                .extracting(AtividadeResponseDTO::turmaId)
                .containsOnly(turmaDoFilho);
    }

    @Test
    void listar_comFilhoSemMatricula_deveRetornar200ComListaVazia() {

        // Arrange
        Familia familia = criarFamilia();
        salvarAtividade(criarTurma(), TipoAtividade.CASA, "Atividade qualquer", null);

        // Act
        var resposta = chamar(HttpMethod.GET, rota(familia.alunoId()), familia.token(), null, AtividadeResponseDTO[].class);

        // Assert
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).isNotNull().isEmpty();
    }

    @Test
    void listar_comFilhoDeOutroResponsavel_deveRetornar404() {

        // Arrange
        Familia familiaA = criarFamilia();
        Familia familiaB = criarFamilia();
        UUID turmaDoB = criarTurma();
        matricular(turmaDoB, familiaB.alunoId());
        salvarAtividade(turmaDoB, TipoAtividade.CASA, "Atividade do filho do B", null);

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
    void listar_semToken_deveRetornar401() {

        // Act
        var resposta = chamar(HttpMethod.GET, rota(UUID.randomUUID()), null, null, ErroApi.class);

        // Assert
        verificarErro(resposta, HttpStatus.UNAUTHORIZED, "NAO_AUTENTICADO", "Autenticação necessária ou sessão inválida.");
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

    private UUID criarTurma() {
        var resposta = chamar(HttpMethod.POST, "/api/turmas", tokenDirecao, new CadastrarTurmaRequest("Turma " + UUID.randomUUID().toString().substring(0, 8), "Sala 1"), TurmaResponse.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resposta.getBody()).isNotNull();
        return resposta.getBody().id();
    }

    private void matricular(UUID turmaId, UUID alunoId) {
        var resposta = chamar(HttpMethod.POST, "/api/turmas/" + turmaId + "/matriculas", tokenDirecao, new MatricularAlunoRequest(alunoId), MatriculaTurmaResponse.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    /** Grava a atividade direto no banco: o módulo de atividades é de outro colega; aqui só consumimos. */
    private UUID salvarAtividade(UUID turmaId, TipoAtividade tipo, String titulo, LocalDate prazo) {
        var atividade = new AtividadeEntity();
        atividade.setTurmaId(turmaId);
        atividade.setProfessorId(UUID.randomUUID());
        atividade.setTipo(tipo);
        atividade.setTitulo(titulo);
        atividade.setDescricao("Descrição de " + titulo);
        atividade.setDataCriacao(Instant.now());
        atividade.setPrazoConclusao(prazo);
        return atividades.saveAndFlush(atividade).getId();
    }

    private String rota(UUID alunoId) {
        return "/api/meus-filhos/" + alunoId + "/atividades-casa";
    }
}
