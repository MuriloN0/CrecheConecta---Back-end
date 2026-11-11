package com.crecheconecta.controller;

import com.crecheconecta.dto.*;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.Usuario;
import com.crecheconecta.repository.AlunoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AlunoControllerIntegrationTest extends BaseControllerIntegrationTest {

    @Autowired
    private AlunoRepository alunos;

    // O cadastro e a edição direta de aluno (POST/PUT /api/alunos) saíram da API no PR #41:
    // o aluno nasce no convite do responsável. Os testes desses endpoints foram removidos.

    @Test
    void listar_comDirecao_deveRetornar200ComAlunoNaPagina() {

        // Arrange
        AlunoResponse criado = criarAluno();
        var encontrados = new ArrayList<AlunoResponse>();

        int pagina = 0;
        int totalPaginas;

        // Act
        do {
            var resposta = chamar(HttpMethod.GET, ALUNOS + "?pagina=" + pagina + "&tamanho=100&ativo=true", tokenDirecao, null, PaginaAlunos.class);

            // Assert: cada página deve responder corretamente.
            assertThat(resposta.getStatusCode())
                    .isEqualTo(HttpStatus.OK);
            assertThat(resposta.getBody()).isNotNull();
            assertThat(resposta.getBody().content()).isNotNull();

            encontrados.addAll(resposta.getBody().content());
            totalPaginas = resposta.getBody().totalPages();
            pagina++;
        } while (pagina < totalPaginas);

        assertThat(encontrados).anySatisfy(aluno -> {
            assertThat(aluno.id()).isEqualTo(criado.id());
            assertThat(aluno.nome()).isEqualTo(criado.nome());
            assertThat(aluno.dataNascimento()).isEqualTo(NASCIMENTO);
            assertThat(aluno.ativo()).isTrue();
        });
    }

    @Test
    void visualizar_comAlunoExistente_deveRetornar200ComDados() {

        // Arrange
        AlunoResponse criado = criarAluno();

        // Act
        var resposta = chamar(HttpMethod.GET, ALUNOS + "/" + criado.id(), tokenDirecao, null, AlunoResponse.class);

        // Assert
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).isEqualTo(criado);
    }

    @Test
    void inativar_comAlunoAtivo_deveRetornar200EPreservarCadastro() {

        // Arrange
        AlunoResponse criado = criarAluno();
        var request = new InativarAlunoRequest(criado.versao());

        // Act
        var resposta = chamar(HttpMethod.POST, ALUNOS + "/" + criado.id() + "/inativar", tokenDirecao, request, AlunoResponse.class);

        // Assert
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);

        AlunoResponse corpo = resposta.getBody();

        assertThat(corpo).isNotNull();
        assertThat(corpo.id()).isEqualTo(criado.id());
        assertThat(corpo.nome()).isEqualTo(criado.nome());
        assertThat(corpo.ativo()).isFalse();
        assertThat(corpo.versao()).isGreaterThan(criado.versao());

        var salvo = alunos.findById(criado.id()).orElseThrow();

        assertThat(salvo.isAtivo()).isFalse();
        assertThat(salvo.getNome()).isEqualTo(criado.nome());
    }

    @Test
    void visualizar_comIdInexistente_deveRetornar404ComMensagem() {

        // Arrange
        UUID idInexistente = UUID.randomUUID();

        // Act
        var resposta = chamar(HttpMethod.GET, ALUNOS + "/" + idInexistente, tokenDirecao, null, ErroApi.class);

        // Assert
        verificarErro(resposta, HttpStatus.NOT_FOUND, "ALUNO_NAO_ENCONTRADO", "Aluno não encontrado.");
    }

    @Test
    void listar_semToken_deveRetornar401ComMensagem() {

        // Arrange
        String tokenAusente = null;

        // Act
        var resposta = chamar(HttpMethod.GET, ALUNOS, tokenAusente, null, ErroApi.class);

        // Assert
        verificarErro(resposta, HttpStatus.UNAUTHORIZED, "NAO_AUTENTICADO", "Autenticação necessária ou sessão inválida.");

        assertThat(resposta.getHeaders().getFirst("WWW-Authenticate")).isEqualTo("Bearer");
    }

    @Test
    void listar_comProfessor_deveRetornar403ComMensagem() {

        // Arrange
        Usuario professor = criarUsuario(Perfil.PROFESSOR);
        String tokenProfessor = autenticar(professor);

        // Act
        var resposta = chamar(HttpMethod.GET, ALUNOS, tokenProfessor, null, ErroApi.class);

        // Assert
        verificarErro(resposta, HttpStatus.FORBIDDEN, "ACESSO_NEGADO", "Você não possui permissão para esta operação.");
    }
}