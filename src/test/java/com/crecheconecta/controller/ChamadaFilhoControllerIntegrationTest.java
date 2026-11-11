package com.crecheconecta.controller;

import com.crecheconecta.dto.AlunoResponsavelResponse;
import com.crecheconecta.dto.ChamadaMesResponse;
import com.crecheconecta.dto.VincularResponsavelRequest;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.Presenca;
import com.crecheconecta.entity.Usuario;
import com.crecheconecta.repository.PresencaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChamadaFilhoControllerIntegrationTest extends BaseControllerIntegrationTest {

    @Autowired
    private PresencaRepository presencas;

    @Test
    void consultar_comMesInformado_deveRetornarSoRegistrosDoMesComResumo() {

        // Arrange
        Familia familia = criarFamilia();
        salvarPresenca(familia.alunoId(), LocalDate.of(2026, 10, 30), true); // outro mês: não entra
        salvarPresenca(familia.alunoId(), LocalDate.of(2026, 11, 5), false); // falta
        salvarPresenca(familia.alunoId(), LocalDate.of(2026, 11, 3), true);  // presença
        salvarPresenca(familia.alunoId(), LocalDate.of(2026, 11, 4), false); // falta

        // Act
        var resposta = chamar(HttpMethod.GET, rota(familia.alunoId()) + "?mes=2026-11", familia.token(), null, ChamadaMesResponse.class);

        // Assert
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getHeaders().getCacheControl()).contains("no-store");

        ChamadaMesResponse corpo = resposta.getBody();
        assertThat(corpo).isNotNull();
        assertThat(corpo.alunoId()).isEqualTo(familia.alunoId());
        assertThat(corpo.mes()).isEqualTo(YearMonth.of(2026, 11));
        assertThat(corpo.presencas()).isEqualTo(1);
        assertThat(corpo.faltas()).isEqualTo(2);
        assertThat(corpo.registros())
                .extracting(ChamadaMesResponse.Registro::data)
                .containsExactly(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 5));
        assertThat(corpo.registros().get(0).presente()).isTrue();
        assertThat(corpo.registros().get(1).presente()).isFalse();
    }

    @Test
    void consultar_semMes_deveUsarOMesAtual() {

        // Arrange
        Familia familia = criarFamilia();
        LocalDate hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        salvarPresenca(familia.alunoId(), hoje, true);

        // Act
        var resposta = chamar(HttpMethod.GET, rota(familia.alunoId()), familia.token(), null, ChamadaMesResponse.class);

        // Assert
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).isNotNull();
        assertThat(resposta.getBody().mes()).isEqualTo(YearMonth.from(hoje));
        assertThat(resposta.getBody().presencas()).isEqualTo(1);
    }

    @Test
    void consultar_semRegistrosNoMes_deveRetornarResumoZerado() {

        // Arrange
        Familia familia = criarFamilia();

        // Act
        var resposta = chamar(HttpMethod.GET, rota(familia.alunoId()) + "?mes=2026-02", familia.token(), null, ChamadaMesResponse.class);

        // Assert
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).isNotNull();
        assertThat(resposta.getBody().presencas()).isZero();
        assertThat(resposta.getBody().faltas()).isZero();
        assertThat(resposta.getBody().registros()).isEmpty();
    }

    @Test
    void consultar_comFilhoDeOutroResponsavel_deveRetornar404() {

        // Arrange
        Familia familiaA = criarFamilia();
        Familia familiaB = criarFamilia();
        salvarPresenca(familiaB.alunoId(), LocalDate.of(2026, 11, 3), false);

        // Act
        var resposta = chamar(HttpMethod.GET, rota(familiaB.alunoId()) + "?mes=2026-11", familiaA.token(), null, ErroApi.class);

        // Assert
        verificarErro(resposta, HttpStatus.NOT_FOUND, "ALUNO_NAO_ENCONTRADO", "Aluno não encontrado.");
    }

    @Test
    void consultar_comMesInvalido_deveRetornar400() {

        // Arrange
        Familia familia = criarFamilia();

        // Act
        var resposta = chamar(HttpMethod.GET, rota(familia.alunoId()) + "?mes=novembro", familia.token(), null, ErroApi.class);

        // Assert
        verificarErro(resposta, HttpStatus.BAD_REQUEST, "PARAMETRO_INVALIDO", "Confira os identificadores e os parâmetros enviados.");
    }

    @Test
    void consultar_comPerfilDirecao_deveRetornar403() {

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

    /** Grava a presença direto no banco (é a mesma tabela que o professor preenche na chamada). */
    private void salvarPresenca(UUID alunoId, LocalDate data, boolean presente) {
        var presenca = new Presenca();
        presenca.setAlunoId(alunoId);
        presenca.setTurmaId(UUID.randomUUID());
        presenca.setRegistradoPor(UUID.randomUUID());
        presenca.setData(data);
        presenca.setPresente(presente);
        presenca.setAtualizadoEm(Instant.now());
        presencas.saveAndFlush(presenca);
    }

    private String rota(UUID alunoId) {
        return "/api/meus-filhos/" + alunoId + "/chamada";
    }
}
