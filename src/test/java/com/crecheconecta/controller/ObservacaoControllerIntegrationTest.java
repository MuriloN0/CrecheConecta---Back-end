package com.crecheconecta.controller;

import com.crecheconecta.dto.AlunoResponsavelResponse;
import com.crecheconecta.dto.AtualizarObservacaoRequest;
import com.crecheconecta.dto.CadastrarTurmaRequest;
import com.crecheconecta.dto.MatricularAlunoRequest;
import com.crecheconecta.dto.ObservacaoRequest;
import com.crecheconecta.dto.ObservacaoResponse;
import com.crecheconecta.dto.TurmaResponse;
import com.crecheconecta.dto.VincularProfessorTurmaRequest;
import com.crecheconecta.dto.VincularResponsavelRequest;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.Usuario;
import com.crecheconecta.repository.ObservacaoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Observações do aluno: o professor da turma escreve (só o autor edita/exclui);
 * o responsável do aluno, os professores da turma e a direção leem.
 */
class ObservacaoControllerIntegrationTest extends BaseControllerIntegrationTest {

    private static final String CONTEUDO = "Hoje participou bem da roda de música e dividiu os brinquedos.";
    private static final String NOME_USUARIO = "Usuário de integração";

    @Autowired
    private ObservacaoRepository observacoes;

    @Test
    void fluxoCompleto_comProfessorDaTurma_deveCadastrarListarEditarEExcluir() {

        // Arrange
        Cenario c = criarCenario();

        // Act + Assert: cadastrar
        var criacao = chamar(HttpMethod.POST, rota(c.alunoId()), c.tokenProfessor(), new ObservacaoRequest("  Socialização  ", CONTEUDO), Void.class);
        assertThat(criacao.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(criacao.getHeaders().getLocation()).isNotNull();
        UUID observacaoId = idDaLocation(criacao.getHeaders().getLocation().toString());

        // listar
        var lista = chamar(HttpMethod.GET, rota(c.alunoId()), c.tokenProfessor(), null, ObservacaoResponse[].class);
        assertThat(lista.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(lista.getHeaders().getCacheControl()).contains("no-store");
        assertThat(lista.getBody()).isNotNull().hasSize(1);
        assertThat(lista.getBody()[0].titulo()).isEqualTo("Socialização");
        assertThat(lista.getBody()[0].conteudo()).isEqualTo(CONTEUDO);
        assertThat(lista.getBody()[0].autorId()).isEqualTo(c.professorId());
        assertThat(lista.getBody()[0].autorNome()).isEqualTo(NOME_USUARIO);
        assertThat(lista.getBody()[0].editavel()).isTrue();

        // visualizar
        var detalhe = chamar(HttpMethod.GET, rota(c.alunoId()) + "/" + observacaoId, c.tokenProfessor(), null, ObservacaoResponse.class);
        assertThat(detalhe.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(detalhe.getBody()).isNotNull();
        Long versaoAntes = detalhe.getBody().versao();

        // editar: a resposta precisa trazer a versão NOVA
        var edicao = chamar(HttpMethod.PUT, rota(c.alunoId()) + "/" + observacaoId, c.tokenProfessor(), new AtualizarObservacaoRequest("Socialização na roda", "Cantou junto com os colegas.", versaoAntes), ObservacaoResponse.class);
        assertThat(edicao.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(edicao.getBody()).isNotNull();
        assertThat(edicao.getBody().titulo()).isEqualTo("Socialização na roda");
        assertThat(edicao.getBody().conteudo()).isEqualTo("Cantou junto com os colegas.");
        assertThat(edicao.getBody().versao()).isEqualTo(versaoAntes + 1);

        // excluir
        var exclusao = chamar(HttpMethod.DELETE, rota(c.alunoId()) + "/" + observacaoId, c.tokenProfessor(), null, Void.class);
        assertThat(exclusao.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(observacoes.findById(observacaoId)).isEmpty();
    }

    @Test
    void cadastrar_comConteudo_deveGuardarConteudoCifradoNoBancoComOAutor() {

        // Arrange
        Cenario c = criarCenario();

        // Act
        UUID observacaoId = cadastrarObservacao(c);

        // Assert
        var salva = observacoes.findById(observacaoId).orElseThrow();
        assertThat(salva.getConteudo()).isNotBlank().isNotEqualTo(CONTEUDO).doesNotContain("roda");
        assertThat(salva.getAutorId()).isEqualTo(c.professorId());
    }

    @Test
    void atualizar_comVersaoDesatualizada_deveRetornar409() {

        // Arrange
        Cenario c = criarCenario();
        UUID observacaoId = cadastrarObservacao(c);
        chamar(HttpMethod.PUT, rota(c.alunoId()) + "/" + observacaoId, c.tokenProfessor(), new AtualizarObservacaoRequest("Primeira edição", null, 0L), ObservacaoResponse.class);

        // Act: tenta editar de novo usando a versão 0 (já ultrapassada)
        var resposta = chamar(HttpMethod.PUT, rota(c.alunoId()) + "/" + observacaoId, c.tokenProfessor(), new AtualizarObservacaoRequest("Edição atrasada", null, 0L), ErroApi.class);

        // Assert
        verificarErro(resposta, HttpStatus.CONFLICT, "CONFLITO_VERSAO", "Cadastro alterado por outra pessoa. Recarregue os dados.");
        assertThat(observacoes.findById(observacaoId).orElseThrow().getTitulo()).isEqualTo("Primeira edição");
    }

    @Test
    void responsavel_deveLerAsObservacoesDoFilhoSemPoderEditar() {

        // Arrange
        Cenario c = criarCenario();
        cadastrarObservacao(c);

        // Act
        var resposta = chamar(HttpMethod.GET, rota(c.alunoId()), c.tokenResponsavel(), null, ObservacaoResponse[].class);

        // Assert
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).isNotNull().hasSize(1);
        assertThat(resposta.getBody()[0].conteudo()).isEqualTo(CONTEUDO);
        assertThat(resposta.getBody()[0].autorNome()).isEqualTo(NOME_USUARIO);
        assertThat(resposta.getBody()[0].editavel()).isFalse();
    }

    @Test
    void responsavel_naoPodeCadastrarObservacao_deveRetornar403SemPersistir() {

        // Arrange
        Cenario c = criarCenario();

        // Act
        var resposta = chamar(HttpMethod.POST, rota(c.alunoId()), c.tokenResponsavel(), new ObservacaoRequest("Do pai", CONTEUDO), ErroApi.class);

        // Assert
        verificarErro(resposta, HttpStatus.FORBIDDEN, "ACESSO_NEGADO", "Você não possui permissão para esta operação.");
        assertThat(observacoes.findByAlunoIdOrderByDataCriacaoDesc(c.alunoId())).isEmpty();
    }

    @Test
    void direcao_deveLerAsObservacoesDoAluno() {

        // Arrange
        Cenario c = criarCenario();
        cadastrarObservacao(c);

        // Act
        var resposta = chamar(HttpMethod.GET, rota(c.alunoId()), tokenDirecao, null, ObservacaoResponse[].class);

        // Assert
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).isNotNull().hasSize(1);
        assertThat(resposta.getBody()[0].editavel()).isFalse();
    }

    @Test
    void direcao_naoPodeCadastrarObservacao_deveRetornar403() {

        // Arrange
        Cenario c = criarCenario();

        // Act
        var resposta = chamar(HttpMethod.POST, rota(c.alunoId()), tokenDirecao, new ObservacaoRequest("Da direção", CONTEUDO), ErroApi.class);

        // Assert
        verificarErro(resposta, HttpStatus.FORBIDDEN, "ACESSO_NEGADO", "Você não possui permissão para esta operação.");
    }

    @Test
    void professorDeOutraTurma_naoPodeLerNemCadastrar_deveRetornar404() {

        // Arrange
        Cenario c = criarCenario();
        cadastrarObservacao(c);
        Cenario outra = criarCenario();

        // Act: o professor da outra turma tenta acessar o aluno da primeira
        var leitura = chamar(HttpMethod.GET, rota(c.alunoId()), outra.tokenProfessor(), null, ErroApi.class);
        var cadastro = chamar(HttpMethod.POST, rota(c.alunoId()), outra.tokenProfessor(), new ObservacaoRequest("Invasão", "Não deveria gravar."), ErroApi.class);

        // Assert
        verificarErro(leitura, HttpStatus.NOT_FOUND, "ALUNO_NAO_ENCONTRADO", "Aluno não encontrado.");
        verificarErro(cadastro, HttpStatus.NOT_FOUND, "ALUNO_NAO_ENCONTRADO", "Aluno não encontrado.");
        assertThat(observacoes.findByAlunoIdOrderByDataCriacaoDesc(c.alunoId())).hasSize(1);
    }

    @Test
    void colegaDaMesmaTurma_podeLerMasNaoEditarNemExcluir() {

        // Arrange
        Cenario c = criarCenario();
        UUID observacaoId = cadastrarObservacao(c);
        String tokenColega = professorVinculado(c.turmaId()).token();

        // Act
        var leitura = chamar(HttpMethod.GET, rota(c.alunoId()), tokenColega, null, ObservacaoResponse[].class);
        var edicao = chamar(HttpMethod.PUT, rota(c.alunoId()) + "/" + observacaoId, tokenColega, new AtualizarObservacaoRequest("Alterado pelo colega", null, 0L), ErroApi.class);
        var exclusao = chamar(HttpMethod.DELETE, rota(c.alunoId()) + "/" + observacaoId, tokenColega, null, ErroApi.class);

        // Assert
        assertThat(leitura.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(leitura.getBody()).isNotNull().hasSize(1);
        assertThat(leitura.getBody()[0].editavel()).isFalse();

        assertThat(edicao.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(edicao.getBody()).isNotNull();
        assertThat(edicao.getBody().codigo()).isEqualTo("ACESSO_NEGADO");
        assertThat(exclusao.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        var intacta = observacoes.findById(observacaoId).orElseThrow();
        assertThat(intacta.getTitulo()).isEqualTo("Observação de teste");
    }

    @Test
    void responsavelDeOutroAluno_naoPodeLer_deveRetornar404() {

        // Arrange
        Cenario c = criarCenario();
        cadastrarObservacao(c);
        Cenario outra = criarCenario();

        // Act: o pai do outro aluno tenta ler as observações do aluno da primeira turma
        var resposta = chamar(HttpMethod.GET, rota(c.alunoId()), outra.tokenResponsavel(), null, ErroApi.class);

        // Assert
        verificarErro(resposta, HttpStatus.NOT_FOUND, "ALUNO_NAO_ENCONTRADO", "Aluno não encontrado.");
    }

    @Test
    void visualizar_observacaoDeOutroAlunoUsandoOAlunoDaTurmaNaUrl_deveRetornar404() {

        // Arrange: o professor leciona para os dois alunos da mesma turma
        Cenario c = criarCenario();
        UUID outroAluno = matricular(c.turmaId());
        UUID observacaoDoOutro = cadastrarObservacao(c, outroAluno);

        // Act: usa o aluno A na URL, mas o ID da observação do aluno B
        var resposta = chamar(HttpMethod.GET, rota(c.alunoId()) + "/" + observacaoDoOutro, c.tokenProfessor(), null, ErroApi.class);

        // Assert
        verificarErro(resposta, HttpStatus.NOT_FOUND, "OBSERVACAO_NAO_ENCONTRADA", "Observação não encontrada.");
    }

    @Test
    void cadastrar_paraAlunoInexistente_deveRetornar404() {

        // Arrange
        Cenario c = criarCenario();

        // Act
        var resposta = chamar(HttpMethod.POST, rota(UUID.randomUUID()), c.tokenProfessor(), new ObservacaoRequest("Teste", null), ErroApi.class);

        // Assert
        verificarErro(resposta, HttpStatus.NOT_FOUND, "ALUNO_NAO_ENCONTRADO", "Aluno não encontrado.");
    }

    // ---------- helpers ----------

    /** Turma com um aluno matriculado, um professor vinculado e o responsável do aluno (todos autenticados). */
    private record Cenario(UUID turmaId, UUID alunoId, UUID professorId, String tokenProfessor, String tokenResponsavel) {
    }

    private record Professor(UUID id, String token) {
    }

    private Cenario criarCenario() {
        var turma = chamar(HttpMethod.POST, "/api/turmas", tokenDirecao, new CadastrarTurmaRequest("Turma " + UUID.randomUUID().toString().substring(0, 8), "S1"), TurmaResponse.class);
        assertThat(turma.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID turmaId = turma.getBody().id();

        UUID alunoId = matricular(turmaId);
        Professor professor = professorVinculado(turmaId);

        Usuario pai = criarUsuario(Perfil.RESPONSAVEL);
        var vinculo = chamar(HttpMethod.POST, ALUNOS + "/" + alunoId + "/responsaveis", tokenDirecao, new VincularResponsavelRequest(pai.getId()), AlunoResponsavelResponse.class);
        assertThat(vinculo.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        return new Cenario(turmaId, alunoId, professor.id(), professor.token(), autenticar(pai));
    }

    /** Cria um aluno e matricula na turma. */
    private UUID matricular(UUID turmaId) {
        UUID alunoId = criarAluno().id();
        var matricula = chamar(HttpMethod.POST, "/api/turmas/" + turmaId + "/matriculas", tokenDirecao, new MatricularAlunoRequest(alunoId), Void.class);
        assertThat(matricula.getStatusCode().is2xxSuccessful()).isTrue();
        return alunoId;
    }

    /** Cria um professor, vincula à turma e devolve o id e o token dele. */
    private Professor professorVinculado(UUID turmaId) {
        Usuario professor = criarUsuario(Perfil.PROFESSOR);
        var vinculo = chamar(HttpMethod.POST, "/api/turmas/" + turmaId + "/professores", tokenDirecao, new VincularProfessorTurmaRequest(professor.getId()), Void.class);
        assertThat(vinculo.getStatusCode().is2xxSuccessful()).isTrue();
        return new Professor(professor.getId(), autenticar(professor));
    }

    private UUID cadastrarObservacao(Cenario c) {
        return cadastrarObservacao(c, c.alunoId());
    }

    private UUID cadastrarObservacao(Cenario c, UUID alunoId) {
        var criacao = chamar(HttpMethod.POST, rota(alunoId), c.tokenProfessor(), new ObservacaoRequest("Observação de teste", CONTEUDO), Void.class);
        assertThat(criacao.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return idDaLocation(criacao.getHeaders().getLocation().toString());
    }

    private String rota(UUID alunoId) {
        return ALUNOS + "/" + alunoId + "/observacoes";
    }

    private UUID idDaLocation(String location) {
        return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }
}
