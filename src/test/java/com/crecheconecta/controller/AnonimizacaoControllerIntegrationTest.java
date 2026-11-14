package com.crecheconecta.controller;

import com.crecheconecta.dto.AlunoResponse;
import com.crecheconecta.dto.AlunoResponsavelResponse;
import com.crecheconecta.dto.AnonimizacaoResponse;
import com.crecheconecta.dto.CadastrarTurmaRequest;
import com.crecheconecta.dto.DiarioRegistroResponse;
import com.crecheconecta.dto.InativarAlunoRequest;
import com.crecheconecta.dto.InformacaoRelevanteRequest;
import com.crecheconecta.dto.MatricularAlunoRequest;
import com.crecheconecta.dto.ObservacaoRequest;
import com.crecheconecta.dto.RegistrarDiarioRequest;
import com.crecheconecta.dto.TurmaResponse;
import com.crecheconecta.dto.VincularProfessorTurmaRequest;
import com.crecheconecta.dto.VincularResponsavelRequest;
import com.crecheconecta.entity.Alimentacao;
import com.crecheconecta.entity.Higiene;
import com.crecheconecta.entity.Humor;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.SituacaoCadastro;
import com.crecheconecta.entity.Sono;
import com.crecheconecta.entity.Usuario;
import com.crecheconecta.repository.AlunoRepository;
import com.crecheconecta.repository.AlunoResponsavelRepository;
import com.crecheconecta.repository.DiarioRotinaRepository;
import com.crecheconecta.repository.InformacaoRelevanteRepository;
import com.crecheconecta.repository.ObservacaoRepository;
import com.crecheconecta.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LGPD: a direção anonimiza os dados de um aluno inativo e do responsável dele.
 * Apaga dado de saúde e textos livres; troca nome, data de nascimento e contato; mantém o histórico sem identificação.
 */
class AnonimizacaoControllerIntegrationTest extends BaseControllerIntegrationTest {

    @Autowired
    private AlunoRepository alunos;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private InformacaoRelevanteRepository informacoes;

    @Autowired
    private ObservacaoRepository observacoes;

    @Autowired
    private DiarioRotinaRepository diarios;

    @Autowired
    private AlunoResponsavelRepository vinculos;

    @Test
    void anonimizar_alunoInativo_deveApagarDadosSensiveisEAnonimizarAlunoEResponsavel() {

        // Arrange: aluno com informação relevante (do pai), observação e diário (do professor)
        Cenario c = criarCenario();
        var informacao = chamar(HttpMethod.POST, ALUNOS + "/" + c.alunoId() + "/informacoes-relevantes", c.tokenPai(), new InformacaoRelevanteRequest("Alergia", "Lactose"), Void.class);
        assertThat(informacao.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var observacao = chamar(HttpMethod.POST, ALUNOS + "/" + c.alunoId() + "/observacoes", c.tokenProfessor(), new ObservacaoRequest("Adaptação", "Brincou com os colegas."), Void.class);
        assertThat(observacao.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var diario = chamar(HttpMethod.PUT, "/api/turmas/" + c.turmaId() + "/diarios/" + hoje() + "/alunos/" + c.alunoId(), c.tokenProfessor(),
                new RegistrarDiarioRequest(Alimentacao.COMEU_TUDO, Sono.DORMIU_BEM, true, Humor.FELIZ, Higiene.HIGIENIZADO, "Chamou a mãe pelo nome.", null), DiarioRegistroResponse.class);
        assertThat(diario.getStatusCode()).isEqualTo(HttpStatus.OK);
        inativar(c.alunoId());

        // Act
        var resposta = chamar(HttpMethod.POST, rota(c.alunoId()), tokenDirecao, null, AnonimizacaoResponse.class);

        // Assert: resumo
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).isNotNull();
        assertThat(resposta.getBody().responsaveisAnonimizados()).isEqualTo(1);
        assertThat(resposta.getBody().informacoesRelevantesRemovidas()).isEqualTo(1);
        assertThat(resposta.getBody().observacoesRemovidas()).isEqualTo(1);

        // aluno: nome genérico, só o ano de nascimento
        var aluno = alunos.findById(c.alunoId()).orElseThrow();
        assertThat(aluno.getNome()).isEqualTo("Aluno anonimizado");
        assertThat(aluno.getDataNascimento()).isNull();
        assertThat(aluno.getAnoNascimento()).isEqualTo(NASCIMENTO.getYear());
        assertThat(aluno.getAnonimizadoEm()).isNotNull();

        // dados sensíveis e textos livres apagados; as opções do diário continuam
        assertThat(informacoes.findByAlunoId(c.alunoId())).isEmpty();
        assertThat(observacoes.findByAlunoIdOrderByDataCriacaoDesc(c.alunoId())).isEmpty();
        var diarioSalvo = diarios.findByAlunoIdAndData(c.alunoId(), hoje()).orElseThrow();
        assertThat(diarioSalvo.getObservacaoProfessor()).isNull();
        assertThat(diarioSalvo.getAlimentacao()).isEqualTo(Alimentacao.COMEU_TUDO);

        // responsável: sem nome, e-mail e telefone reais, conta inativa e sessão revogada
        Usuario pai = usuarios.findById(c.paiId()).orElseThrow();
        assertThat(pai.getNome()).isEqualTo("Responsável anonimizado");
        assertThat(pai.getEmail()).startsWith("anonimizado-").doesNotContain("example.test");
        assertThat(pai.getTelefone()).isNull();
        assertThat(pai.isAtivo()).isFalse();
        assertThat(pai.getSituacaoCadastro()).isEqualTo(SituacaoCadastro.INATIVO);
        assertThat(vinculos.existsByAlunoIdAndEncerradoEmIsNull(c.alunoId())).isFalse();

        var acessoDoPai = chamar(HttpMethod.GET, "/api/meus-filhos", c.tokenPai(), null, ErroApi.class);
        assertThat(acessoDoPai.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // a direção vê o cadastro marcado como anonimizado
        var consulta = chamar(HttpMethod.GET, ALUNOS + "/" + c.alunoId(), tokenDirecao, null, AlunoResponse.class);
        assertThat(consulta.getBody()).isNotNull();
        assertThat(consulta.getBody().anonimizado()).isTrue();
    }

    @Test
    void anonimizar_alunoAtivo_deveRetornar400SemAlterar() {

        // Arrange
        Cenario c = criarCenario();

        // Act
        var resposta = chamar(HttpMethod.POST, rota(c.alunoId()), tokenDirecao, null, ErroApi.class);

        // Assert
        verificarErro(resposta, HttpStatus.BAD_REQUEST, "REGRA_NEGOCIO", "Inative o aluno antes de anonimizar os dados.");
        assertThat(alunos.findById(c.alunoId()).orElseThrow().getAnonimizadoEm()).isNull();
        assertThat(usuarios.findById(c.paiId()).orElseThrow().isAtivo()).isTrue();
    }

    @Test
    void anonimizar_duasVezes_deveRetornar400() {

        // Arrange
        Cenario c = criarCenario();
        inativar(c.alunoId());
        var primeira = chamar(HttpMethod.POST, rota(c.alunoId()), tokenDirecao, null, AnonimizacaoResponse.class);
        assertThat(primeira.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Act
        var segunda = chamar(HttpMethod.POST, rota(c.alunoId()), tokenDirecao, null, ErroApi.class);

        // Assert
        verificarErro(segunda, HttpStatus.BAD_REQUEST, "REGRA_NEGOCIO", "Os dados deste aluno já foram anonimizados.");
    }

    @Test
    void anonimizar_comoProfessorOuResponsavel_deveRetornar403() {

        // Arrange
        Cenario c = criarCenario();
        inativar(c.alunoId());

        // Act
        var professor = chamar(HttpMethod.POST, rota(c.alunoId()), c.tokenProfessor(), null, ErroApi.class);

        // Assert
        verificarErro(professor, HttpStatus.FORBIDDEN, "ACESSO_NEGADO", "Você não possui permissão para esta operação.");
        assertThat(alunos.findById(c.alunoId()).orElseThrow().getAnonimizadoEm()).isNull();
    }

    @Test
    void anonimizar_alunoInexistente_deveRetornar404() {

        // Act
        var resposta = chamar(HttpMethod.POST, rota(UUID.randomUUID()), tokenDirecao, null, ErroApi.class);

        // Assert
        verificarErro(resposta, HttpStatus.NOT_FOUND, "ALUNO_NAO_ENCONTRADO", "Aluno não encontrado.");
    }

    // ---------- helpers ----------

    /** Turma com um aluno matriculado, um professor vinculado e o responsável do aluno (todos autenticados). */
    private record Cenario(UUID turmaId, UUID alunoId, String tokenProfessor, UUID paiId, String tokenPai) {
    }

    private Cenario criarCenario() {
        var turma = chamar(HttpMethod.POST, "/api/turmas", tokenDirecao, new CadastrarTurmaRequest("Turma " + UUID.randomUUID().toString().substring(0, 8), "S1"), TurmaResponse.class);
        assertThat(turma.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID turmaId = turma.getBody().id();

        UUID alunoId = criarAluno().id();
        var matricula = chamar(HttpMethod.POST, "/api/turmas/" + turmaId + "/matriculas", tokenDirecao, new MatricularAlunoRequest(alunoId), Void.class);
        assertThat(matricula.getStatusCode().is2xxSuccessful()).isTrue();

        Usuario professor = criarUsuario(Perfil.PROFESSOR);
        var vinculoProfessor = chamar(HttpMethod.POST, "/api/turmas/" + turmaId + "/professores", tokenDirecao, new VincularProfessorTurmaRequest(professor.getId()), Void.class);
        assertThat(vinculoProfessor.getStatusCode().is2xxSuccessful()).isTrue();

        Usuario pai = criarUsuario(Perfil.RESPONSAVEL);
        var vinculoPai = chamar(HttpMethod.POST, ALUNOS + "/" + alunoId + "/responsaveis", tokenDirecao, new VincularResponsavelRequest(pai.getId()), AlunoResponsavelResponse.class);
        assertThat(vinculoPai.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        return new Cenario(turmaId, alunoId, autenticar(professor), pai.getId(), autenticar(pai));
    }

    private void inativar(UUID alunoId) {
        var atual = chamar(HttpMethod.GET, ALUNOS + "/" + alunoId, tokenDirecao, null, AlunoResponse.class);
        assertThat(atual.getBody()).isNotNull();
        var inativacao = chamar(HttpMethod.POST, ALUNOS + "/" + alunoId + "/inativar", tokenDirecao, new InativarAlunoRequest(atual.getBody().versao()), AlunoResponse.class);
        assertThat(inativacao.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private String rota(UUID alunoId) {
        return ALUNOS + "/" + alunoId + "/anonimizar";
    }

    // Mesmo fuso que o diário usa para decidir o que é "hoje".
    private LocalDate hoje() {
        return LocalDate.now(ZoneId.of("America/Sao_Paulo"));
    }
}
