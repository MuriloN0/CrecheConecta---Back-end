package com.crecheconecta.controller;

import com.crecheconecta.dto.AlunoResponsavelResponse;
import com.crecheconecta.dto.AtualizarObservacaoRequest;
import com.crecheconecta.dto.ObservacaoRequest;
import com.crecheconecta.dto.ObservacaoResponse;
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

class ObservacaoControllerIntegrationTest extends BaseControllerIntegrationTest {

    private static final String CONTEUDO = "Tem alergia a lactose e chora quando ouve barulho alto.";

    @Autowired
    private ObservacaoRepository observacoes;

    @Test
    void fluxoCompleto_comResponsavelDoAluno_deveCadastrarListarEditarEExcluir() {

        // Arrange
        Familia familia = criarFamilia();

        // Act + Assert: cadastrar
        var criacao = chamar(HttpMethod.POST, rota(familia.alunoId()), familia.token(), new ObservacaoRequest("  Alergia  ", CONTEUDO), Void.class);
        assertThat(criacao.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(criacao.getHeaders().getLocation()).isNotNull();
        UUID observacaoId = idDaLocation(criacao.getHeaders().getLocation().toString());

        // listar
        var lista = chamar(HttpMethod.GET, rota(familia.alunoId()), familia.token(), null, ObservacaoResponse[].class);
        assertThat(lista.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(lista.getHeaders().getCacheControl()).contains("no-store");
        assertThat(lista.getBody()).isNotNull().hasSize(1);
        assertThat(lista.getBody()[0].titulo()).isEqualTo("Alergia");
        assertThat(lista.getBody()[0].conteudo()).isEqualTo(CONTEUDO);

        // visualizar
        var detalhe = chamar(HttpMethod.GET, rota(familia.alunoId()) + "/" + observacaoId, familia.token(), null, ObservacaoResponse.class);
        assertThat(detalhe.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(detalhe.getBody()).isNotNull();
        Long versaoAntes = detalhe.getBody().versao();

        // editar: a resposta precisa trazer a versão NOVA
        var edicao = chamar(HttpMethod.PUT, rota(familia.alunoId()) + "/" + observacaoId, familia.token(), new AtualizarObservacaoRequest("Alergia alimentar", "Lactose e glúten.", versaoAntes), ObservacaoResponse.class);
        assertThat(edicao.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(edicao.getBody()).isNotNull();
        assertThat(edicao.getBody().titulo()).isEqualTo("Alergia alimentar");
        assertThat(edicao.getBody().conteudo()).isEqualTo("Lactose e glúten.");
        assertThat(edicao.getBody().versao()).isEqualTo(versaoAntes + 1);

        // excluir
        var exclusao = chamar(HttpMethod.DELETE, rota(familia.alunoId()) + "/" + observacaoId, familia.token(), null, Void.class);
        assertThat(exclusao.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(observacoes.findById(observacaoId)).isEmpty();
    }

    @Test
    void cadastrar_comConteudo_deveGuardarConteudoCifradoNoBanco() {

        // Arrange
        Familia familia = criarFamilia();

        // Act
        var criacao = chamar(HttpMethod.POST, rota(familia.alunoId()), familia.token(), new ObservacaoRequest("Saúde", CONTEUDO), Void.class);

        // Assert
        assertThat(criacao.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID observacaoId = idDaLocation(criacao.getHeaders().getLocation().toString());

        var salva = observacoes.findById(observacaoId).orElseThrow();
        assertThat(salva.getConteudo()).isNotBlank().isNotEqualTo(CONTEUDO).doesNotContain("lactose");
    }

    @Test
    void atualizar_comVersaoDesatualizada_deveRetornar409() {

        // Arrange
        Familia familia = criarFamilia();
        UUID observacaoId = cadastrarObservacao(familia);
        chamar(HttpMethod.PUT, rota(familia.alunoId()) + "/" + observacaoId, familia.token(), new AtualizarObservacaoRequest("Primeira edição", null, 0L), ObservacaoResponse.class);

        // Act: tenta editar de novo usando a versão 0 (já ultrapassada)
        var resposta = chamar(HttpMethod.PUT, rota(familia.alunoId()) + "/" + observacaoId, familia.token(), new AtualizarObservacaoRequest("Edição atrasada", null, 0L), ErroApi.class);

        // Assert
        verificarErro(resposta, HttpStatus.CONFLICT, "CONFLITO_VERSAO", "Cadastro alterado por outra pessoa. Recarregue os dados.");
        assertThat(observacoes.findById(observacaoId).orElseThrow().getTitulo()).isEqualTo("Primeira edição");
    }

    @Test
    void listar_comFilhoDeOutroResponsavel_deveRetornar404() {

        // Arrange
        Familia familiaA = criarFamilia();
        Familia familiaB = criarFamilia();
        cadastrarObservacao(familiaB);

        // Act: pai A tenta ler as observações do filho do pai B
        var resposta = chamar(HttpMethod.GET, rota(familiaB.alunoId()), familiaA.token(), null, ErroApi.class);

        // Assert
        verificarErro(resposta, HttpStatus.NOT_FOUND, "ALUNO_NAO_ENCONTRADO", "Aluno não encontrado.");
    }

    @Test
    void cadastrar_paraFilhoDeOutroResponsavel_deveRetornar404SemPersistir() {

        // Arrange
        Familia familiaA = criarFamilia();
        Familia familiaB = criarFamilia();

        // Act
        var resposta = chamar(HttpMethod.POST, rota(familiaB.alunoId()), familiaA.token(), new ObservacaoRequest("Invasão", "Não deveria gravar."), ErroApi.class);

        // Assert
        verificarErro(resposta, HttpStatus.NOT_FOUND, "ALUNO_NAO_ENCONTRADO", "Aluno não encontrado.");
        assertThat(observacoes.findByAlunoId(familiaB.alunoId())).isEmpty();
    }

    @Test
    void editarEExcluir_observacaoDoFilhoDeOutroResponsavel_devemRetornar404SemAlterar() {

        // Arrange
        Familia familiaA = criarFamilia();
        Familia familiaB = criarFamilia();
        UUID observacaoDoB = cadastrarObservacao(familiaB);

        // Act
        var edicao = chamar(HttpMethod.PUT, rota(familiaB.alunoId()) + "/" + observacaoDoB, familiaA.token(), new AtualizarObservacaoRequest("Alterado pelo A", null, 0L), ErroApi.class);
        var exclusao = chamar(HttpMethod.DELETE, rota(familiaB.alunoId()) + "/" + observacaoDoB, familiaA.token(), null, ErroApi.class);

        // Assert
        verificarErro(edicao, HttpStatus.NOT_FOUND, "ALUNO_NAO_ENCONTRADO", "Aluno não encontrado.");
        verificarErro(exclusao, HttpStatus.NOT_FOUND, "ALUNO_NAO_ENCONTRADO", "Aluno não encontrado.");

        var intacta = observacoes.findById(observacaoDoB).orElseThrow();
        assertThat(intacta.getTitulo()).isEqualTo("Observação de teste");
    }

    @Test
    void visualizar_observacaoDeOutroAlunoUsandoOProprioFilhoNaUrl_deveRetornar404() {

        // Arrange
        Familia familiaA = criarFamilia();
        Familia familiaB = criarFamilia();
        UUID observacaoDoB = cadastrarObservacao(familiaB);

        // Act: pai A usa o ID do PRÓPRIO filho na URL, mas o ID da observação do filho do B
        var resposta = chamar(HttpMethod.GET, rota(familiaA.alunoId()) + "/" + observacaoDoB, familiaA.token(), null, ErroApi.class);

        // Assert
        verificarErro(resposta, HttpStatus.NOT_FOUND, "OBSERVACAO_NAO_ENCONTRADA", "Observação não encontrada.");
    }

    @Test
    void cadastrar_paraAlunoInexistente_deveRetornar404() {

        // Arrange
        Familia familia = criarFamilia();

        // Act
        var resposta = chamar(HttpMethod.POST, rota(UUID.randomUUID()), familia.token(), new ObservacaoRequest("Teste", null), ErroApi.class);

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

    private UUID cadastrarObservacao(Familia familia) {
        var criacao = chamar(HttpMethod.POST, rota(familia.alunoId()), familia.token(), new ObservacaoRequest("Observação de teste", CONTEUDO), Void.class);
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
