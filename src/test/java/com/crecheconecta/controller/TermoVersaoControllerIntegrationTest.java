package com.crecheconecta.controller;

import com.crecheconecta.dto.AtualizarTermoVersaoRequest;
import com.crecheconecta.dto.PublicarTermoRequest;
import com.crecheconecta.dto.TermoStatusResponse;
import com.crecheconecta.dto.TermoVersaoRequest;
import com.crecheconecta.dto.TermoVersaoResponse;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.StatusTermo;
import com.crecheconecta.entity.TermoVersao;
import com.crecheconecta.repository.TermoVersaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TermoVersaoControllerIntegrationTest extends BaseControllerIntegrationTest {

    private static final String ROTA = "/api/termos";

    @Autowired
    private TermoVersaoRepository termos;

    private String vigente;

    @BeforeEach
    void prepararTermos() {
        termos.deleteAll();
        vigente = "vigente-" + UUID.randomUUID().toString().substring(0, 8);

        var termo = new TermoVersao();
        termo.setIdentificador(vigente);
        termo.setTermoUso("Termo vigente");
        termo.setPoliticaPrivacidade("Política vigente");
        termo.setStatus(StatusTermo.VIGENTE);
        termo.setCriadoEm(Instant.now());
        termo.setAtualizadoEm(Instant.now());
        termo.setPublicadoEm(Instant.now());
        termos.saveAndFlush(termo);
    }

    @Test
    void listar_comoDirecao_deveTrazerAVersaoVigente() {
        var resposta = chamar(HttpMethod.GET, ROTA, tokenDirecao, null, TermoVersaoResponse[].class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getHeaders().getCacheControl()).contains("no-store");
        assertThat(resposta.getBody()).hasSize(1);
        assertThat(resposta.getBody()[0].identificador()).isEqualTo(vigente);
        assertThat(resposta.getBody()[0].status()).isEqualTo(StatusTermo.VIGENTE);
        assertThat(resposta.getBody()[0].aceites()).isZero();
    }

    @Test
    void criar_deveGerarUmRascunhoComIdentificadorAutomatico() {
        var rascunho = criarRascunho();

        assertThat(rascunho.status()).isEqualTo(StatusTermo.RASCUNHO);
        assertThat(rascunho.identificador()).matches("v[1-9][0-9]*");
        assertThat(rascunho.publicadoEm()).isNull();
        assertThat(rascunho.versao()).isZero();
    }

    @Test
    void criar_deveAvancarANumeracaoMesmoAposExcluirRascunho() {
        var primeiro = criarRascunho();
        var exclusao = chamar(HttpMethod.DELETE, ROTA + "/" + primeiro.id(), tokenDirecao, null, Void.class);
        assertThat(exclusao.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        var seguinte = criarRascunho();

        assertThat(seguinte.identificador()).matches("v[1-9][0-9]*");
        assertThat(numero(seguinte)).isGreaterThan(numero(primeiro));
    }

    @Test
    void criar_comDadosInvalidos_deveRetornar400() {
        var termoEmBranco = chamar(
                HttpMethod.POST, ROTA, tokenDirecao,
                new TermoVersaoRequest("   ", "Política"), ErroApi.class
        );
        var politicaEmBranco = chamar(
                HttpMethod.POST, ROTA, tokenDirecao,
                new TermoVersaoRequest("Termo", "   "), ErroApi.class
        );

        assertThat(termoEmBranco.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(politicaEmBranco.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void atualizar_rascunho_devePreservarIdentificadorEIncrementarVersaoDeEdicao() {
        var rascunho = criarRascunho();
        var dados = new AtualizarTermoVersaoRequest("Termo editado", "Política editada", rascunho.versao());

        var resposta = chamar(
                HttpMethod.PUT, ROTA + "/" + rascunho.id(), tokenDirecao, dados, TermoVersaoResponse.class
        );

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).isNotNull();
        assertThat(resposta.getBody().identificador()).isEqualTo(rascunho.identificador());
        assertThat(resposta.getBody().termoUso()).isEqualTo("Termo editado");
        assertThat(resposta.getBody().versao()).isEqualTo(rascunho.versao() + 1);
    }

    @Test
    void atualizar_comVersaoDesatualizada_deveRetornar409() {
        var rascunho = criarRascunho();

        var resposta = chamar(
                HttpMethod.PUT, ROTA + "/" + rascunho.id(), tokenDirecao,
                new AtualizarTermoVersaoRequest("Termo", "Política", 99L), ErroApi.class
        );

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resposta.getBody()).isNotNull();
        assertThat(resposta.getBody().codigo()).isEqualTo("CONFLITO_VERSAO");
    }

    @Test
    void atualizar_versaoVigente_deveRetornar400() {
        var atual = buscarVigente();

        var resposta = chamar(
                HttpMethod.PUT, ROTA + "/" + atual.id(), tokenDirecao,
                new AtualizarTermoVersaoRequest("Alterado", "Alterado", atual.versao()), ErroApi.class
        );

        verificarErro(resposta, HttpStatus.BAD_REQUEST, "REGRA_NEGOCIO", "Somente rascunhos podem ser editados.");
    }

    @Test
    void excluir_rascunho_deveRetornar204() {
        var rascunho = criarRascunho();

        var exclusao = chamar(HttpMethod.DELETE, ROTA + "/" + rascunho.id(), tokenDirecao, null, Void.class);
        var consulta = chamar(HttpMethod.GET, ROTA + "/" + rascunho.id(), tokenDirecao, null, ErroApi.class);

        assertThat(exclusao.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verificarErro(consulta, HttpStatus.NOT_FOUND, "TERMO_NAO_ENCONTRADO", "Versão dos termos não encontrada.");
    }

    @Test
    void excluir_versaoVigente_deveRetornar400() {
        var atual = buscarVigente();
        var resposta = chamar(HttpMethod.DELETE, ROTA + "/" + atual.id(), tokenDirecao, null, ErroApi.class);

        verificarErro(resposta, HttpStatus.BAD_REQUEST, "REGRA_NEGOCIO", "Somente rascunhos podem ser excluídos.");
    }

    @Test
    void publicar_deveTornarVigenteEArquivarAAnterior() {
        var rascunho = criarRascunho();
        var resposta = chamar(
                HttpMethod.POST, ROTA + "/" + rascunho.id() + "/publicar", tokenDirecao,
                new PublicarTermoRequest(rascunho.versao()), TermoVersaoResponse.class
        );
        var lista = chamar(HttpMethod.GET, ROTA, tokenDirecao, null, TermoVersaoResponse[].class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).isNotNull();
        assertThat(resposta.getBody().status()).isEqualTo(StatusTermo.VIGENTE);
        assertThat(resposta.getBody().publicadoEm()).isNotNull();
        assertThat(lista.getBody()).hasSize(2);
        assertThat(lista.getBody()).filteredOn(t -> t.status() == StatusTermo.VIGENTE).hasSize(1)
                .extracting(TermoVersaoResponse::identificador).containsExactly(rascunho.identificador());
        assertThat(lista.getBody()).filteredOn(t -> t.status() == StatusTermo.ARQUIVADO).hasSize(1)
                .extracting(TermoVersaoResponse::identificador).containsExactly(vigente);
    }

    @Test
    void publicar_versaoQueJaFoiPublicada_deveRetornar400() {
        var atual = buscarVigente();
        var resposta = chamar(
                HttpMethod.POST, ROTA + "/" + atual.id() + "/publicar", tokenDirecao,
                new PublicarTermoRequest(atual.versao()), ErroApi.class
        );

        verificarErro(resposta, HttpStatus.BAD_REQUEST, "REGRA_NEGOCIO", "Somente rascunhos podem ser publicados.");
    }

    @Test
    void publicar_comVersaoDesatualizada_deveRetornar409() {
        var rascunho = criarRascunho();
        var resposta = chamar(
                HttpMethod.POST, ROTA + "/" + rascunho.id() + "/publicar", tokenDirecao,
                new PublicarTermoRequest(99L), ErroApi.class
        );

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resposta.getBody()).isNotNull();
        assertThat(resposta.getBody().codigo()).isEqualTo("CONFLITO_VERSAO");
    }

    @Test
    void novaVersaoPublicada_deveExigirNovoAceiteEPreservarOAceiteAnterior() {
        String tokenPai = autenticar(criarResponsavelSemAceite());

        var antes = chamar(HttpMethod.GET, "/api/termo/status", tokenPai, null, TermoStatusResponse.class);
        assertThat(antes.getBody()).isNotNull();
        assertThat(antes.getBody().precisaAceitar()).isTrue();
        assertThat(antes.getBody().versao()).isEqualTo(vigente);
        assertThat(antes.getBody().termoUso()).isEqualTo("Termo vigente");

        var aceite = chamar(
                HttpMethod.POST, "/api/termo/aceite", tokenPai,
                new TermoController.AceitarTermoRequest(vigente), Void.class
        );
        assertThat(aceite.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        var depoisDoAceite = chamar(HttpMethod.GET, "/api/termo/status", tokenPai, null, TermoStatusResponse.class);
        assertThat(depoisDoAceite.getBody()).isNotNull();
        assertThat(depoisDoAceite.getBody().precisaAceitar()).isFalse();
        var anterior = buscarVigente();
        assertThat(anterior.aceites()).isEqualTo(1);

        var rascunho = criarRascunho();
        var publicada = chamar(
                HttpMethod.POST, ROTA + "/" + rascunho.id() + "/publicar", tokenDirecao,
                new PublicarTermoRequest(rascunho.versao()), TermoVersaoResponse.class
        );
        assertThat(publicada.getStatusCode()).isEqualTo(HttpStatus.OK);

        var depoisDaPublicacao = chamar(HttpMethod.GET, "/api/termo/status", tokenPai, null, TermoStatusResponse.class);
        assertThat(depoisDaPublicacao.getBody()).isNotNull();
        assertThat(depoisDaPublicacao.getBody().precisaAceitar()).isTrue();
        assertThat(depoisDaPublicacao.getBody().versao()).isEqualTo(rascunho.identificador());
        assertThat(depoisDaPublicacao.getBody().termoUso()).isEqualTo("Novo termo");

        var arquivada = chamar(HttpMethod.GET, ROTA + "/" + anterior.id(), tokenDirecao, null, TermoVersaoResponse.class);
        assertThat(arquivada.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(arquivada.getBody()).isNotNull();
        assertThat(arquivada.getBody().identificador()).isEqualTo(vigente);
        assertThat(arquivada.getBody().aceites()).isEqualTo(1);
    }

    @Test
    void acessoSemPermissao_deveSerNegado() {
        String tokenPai = autenticar(criarUsuario(Perfil.RESPONSAVEL));
        String tokenProfessor = autenticar(criarUsuario(Perfil.PROFESSOR));

        var responsavel = chamar(HttpMethod.GET, ROTA, tokenPai, null, ErroApi.class);
        var professor = chamar(
                HttpMethod.POST, ROTA, tokenProfessor, new TermoVersaoRequest("Termo", "Política"), ErroApi.class
        );
        var semToken = chamar(HttpMethod.GET, ROTA, null, null, ErroApi.class);

        verificarErro(responsavel, HttpStatus.FORBIDDEN, "ACESSO_NEGADO", "Você não possui permissão para esta operação.");
        verificarErro(professor, HttpStatus.FORBIDDEN, "ACESSO_NEGADO", "Você não possui permissão para esta operação.");
        assertThat(semToken.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private TermoVersaoResponse criarRascunho() {
        var resposta = chamar(
                HttpMethod.POST, ROTA, tokenDirecao,
                new TermoVersaoRequest("Novo termo", "Nova política"), TermoVersaoResponse.class
        );
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resposta.getBody()).isNotNull();
        return resposta.getBody();
    }

    private long numero(TermoVersaoResponse termo) {
        return Long.parseLong(termo.identificador().substring(1));
    }

    private TermoVersaoResponse buscarVigente() {
        var lista = chamar(HttpMethod.GET, ROTA, tokenDirecao, null, TermoVersaoResponse[].class);
        assertThat(lista.getBody()).isNotNull();

        for (TermoVersaoResponse termo : lista.getBody()) {
            if (termo.status() == StatusTermo.VIGENTE) {
                return termo;
            }
        }

        throw new AssertionError("Nenhuma versão vigente na lista.");
    }
}