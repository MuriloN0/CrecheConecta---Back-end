package com.crecheconecta.controller;

import com.crecheconecta.dto.MeusDadosResponse;
import com.crecheconecta.dto.TermoStatusResponse;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.Sessao;
import com.crecheconecta.repository.AceiteTermoRepository;
import com.crecheconecta.repository.SessaoRepository;
import com.crecheconecta.repository.TermoVersaoRepository;
import com.crecheconecta.service.SessaoService;
import com.crecheconecta.service.TokenSessaoService;
import com.crecheconecta.service.AuditoriaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;

class RevogacaoTermoControllerIntegrationTest extends BaseControllerIntegrationTest {

    private static final String ROTA = "/api/perfil/termos/revogar";

    @Autowired private AceiteTermoRepository aceites;
    @Autowired private SessaoRepository sessoes;
    @Autowired private TermoVersaoRepository termos;
    @Autowired private TokenSessaoService tokens;
    @Autowired private SessaoService sessaoService;
    @Autowired private JdbcTemplate jdbc;
    @MockitoSpyBean private AuditoriaService auditoria;

    @Test
    void revogaHistoricoESessoesEPermiteNovoAceiteDaMesmaVersao() {
        var responsavel = criarUsuario(Perfil.RESPONSAVEL);
        String token = autenticar(responsavel);
        String renovacao = tokens.gerar();

        var outraSessao = new Sessao();
        outraSessao.setUsuario(responsavel);
        outraSessao.setTokenHash(tokens.calcularHash(tokens.gerar()));
        outraSessao.setRenovacaoHash(tokens.calcularHash(renovacao));
        outraSessao.setCriadoEm(Instant.now().minusSeconds(60));
        outraSessao.setAcessoExpiraEm(Instant.now().plusSeconds(600));
        outraSessao.setExpiraEm(Instant.now().plusSeconds(3000));
        sessoes.saveAndFlush(outraSessao);

        int quantidadeAntes = aceites
                .findByUsuarioIdOrderByDataAceiteDesc(responsavel.getId())
                .size();

        var resposta = chamar(
                HttpMethod.POST, ROTA, token, null, Void.class
        );

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(resposta.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(resposta.getHeaders().get("Set-Cookie")).hasSize(2);

        assertThat(aceites.findByUsuarioIdOrderByDataAceiteDesc(responsavel.getId()))
                .hasSize(quantidadeAntes)
                .allSatisfy(aceite ->
                        assertThat(aceite.getRevogadoEm()).isNotNull()
                );

        assertThat(sessaoService.autenticar(token)).isEmpty();
        assertThat(sessaoService.renovar(renovacao)).isEmpty();

        assertThat(chamar(
                HttpMethod.GET, "/api/termo/status", token, null, String.class
        ).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM auditoria WHERE usuario_id = ? AND acao = ?",
                Integer.class,
                responsavel.getId(),
                "TERMO_ACEITE_REVOGADO"
        )).isEqualTo(1);

        jdbc.update(
                "UPDATE acao_verificacao SET criado_em = ? WHERE usuario_id = ? AND finalidade = 'LOGIN'",
                Instant.now().minusSeconds(61).atOffset(java.time.ZoneOffset.UTC),
                responsavel.getId()
        );

        String novoToken = autenticar(responsavel);

        var status = chamar(
                HttpMethod.GET,
                "/api/termo/status",
                novoToken,
                null,
                TermoStatusResponse.class
        );

        assertThat(status.getBody().precisaAceitar()).isTrue();

        assertThat(chamar(
                HttpMethod.GET, "/api/meus-filhos", novoToken, null, String.class
        ).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        var exportacao = chamar(
                HttpMethod.GET,
                "/api/meus-dados",
                novoToken,
                null,
                MeusDadosResponse.class
        );

        assertThat(exportacao.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(exportacao.getBody().aceitesDosTermos())
                .allSatisfy(aceite ->
                        assertThat(aceite.revogadoEm()).isNotNull()
                );

        var aceite = chamar(
                HttpMethod.POST,
                "/api/termo/aceite",
                novoToken,
                new TermoController.AceitarTermoRequest(status.getBody().versao()),
                Void.class
        );

        assertThat(aceite.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(chamar(
                HttpMethod.GET,
                "/api/termo/status",
                novoToken,
                null,
                TermoStatusResponse.class
        ).getBody().precisaAceitar()).isFalse();

        assertThat(aceites.findByUsuarioIdOrderByDataAceiteDesc(responsavel.getId()))
                .hasSize(quantidadeAntes + 1);
    }

    @Test
    void falhaNaAuditoriaReverteRevogacaoESessoes() {
        var responsavel = criarUsuario(Perfil.RESPONSAVEL);
        String token = autenticar(responsavel);

        doThrow(new IllegalStateException("Falha simulada"))
                .when(auditoria)
                .aposCommit("TERMO_ACEITE_REVOGADO", responsavel.getId(), null);

        assertThat(chamar(
                HttpMethod.POST, ROTA, token, null, String.class
        ).getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        assertThat(sessaoService.autenticar(token)).isPresent();

        assertThat(aceites.findByUsuarioIdOrderByDataAceiteDesc(responsavel.getId()))
                .allSatisfy(aceite ->
                        assertThat(aceite.getRevogadoEm()).isNull()
                );
    }

    @Test
    void naoAlteraSessaoOuAceiteDeOutroResponsavel() {
        var primeiro = criarUsuario(Perfil.RESPONSAVEL);
        var segundo = criarUsuario(Perfil.RESPONSAVEL);

        String tokenPrimeiro = autenticar(primeiro);
        String tokenSegundo = autenticar(segundo);

        assertThat(chamar(
                HttpMethod.POST, ROTA, tokenPrimeiro, null, Void.class
        ).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(sessaoService.autenticar(tokenSegundo)).isPresent();

        assertThat(aceites.findByUsuarioIdOrderByDataAceiteDesc(segundo.getId()))
                .allSatisfy(aceite ->
                        assertThat(aceite.getRevogadoEm()).isNull()
                );
    }

    @Test
    void rejeitaAnonimoProfessorEDirecao() {
        assertThat(chamar(
                HttpMethod.POST, ROTA, null, null, String.class
        ).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        for (var perfil : new Perfil[]{Perfil.PROFESSOR, Perfil.DIRECAO}) {
            String token = autenticar(criarUsuario(perfil));

            assertThat(chamar(
                    HttpMethod.POST, ROTA, token, null, String.class
            ).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }
}