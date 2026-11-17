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
    void responsavelQueAceitouAVigente_deveAcessarOsDadosDoFilho() {
        Familia familia = criarFamilia(criarUsuario(Perfil.RESPONSAVEL));

        var lista = chamar(HttpMethod.GET, "/api/meus-filhos", familia.token(), null, Object[].class);
        var filho = chamar(HttpMethod.GET, "/api/meus-filhos/" + familia.alunoId(), familia.token(), null, Object.class);

        assertThat(lista.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(filho.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void responsavelSemAceite_deveReceber403TermoPendenteNasRotasDosPais() {
        Familia familia = criarFamilia(criarResponsavelSemAceite());

        var lista = chamar(HttpMethod.GET, "/api/meus-filhos", familia.token(), null, ErroApi.class);
        var filho = chamar(HttpMethod.GET, "/api/meus-filhos/" + familia.alunoId(), familia.token(), null, ErroApi.class);
        var informacoes = chamar(
                HttpMethod.GET, ALUNOS + "/" + familia.alunoId() + "/informacoes-relevantes",
                familia.token(), null, ErroApi.class
        );

        verificarErro(lista, HttpStatus.FORBIDDEN, "TERMO_PENDENTE", MENSAGEM);
        verificarErro(filho, HttpStatus.FORBIDDEN, "TERMO_PENDENTE", MENSAGEM);
        verificarErro(informacoes, HttpStatus.FORBIDDEN, "TERMO_PENDENTE", MENSAGEM);
    }

    @Test
    void responsavelSemAceite_deveConseguirConsultarEAceitarOTermo_eDepoisAcessarOsDados() {
        Familia familia = criarFamilia(criarResponsavelSemAceite());

        var status = chamar(HttpMethod.GET, "/api/termo/status", familia.token(), null, TermoStatusResponse.class);
        assertThat(status.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(status.getBody()).isNotNull();
        assertThat(status.getBody().precisaAceitar()).isTrue();

        var aceite = chamar(
                HttpMethod.POST, "/api/termo/aceite", familia.token(),
                new TermoController.AceitarTermoRequest(status.getBody().versao()), Void.class
        );
        assertThat(aceite.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        var lista = chamar(HttpMethod.GET, "/api/meus-filhos", familia.token(), null, Object[].class);
        assertThat(lista.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void novaVersaoPublicada_deveBloquearQuemSoAceitouAAnterior() {
        Familia familia = criarFamilia(criarUsuario(Perfil.RESPONSAVEL));
        var antes = chamar(HttpMethod.GET, "/api/meus-filhos", familia.token(), null, Object[].class);
        assertThat(antes.getStatusCode()).isEqualTo(HttpStatus.OK);

        var rascunho = chamar(
                HttpMethod.POST, "/api/termos", tokenDirecao,
                new TermoVersaoRequest("Termo novo", "Política nova"), TermoVersaoResponse.class
        );
        assertThat(rascunho.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(rascunho.getBody()).isNotNull();

        var publicada = chamar(
                HttpMethod.POST, "/api/termos/" + rascunho.getBody().id() + "/publicar", tokenDirecao,
                new PublicarTermoRequest(rascunho.getBody().versao()), TermoVersaoResponse.class
        );
        assertThat(publicada.getStatusCode()).isEqualTo(HttpStatus.OK);

        var depois = chamar(HttpMethod.GET, "/api/meus-filhos", familia.token(), null, ErroApi.class);
        verificarErro(depois, HttpStatus.FORBIDDEN, "TERMO_PENDENTE", MENSAGEM);
    }

    private record Familia(String token, UUID alunoId) {}

    private Familia criarFamilia(Usuario pai) {
        UUID alunoId = criarAluno().id();
        var vinculo = chamar(
                HttpMethod.POST, ALUNOS + "/" + alunoId + "/responsaveis", tokenDirecao,
                new VincularResponsavelRequest(pai.getId()), AlunoResponsavelResponse.class
        );
        assertThat(vinculo.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return new Familia(autenticar(pai), alunoId);
    }
}