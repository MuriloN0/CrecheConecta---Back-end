package com.crecheconecta.controller;

import com.crecheconecta.dto.AlunoResponse;
import com.crecheconecta.dto.AlunoResponsavelResponse;
import com.crecheconecta.dto.VincularResponsavelRequest;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.Usuario;
import com.crecheconecta.repository.AlunoResponsavelRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AlunoResponsavelControllerIntegrationTest extends BaseControllerIntegrationTest {

    @Autowired
    private AlunoResponsavelRepository vinculos;

    @Test
    void vincular_comAlunoEResponsavel_deveRetornar201EPersistir() {

        // Arrange
        AlunoResponse aluno = criarAluno();
        Usuario responsavel = criarUsuario(Perfil.RESPONSAVEL);

        var request = new VincularResponsavelRequest(responsavel.getId());

        // Act
        var resposta = chamar(HttpMethod.POST, rotaResponsaveis(aluno.id()), tokenDirecao, request, AlunoResponsavelResponse.class);

        // Assert
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        AlunoResponsavelResponse corpo = resposta.getBody();

        assertThat(corpo).isNotNull();
        assertThat(corpo.id()).isNotNull();
        assertThat(corpo.alunoId()).isEqualTo(aluno.id());
        assertThat(corpo.responsavelId()).isEqualTo(responsavel.getId());
        assertThat(corpo.criadoEm()).isNotNull();
        assertThat(corpo.encerradoEm()).isNull();

        var salvo = vinculos.findById(corpo.id()).orElseThrow();

        assertThat(salvo.getAlunoId()).isEqualTo(aluno.id());
        assertThat(salvo.getResponsavelId())
                .isEqualTo(responsavel.getId());
        assertThat(salvo.getCriadoEm()).isNotNull();
        assertThat(salvo.getEncerradoEm()).isNull();
    }

    @Test
    void listarResponsaveis_aposVincular_deveRetornar200ComRelacionamento() {

        // Arrange
        AlunoResponse aluno = criarAluno();
        Usuario responsavel = criarUsuario(Perfil.RESPONSAVEL);

        AlunoResponsavelResponse vinculo = criarVinculo(aluno.id(), responsavel.getId());

        // Act
        var resposta = chamar(HttpMethod.GET, rotaResponsaveis(aluno.id()), tokenDirecao, null, AlunoResponsavelResponse[].class);

        // Assert
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).isNotNull().hasSize(1);

        var encontrado = resposta.getBody()[0];

        assertThat(encontrado.id()).isEqualTo(vinculo.id());
        assertThat(encontrado.alunoId()).isEqualTo(aluno.id());
        assertThat(encontrado.responsavelId())
                .isEqualTo(responsavel.getId());
        assertThat(encontrado.criadoEm()).isNotNull();
        assertThat(encontrado.encerradoEm()).isNull();
    }

    @Test
    void encerrar_comVinculoAtual_deveRetornar200EPreservarHistorico() {
        // Arrange
        AlunoResponse aluno = criarAluno();
        Usuario responsavel = criarUsuario(Perfil.RESPONSAVEL);

        AlunoResponsavelResponse vinculo = criarVinculo(aluno.id(), responsavel.getId());

        var criadoEmPersistido = vinculos.findById(vinculo.id())
                .orElseThrow()
                .getCriadoEm();

        // Act
        var resposta = chamar(HttpMethod.POST, rotaResponsaveis(aluno.id()) + "/" + vinculo.id() + "/encerrar", tokenDirecao, null, AlunoResponsavelResponse.class);

        // Assert
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);

        AlunoResponsavelResponse corpo = resposta.getBody();

        assertThat(corpo).isNotNull();
        assertThat(corpo.id()).isEqualTo(vinculo.id());
        assertThat(corpo.alunoId()).isEqualTo(aluno.id());
        assertThat(corpo.responsavelId()).isEqualTo(responsavel.getId());
        assertThat(corpo.criadoEm()).isEqualTo(criadoEmPersistido);
        assertThat(corpo.encerradoEm()).isNotNull();

        // O vínculo permanece no banco como histórico.
        var salvo = vinculos.findById(vinculo.id()).orElseThrow();

        assertThat(salvo.getAlunoId()).isEqualTo(aluno.id());
        assertThat(salvo.getResponsavelId()).isEqualTo(responsavel.getId());
        assertThat(salvo.getCriadoEm()).isEqualTo(criadoEmPersistido);
        assertThat(salvo.getEncerradoEm()).isNotNull();

        // A API deixa de retorná-lo entre os vínculos atuais.
        var consulta = chamar(HttpMethod.GET, rotaResponsaveis(aluno.id()), tokenDirecao, null, AlunoResponsavelResponse[].class);

        assertThat(consulta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(consulta.getBody()).isNotNull().isEmpty();
    }

    @Test
    void vincular_comAlunoJaVinculado_deveRetornar400SemCriarSegundoVinculo() {

        // Arrange
        AlunoResponse aluno = criarAluno();

        Usuario primeiro = criarUsuario(Perfil.RESPONSAVEL);
        Usuario segundo = criarUsuario(Perfil.RESPONSAVEL);

        AlunoResponsavelResponse original = criarVinculo(aluno.id(), primeiro.getId());

        var request = new VincularResponsavelRequest(segundo.getId());

        // Act
        var resposta = chamar(HttpMethod.POST, rotaResponsaveis(aluno.id()), tokenDirecao, request, ErroApi.class);

        // Assert
        verificarErro(resposta, HttpStatus.BAD_REQUEST, "REGRA_NEGOCIO", "O aluno já possui um responsável vinculado. " + "Encerre o vínculo anterior antes de continuar.");

        var atuais = vinculos.findByAlunoIdAndEncerradoEmIsNullOrderByCriadoEmAsc(aluno.id());

        assertThat(atuais).hasSize(1);
        assertThat(atuais.getFirst().getId()).isEqualTo(original.id());
        assertThat(atuais.getFirst().getResponsavelId()).isEqualTo(primeiro.getId());

        assertThat(vinculos.existsByResponsavelIdAndEncerradoEmIsNull(segundo.getId())).isFalse();
    }

    private AlunoResponsavelResponse criarVinculo(UUID alunoId, UUID responsavelId) {

        var resposta = chamar(HttpMethod.POST, rotaResponsaveis(alunoId), tokenDirecao, new VincularResponsavelRequest(responsavelId), AlunoResponsavelResponse.class);

        assertThat(resposta.getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        assertThat(resposta.getBody()).isNotNull();
        assertThat(resposta.getBody().id()).isNotNull();

        return resposta.getBody();
    }

    private String rotaResponsaveis(UUID alunoId) {

        return ALUNOS + "/" + alunoId + "/responsaveis";
    }
}