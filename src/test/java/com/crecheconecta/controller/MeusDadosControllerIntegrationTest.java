package com.crecheconecta.controller;

import com.crecheconecta.dto.AlunoResponsavelResponse;
import com.crecheconecta.dto.InformacaoRelevanteRequest;
import com.crecheconecta.dto.MeusDadosResponse;
import com.crecheconecta.dto.VincularResponsavelRequest;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.StatusTermo;
import com.crecheconecta.entity.TermoVersao;
import com.crecheconecta.entity.Usuario;
import com.crecheconecta.repository.TermoVersaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** LGPD: o responsável baixa uma cópia dos dados dele e das crianças vinculadas. */
class MeusDadosControllerIntegrationTest extends BaseControllerIntegrationTest {

    private static final String ROTA = "/api/meus-dados";

    @Autowired
    private TermoVersaoRepository termos;

    // O H2 dos testes não roda a migration que cria a versão inicial: cada teste monta a sua vigente.
    @BeforeEach
    void prepararTermoVigente() {
        termos.deleteAll();
        var termo = new TermoVersao();
        termo.setIdentificador("vigente-" + UUID.randomUUID().toString().substring(0, 8));
        termo.setTermoUso("Termo vigente");
        termo.setPoliticaPrivacidade("Política vigente");
        termo.setStatus(StatusTermo.VIGENTE);
        termo.setCriadoEm(Instant.now());
        termo.setAtualizadoEm(Instant.now());
        termo.setPublicadoEm(Instant.now());
        termos.saveAndFlush(termo);
    }

    @Test
    void exportar_comoResponsavel_deveTrazerDadosDoTitularEDoFilhoDecifrados() {

        // Arrange
        Usuario pai = criarUsuario(Perfil.RESPONSAVEL);
        UUID alunoId = vincularNovoAluno(pai);
        String tokenPai = autenticar(pai);
        var informacao = chamar(HttpMethod.POST, ALUNOS + "/" + alunoId + "/informacoes-relevantes", tokenPai, new InformacaoRelevanteRequest("Alergia", "Lactose"), Void.class);
        assertThat(informacao.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Act
        var resposta = chamar(HttpMethod.GET, ROTA, tokenPai, null, MeusDadosResponse.class);

        // Assert
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getHeaders().getCacheControl()).contains("no-store");
        MeusDadosResponse dados = resposta.getBody();
        assertThat(dados).isNotNull();
        assertThat(dados.geradoEm()).isNotNull();
        assertThat(dados.titular().email()).isEqualTo(pai.getEmail());
        assertThat(dados.aceitesDosTermos()).hasSize(1);
        assertThat(dados.filhos()).hasSize(1);
        var filho = dados.filhos().get(0);
        assertThat(filho.dataNascimento()).isEqualTo(NASCIMENTO);
        assertThat(filho.informacoesRelevantes()).hasSize(1);
        assertThat(filho.informacoesRelevantes().get(0).nome()).isEqualTo("Alergia");
        assertThat(filho.informacoesRelevantes().get(0).observacoes()).isEqualTo("Lactose");
    }

    @Test
    void exportar_semAceiteDaVersaoVigente_deveContinuarPermitido() {

        // Arrange: o acesso aos próprios dados é direito do titular, mesmo com termo pendente
        Usuario pai = criarResponsavelSemAceite();
        vincularNovoAluno(pai);
        String tokenPai = autenticar(pai);

        // Act
        var dados = chamar(HttpMethod.GET, ROTA, tokenPai, null, MeusDadosResponse.class);
        var filhos = chamar(HttpMethod.GET, "/api/meus-filhos", tokenPai, null, ErroApi.class);

        // Assert
        assertThat(dados.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dados.getBody()).isNotNull();
        assertThat(dados.getBody().aceitesDosTermos()).isEmpty();
        assertThat(filhos.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void exportar_comOutroPerfilOuSemLogin_deveSerNegado() {

        // Arrange
        String tokenProfessor = autenticar(criarUsuario(Perfil.PROFESSOR));

        // Act
        var professor = chamar(HttpMethod.GET, ROTA, tokenProfessor, null, ErroApi.class);
        var direcao = chamar(HttpMethod.GET, ROTA, tokenDirecao, null, ErroApi.class);
        var semLogin = chamar(HttpMethod.GET, ROTA, null, null, ErroApi.class);

        // Assert
        verificarErro(professor, HttpStatus.FORBIDDEN, "ACESSO_NEGADO", "Você não possui permissão para esta operação.");
        verificarErro(direcao, HttpStatus.FORBIDDEN, "ACESSO_NEGADO", "Você não possui permissão para esta operação.");
        assertThat(semLogin.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ---------- helpers ----------

    private UUID vincularNovoAluno(Usuario pai) {
        UUID alunoId = criarAluno().id();
        var vinculo = chamar(HttpMethod.POST, ALUNOS + "/" + alunoId + "/responsaveis", tokenDirecao, new VincularResponsavelRequest(pai.getId()), AlunoResponsavelResponse.class);
        assertThat(vinculo.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return alunoId;
    }
}
