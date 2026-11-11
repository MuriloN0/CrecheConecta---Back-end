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

    // O H2 dos testes não roda a migration (que cria a versão inicial): cada teste monta a sua versão vigente.
    @BeforeEach
    void prepararTermos() {
        termos.deleteAll();
        vigente = idUnico("vigente");
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

        // Act
        var resposta = chamar(HttpMethod.GET, ROTA, tokenDirecao, null, TermoVersaoResponse[].class);

        // Assert
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getHeaders().getCacheControl()).contains("no-store");
        assertThat(resposta.getBody()).hasSize(1);
        assertThat(resposta.getBody()[0].identificador()).isEqualTo(vigente);
        assertThat(resposta.getBody()[0].status()).isEqualTo(StatusTermo.VIGENTE);
        assertThat(resposta.getBody()[0].aceites()).isZero();
    }

    @Test
    void criar_deveGerarUmRascunho() {

        // Arrange
        String identificador = idUnico("rascunho");

        // Act
        var resposta = chamar(HttpMethod.POST, ROTA, tokenDirecao, new TermoVersaoRequest(identificador, "Novo termo", "Nova política"), TermoVersaoResponse.class);

        // Assert
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        TermoVersaoResponse corpo = resposta.getBody();
        assertThat(corpo).isNotNull();
        assertThat(corpo.status()).isEqualTo(StatusTermo.RASCUNHO);
        assertThat(corpo.identificador()).isEqualTo(identificador);
        assertThat(corpo.publicadoEm()).isNull();
        assertThat(corpo.versao()).isZero();
    }

    @Test
    void criar_comIdentificadorRepetido_deveRetornar400() {

        // Act
        var resposta = chamar(HttpMethod.POST, ROTA, tokenDirecao, new TermoVersaoRequest(vigente.toUpperCase(), "Termo", "Política"), ErroApi.class);

        // Assert
        verificarErro(resposta, HttpStatus.BAD_REQUEST, "REGRA_NEGOCIO", "Já existe uma versão com este identificador.");
    }

    @Test
    void criar_comDadosInvalidos_deveRetornar400() {

        // Act
        var identificadorComEspaco = chamar(HttpMethod.POST, ROTA, tokenDirecao, new TermoVersaoRequest("com espaco", "Termo", "Política"), ErroApi.class);
        var textoEmBranco = chamar(HttpMethod.POST, ROTA, tokenDirecao, new TermoVersaoRequest(idUnico("ok"), "   ", "Política"), ErroApi.class);

        // Assert
        assertThat(identificadorComEspaco.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(textoEmBranco.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void atualizar_rascunho_deveIncrementarAVersao() {

        // Arrange
        TermoVersaoResponse rascunho = criarRascunho(idUnico("rascunho"));
        String novoIdentificador = idUnico("editado");

        // Act
        var resposta = chamar(HttpMethod.PUT, ROTA + "/" + rascunho.id(), tokenDirecao, new AtualizarTermoVersaoRequest(novoIdentificador, "Termo editado", "Política editada", rascunho.versao()), TermoVersaoResponse.class);

        // Assert
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).isNotNull();
        assertThat(resposta.getBody().identificador()).isEqualTo(novoIdentificador);
        assertThat(resposta.getBody().termoUso()).isEqualTo("Termo editado");
        assertThat(resposta.getBody().versao()).isEqualTo(rascunho.versao() + 1);
    }

    @Test
    void atualizar_comVersaoDesatualizada_deveRetornar409() {

        // Arrange
        TermoVersaoResponse rascunho = criarRascunho(idUnico("rascunho"));

        // Act
        var resposta = chamar(HttpMethod.PUT, ROTA + "/" + rascunho.id(), tokenDirecao, new AtualizarTermoVersaoRequest(rascunho.identificador(), "Termo", "Política", 99L), ErroApi.class);

        // Assert
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resposta.getBody()).isNotNull();
        assertThat(resposta.getBody().codigo()).isEqualTo("CONFLITO_VERSAO");
    }

    @Test
    void atualizar_versaoVigente_deveRetornar400() {

        // Arrange
        TermoVersaoResponse atual = buscarVigente();

        // Act
        var resposta = chamar(HttpMethod.PUT, ROTA + "/" + atual.id(), tokenDirecao, new AtualizarTermoVersaoRequest(atual.identificador(), "Alterado", "Alterado", atual.versao()), ErroApi.class);

        // Assert
        verificarErro(resposta, HttpStatus.BAD_REQUEST, "REGRA_NEGOCIO", "Somente rascunhos podem ser editados.");
    }

    @Test
    void excluir_rascunho_deveRetornar204() {

        // Arrange
        TermoVersaoResponse rascunho = criarRascunho(idUnico("rascunho"));

        // Act
        var exclusao = chamar(HttpMethod.DELETE, ROTA + "/" + rascunho.id(), tokenDirecao, null, Void.class);
        var consulta = chamar(HttpMethod.GET, ROTA + "/" + rascunho.id(), tokenDirecao, null, ErroApi.class);

        // Assert
        assertThat(exclusao.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verificarErro(consulta, HttpStatus.NOT_FOUND, "TERMO_NAO_ENCONTRADO", "Versão dos termos não encontrada.");
    }

    @Test
    void excluir_versaoVigente_deveRetornar400() {

        // Arrange
        TermoVersaoResponse atual = buscarVigente();

        // Act
        var resposta = chamar(HttpMethod.DELETE, ROTA + "/" + atual.id(), tokenDirecao, null, ErroApi.class);

        // Assert
        verificarErro(resposta, HttpStatus.BAD_REQUEST, "REGRA_NEGOCIO", "Somente rascunhos podem ser excluídos.");
    }

    @Test
    void publicar_deveTornarVigenteEArquivarAAnterior() {

        // Arrange
        TermoVersaoResponse rascunho = criarRascunho(idUnico("nova"));

        // Act
        var resposta = chamar(HttpMethod.POST, ROTA + "/" + rascunho.id() + "/publicar", tokenDirecao, new PublicarTermoRequest(rascunho.versao()), TermoVersaoResponse.class);
        var lista = chamar(HttpMethod.GET, ROTA, tokenDirecao, null, TermoVersaoResponse[].class);

        // Assert
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

        // Arrange
        TermoVersaoResponse atual = buscarVigente();

        // Act
        var resposta = chamar(HttpMethod.POST, ROTA + "/" + atual.id() + "/publicar", tokenDirecao, new PublicarTermoRequest(atual.versao()), ErroApi.class);

        // Assert
        verificarErro(resposta, HttpStatus.BAD_REQUEST, "REGRA_NEGOCIO", "Somente rascunhos podem ser publicados.");
    }

    @Test
    void publicar_comVersaoDesatualizada_deveRetornar409() {

        // Arrange
        TermoVersaoResponse rascunho = criarRascunho(idUnico("rascunho"));

        // Act
        var resposta = chamar(HttpMethod.POST, ROTA + "/" + rascunho.id() + "/publicar", tokenDirecao, new PublicarTermoRequest(99L), ErroApi.class);

        // Assert
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resposta.getBody()).isNotNull();
        assertThat(resposta.getBody().codigo()).isEqualTo("CONFLITO_VERSAO");
    }

    @Test
    void novaVersaoPublicada_deveExigirNovoAceiteDeTodos() {

        // Arrange
        String tokenPai = autenticar(criarUsuario(Perfil.RESPONSAVEL));

        // Act + Assert 1: ainda não aceitou nada, vê a versão vigente
        var antes = chamar(HttpMethod.GET, "/api/termo/status", tokenPai, null, TermoStatusResponse.class);
        assertThat(antes.getBody()).isNotNull();
        assertThat(antes.getBody().precisaAceitar()).isTrue();
        assertThat(antes.getBody().versao()).isEqualTo(vigente);
        assertThat(antes.getBody().termoUso()).isEqualTo("Termo vigente");

        // Act + Assert 2: aceita, não precisa mais
        var aceite = chamar(HttpMethod.POST, "/api/termo/aceite", tokenPai, null, Void.class);
        assertThat(aceite.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        var depoisDoAceite = chamar(HttpMethod.GET, "/api/termo/status", tokenPai, null, TermoStatusResponse.class);
        assertThat(depoisDoAceite.getBody()).isNotNull();
        assertThat(depoisDoAceite.getBody().precisaAceitar()).isFalse();

        // Act + Assert 3: a direção conta 1 aceite na versão vigente
        assertThat(buscarVigente().aceites()).isEqualTo(1);

        // Act + Assert 4: a direção publica uma nova versão, e o pai precisa aceitar de novo
        TermoVersaoResponse rascunho = criarRascunho(idUnico("v-nova"));
        var publicada = chamar(HttpMethod.POST, ROTA + "/" + rascunho.id() + "/publicar", tokenDirecao, new PublicarTermoRequest(rascunho.versao()), TermoVersaoResponse.class);
        assertThat(publicada.getStatusCode()).isEqualTo(HttpStatus.OK);

        var depoisDaPublicacao = chamar(HttpMethod.GET, "/api/termo/status", tokenPai, null, TermoStatusResponse.class);
        assertThat(depoisDaPublicacao.getBody()).isNotNull();
        assertThat(depoisDaPublicacao.getBody().precisaAceitar()).isTrue();
        assertThat(depoisDaPublicacao.getBody().versao()).isEqualTo(rascunho.identificador());
        assertThat(depoisDaPublicacao.getBody().termoUso()).isEqualTo("Novo termo");
    }

    @Test
    void acessoSemPermissao_deveSerNegado() {

        // Arrange
        String tokenPai = autenticar(criarUsuario(Perfil.RESPONSAVEL));
        String tokenProfessor = autenticar(criarUsuario(Perfil.PROFESSOR));

        // Act
        var responsavel = chamar(HttpMethod.GET, ROTA, tokenPai, null, ErroApi.class);
        var professor = chamar(HttpMethod.POST, ROTA, tokenProfessor, new TermoVersaoRequest(idUnico("x"), "Termo", "Política"), ErroApi.class);
        var semToken = chamar(HttpMethod.GET, ROTA, null, null, ErroApi.class);

        // Assert
        verificarErro(responsavel, HttpStatus.FORBIDDEN, "ACESSO_NEGADO", "Você não possui permissão para esta operação.");
        verificarErro(professor, HttpStatus.FORBIDDEN, "ACESSO_NEGADO", "Você não possui permissão para esta operação.");
        assertThat(semToken.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ---------- helpers ----------

    private String idUnico(String prefixo) {
        return prefixo + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private TermoVersaoResponse criarRascunho(String identificador) {
        var resposta = chamar(HttpMethod.POST, ROTA, tokenDirecao, new TermoVersaoRequest(identificador, "Novo termo", "Nova política"), TermoVersaoResponse.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resposta.getBody()).isNotNull();
        return resposta.getBody();
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
