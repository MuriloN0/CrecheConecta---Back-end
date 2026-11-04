package com.crecheconecta.controller;

import com.crecheconecta.dto.AlunoResponsavelResponse;
import com.crecheconecta.dto.DiarioRotinaResponse;
import com.crecheconecta.dto.VincularResponsavelRequest;
import com.crecheconecta.entity.Alimentacao;
import com.crecheconecta.entity.DiarioRotina;
import com.crecheconecta.entity.Humor;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.Sono;
import com.crecheconecta.entity.Usuario;
import com.crecheconecta.repository.DiarioRotinaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DiarioRotinaFilhoControllerIntegrationTest extends BaseControllerIntegrationTest {

    @Autowired
    private DiarioRotinaRepository diarios;

    @Test
    void consultar_comDataInformada_deveRetornarODiarioDaqueleDia() {

        // Arrange
        Familia familia = criarFamilia();
        salvarDiario(familia.alunoId(), LocalDate.of(2026, 11, 2), Alimentacao.COMEU_TUDO, Sono.DORMIU_BEM, true, Humor.FELIZ, "Brincou muito no parque.");
        salvarDiario(familia.alunoId(), LocalDate.of(2026, 11, 3), Alimentacao.COMEU_POUCO, Sono.NAO_DORMIU, false, Humor.CHOROSO, null);

        // Act
        var resposta = chamar(HttpMethod.GET, rota(familia.alunoId()) + "?data=2026-11-02", familia.token(), null, DiarioRotinaResponse.class);

        // Assert
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getHeaders().getCacheControl()).contains("no-store");

        DiarioRotinaResponse corpo = resposta.getBody();
        assertThat(corpo).isNotNull();
        assertThat(corpo.alunoId()).isEqualTo(familia.alunoId());
        assertThat(corpo.data()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(corpo.alimentacao()).isEqualTo(Alimentacao.COMEU_TUDO);
        assertThat(corpo.sono()).isEqualTo(Sono.DORMIU_BEM);
        assertThat(corpo.evacuacao()).isTrue();
        assertThat(corpo.humor()).isEqualTo(Humor.FELIZ);
        assertThat(corpo.observacaoProfessor()).isEqualTo("Brincou muito no parque.");
    }

    @Test
    void consultar_semData_deveRetornarODiarioMaisRecente() {

        // Arrange
        Familia familia = criarFamilia();
        salvarDiario(familia.alunoId(), LocalDate.of(2026, 10, 30), Alimentacao.COMEU_BEM, Sono.DORMIU_BEM, true, Humor.CALMO, null);
        salvarDiario(familia.alunoId(), LocalDate.of(2026, 11, 3), Alimentacao.COMEU_POUCO, Sono.DORMIU_POUCO, false, Humor.AGITADO, null);
        salvarDiario(familia.alunoId(), LocalDate.of(2026, 11, 1), Alimentacao.NAO_COMEU, Sono.NAO_DORMIU, false, Humor.CHOROSO, null);

        // Act
        var resposta = chamar(HttpMethod.GET, rota(familia.alunoId()), familia.token(), null, DiarioRotinaResponse.class);

        // Assert
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).isNotNull();
        assertThat(resposta.getBody().data()).isEqualTo(LocalDate.of(2026, 11, 3));
        assertThat(resposta.getBody().humor()).isEqualTo(Humor.AGITADO);
    }

    @Test
    void consultar_comDiaSemRegistro_deveRetornar404() {

        // Arrange
        Familia familia = criarFamilia();
        salvarDiario(familia.alunoId(), LocalDate.of(2026, 11, 2), Alimentacao.COMEU_BEM, Sono.DORMIU_BEM, true, Humor.FELIZ, null);

        // Act
        var resposta = chamar(HttpMethod.GET, rota(familia.alunoId()) + "?data=2026-11-05", familia.token(), null, ErroApi.class);

        // Assert
        verificarErro(resposta, HttpStatus.NOT_FOUND, "DIARIO_ROTINA_NAO_ENCONTRADO", "Diário de rotina não encontrado.");
    }

    @Test
    void consultar_semNenhumDiario_deveRetornar404() {

        // Arrange
        Familia familia = criarFamilia();

        // Act
        var resposta = chamar(HttpMethod.GET, rota(familia.alunoId()), familia.token(), null, ErroApi.class);

        // Assert
        verificarErro(resposta, HttpStatus.NOT_FOUND, "DIARIO_ROTINA_NAO_ENCONTRADO", "Diário de rotina não encontrado.");
    }

    @Test
    void consultar_comFilhoDeOutroResponsavel_deveRetornar404DeAluno() {

        // Arrange
        Familia familiaA = criarFamilia();
        Familia familiaB = criarFamilia();
        salvarDiario(familiaB.alunoId(), LocalDate.of(2026, 11, 2), Alimentacao.COMEU_BEM, Sono.DORMIU_BEM, true, Humor.FELIZ, "Dado privado do filho do B");

        // Act
        var resposta = chamar(HttpMethod.GET, rota(familiaB.alunoId()) + "?data=2026-11-02", familiaA.token(), null, ErroApi.class);

        // Assert
        verificarErro(resposta, HttpStatus.NOT_FOUND, "ALUNO_NAO_ENCONTRADO", "Aluno não encontrado.");
    }

    @Test
    void consultar_comDataInvalida_deveRetornar400() {

        // Arrange
        Familia familia = criarFamilia();

        // Act
        var resposta = chamar(HttpMethod.GET, rota(familia.alunoId()) + "?data=02-11-2026", familia.token(), null, ErroApi.class);

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

    /** Grava o diário direto no banco: o lado do professor (registrar) ainda não existe. */
    private void salvarDiario(UUID alunoId, LocalDate data, Alimentacao alimentacao, Sono sono, boolean evacuacao, Humor humor, String observacao) {
        var diario = new DiarioRotina();
        diario.setAlunoId(alunoId);
        diario.setTurmaId(UUID.randomUUID());
        diario.setProfessorId(UUID.randomUUID());
        diario.setData(data);
        diario.setAlimentacao(alimentacao);
        diario.setSono(sono);
        diario.setEvacuacao(evacuacao);
        diario.setHumor(humor);
        diario.setObservacaoProfessor(observacao);
        diario.setCriadoEm(Instant.now());
        diarios.saveAndFlush(diario);
    }

    private String rota(UUID alunoId) {
        return "/api/meus-filhos/" + alunoId + "/diario";
    }
}
