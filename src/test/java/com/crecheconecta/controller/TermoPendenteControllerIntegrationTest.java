package com.crecheconecta.controller;

import com.crecheconecta.dto.AlunoResponsavelResponse;
import com.crecheconecta.dto.PublicarTermoRequest;
import com.crecheconecta.dto.TermoStatusResponse;
import com.crecheconecta.dto.TermoVersaoRequest;
import com.crecheconecta.dto.TermoVersaoResponse;
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

/**
 * LGPD: sem aceitar a versão vigente dos termos, o responsável não acessa os dados da criança
 * (403 TERMO_PENDENTE). As rotas do próprio termo continuam liberadas para ele poder aceitar.
 */
class TermoPendenteControllerIntegrationTest extends BaseControllerIntegrationTest {

    private static final String MENSAGEM = "Aceite a versão vigente dos Termos de Uso e da Política de Privacidade para continuar.";

    @Autowired
    private TermoVersaoRepository termos;

    // O H2 dos testes não roda a migration que cria a versão inicial: cada teste monta a sua vigente.
    @BeforeEach
    void prepararTermoVigente() {
        termos.deleteAll();
        var termo = new TermoVersao();
        termo.setIdentificador(idUnico("vigente"));
        termo.setTermoUso("Termo vigente");
        termo.setPoliticaPrivacidade("Política vigente");
        termo.setStatus(StatusTermo.VIGENTE);
        termo.setCriadoEm(Instant.now());
        termo.setAtualizadoEm(Instant.now());
        termo.setPublicadoEm(Instant.now());
        termos.saveAndFlush(termo);
    }

    @Test
    void responsavelQueAceitouAVigente_deveAcessarOsDadosDoFilho() {

        // Arrange
        Familia familia = criarFamilia(criarUsuario(Perfil.RESPONSAVEL));

        // Act
        var lista = chamar(HttpMethod.GET, "/api/meus-filhos", familia.token(), null, Object[].class);
        var filho = chamar(HttpMethod.GET, "/api/meus-filhos/" + familia.alunoId(), familia.token(), null, Object.class);

        // Assert
        assertThat(lista.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(filho.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void responsavelSemAceite_deveReceber403TermoPendenteNasRotasDosPais() {

        // Arrange
        Familia familia = criarFamilia(criarResponsavelSemAceite());

        // Act
        var lista = chamar(HttpMethod.GET, "/api/meus-filhos", familia.token(), null, ErroApi.class);
        var filho = chamar(HttpMethod.GET, "/api/meus-filhos/" + familia.alunoId(), familia.token(), null, ErroApi.class);
        var fichas = chamar(HttpMethod.GET, ALUNOS + "/" + familia.alunoId() + "/fichas-saude", familia.token(), null, ErroApi.class);

        // Assert
        verificarErro(lista, HttpStatus.FORBIDDEN, "TERMO_PENDENTE", MENSAGEM);
        verificarErro(filho, HttpStatus.FORBIDDEN, "TERMO_PENDENTE", MENSAGEM);
        verificarErro(fichas, HttpStatus.FORBIDDEN, "TERMO_PENDENTE", MENSAGEM);
    }

    @Test
    void responsavelSemAceite_deveConseguirConsultarEAceitarOTermo_eDepoisAcessarOsDados() {

        // Arrange
        Familia familia = criarFamilia(criarResponsavelSemAceite());

        // Act + Assert 1: as rotas do termo continuam liberadas
        var status = chamar(HttpMethod.GET, "/api/termo/status", familia.token(), null, TermoStatusResponse.class);
        assertThat(status.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(status.getBody()).isNotNull();
        assertThat(status.getBody().precisaAceitar()).isTrue();

        var aceite = chamar(HttpMethod.POST, "/api/termo/aceite", familia.token(), null, Void.class);
        assertThat(aceite.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Act + Assert 2: depois do aceite, os dados do filho voltam a abrir
        var lista = chamar(HttpMethod.GET, "/api/meus-filhos", familia.token(), null, Object[].class);
        assertThat(lista.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void novaVersaoPublicada_deveBloquearQuemSoAceitouAAnterior() {

        // Arrange: responsável com aceite da vigente atual
        Familia familia = criarFamilia(criarUsuario(Perfil.RESPONSAVEL));
        var antes = chamar(HttpMethod.GET, "/api/meus-filhos", familia.token(), null, Object[].class);
        assertThat(antes.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Act: a direção publica uma nova versão
        var rascunho = chamar(HttpMethod.POST, "/api/termos", tokenDirecao, new TermoVersaoRequest(idUnico("nova"), "Termo novo", "Política nova"), TermoVersaoResponse.class);
        assertThat(rascunho.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var publicada = chamar(HttpMethod.POST, "/api/termos/" + rascunho.getBody().id() + "/publicar", tokenDirecao, new PublicarTermoRequest(rascunho.getBody().versao()), TermoVersaoResponse.class);
        assertThat(publicada.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Assert: o mesmo responsável passa a precisar do novo aceite
        var depois = chamar(HttpMethod.GET, "/api/meus-filhos", familia.token(), null, ErroApi.class);
        verificarErro(depois, HttpStatus.FORBIDDEN, "TERMO_PENDENTE", MENSAGEM);
    }

    // ---------- helpers ----------

    /** Um responsável autenticado + um aluno vinculado a ele. */
    private record Familia(String token, UUID alunoId) {
    }

    private Familia criarFamilia(Usuario pai) {
        UUID alunoId = criarAluno().id();
        var vinculo = chamar(HttpMethod.POST, ALUNOS + "/" + alunoId + "/responsaveis", tokenDirecao, new VincularResponsavelRequest(pai.getId()), AlunoResponsavelResponse.class);
        assertThat(vinculo.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return new Familia(autenticar(pai), alunoId);
    }

    private String idUnico(String prefixo) {
        return prefixo + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
