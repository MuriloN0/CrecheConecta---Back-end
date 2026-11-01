package com.crecheconecta.controller;

import com.crecheconecta.dto.*;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.Usuario;
import com.crecheconecta.repository.AlunoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AlunoControllerIntegrationTest extends BaseControllerIntegrationTest {

    @Autowired
    private AlunoRepository alunos;

    @Test
    void cadastrar_comDadosValidos_deveRetornar201ComLocationEPersistir() {

        // Arrange
        String nome = "Aluno " + UUID.randomUUID();
        var request = new CadastrarAlunoRequest(nome, NASCIMENTO);

        // Act
        var resposta = chamar(
                HttpMethod.POST, ALUNOS, tokenDirecao,
                request, AlunoResponse.class
        );

        // Assert
        assertThat(resposta.getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        AlunoResponse corpo = resposta.getBody();

        assertThat(corpo).isNotNull();
        assertThat(corpo.id()).isNotNull();
        assertThat(corpo.nome()).isEqualTo(nome);
        assertThat(corpo.dataNascimento()).isEqualTo(NASCIMENTO);
        assertThat(corpo.ativo()).isTrue();
        assertThat(corpo.versao()).isNotNull();

        assertThat(resposta.getHeaders().getLocation())
                .isEqualTo(URI.create(ALUNOS + "/" + corpo.id()));

        assertThat(resposta.getHeaders().getCacheControl())
                .contains("no-store");

        var salvo = alunos.findById(corpo.id()).orElseThrow();

        assertThat(salvo.getNome()).isEqualTo(nome);
        assertThat(salvo.getDataNascimento()).isEqualTo(NASCIMENTO);
        assertThat(salvo.isAtivo()).isTrue();
        assertThat(salvo.getVersao()).isEqualTo(corpo.versao());
    }

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
    void atualizar_comVersaoAtual_deveRetornar200EPersistirAlteracao() {

        // Arrange
        AlunoResponse criado = criarAluno();

        String novoNome = "Nome atualizado " + UUID.randomUUID();
        LocalDate novoNascimento = LocalDate.of(2021, 8, 20);

        var request = new AtualizarAlunoRequest(novoNome, novoNascimento, criado.versao());

        // Act
        var resposta = chamar(HttpMethod.PUT, ALUNOS + "/" + criado.id(), tokenDirecao, request, AlunoResponse.class);

        // Assert
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);

        AlunoResponse corpo = resposta.getBody();

        assertThat(corpo).isNotNull();
        assertThat(corpo.id()).isEqualTo(criado.id());
        assertThat(corpo.nome()).isEqualTo(novoNome);
        assertThat(corpo.dataNascimento()).isEqualTo(novoNascimento);
        assertThat(corpo.ativo()).isTrue();
        assertThat(corpo.versao()).isGreaterThan(criado.versao());

        var salvo = alunos.findById(criado.id()).orElseThrow();

        assertThat(salvo.getNome()).isEqualTo(novoNome);
        assertThat(salvo.getDataNascimento()).isEqualTo(novoNascimento);
        assertThat(salvo.getVersao()).isEqualTo(corpo.versao());
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
    void cadastrar_comNomeVazio_deveRetornar400SemPersistir() {

        // Arrange
        long quantidadeAntes = alunos.count();
        var request = new CadastrarAlunoRequest("", NASCIMENTO);

        // Act
        var resposta = chamar(HttpMethod.POST, ALUNOS, tokenDirecao, request, ErroApi.class);

        // Assert
        verificarErro(resposta, HttpStatus.BAD_REQUEST, "DADOS_INVALIDOS", "Confira os campos enviados.");

        assertThat(alunos.count()).isEqualTo(quantidadeAntes);
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
    void atualizar_comVersaoDesatualizada_deveRetornar409SemAlterar() {

        // Arrange
        AlunoResponse criado = criarAluno();

        var primeiraAlteracao = new AtualizarAlunoRequest("Nome confirmado", NASCIMENTO, criado.versao());

        var atualizacao = chamar(HttpMethod.PUT, ALUNOS + "/" + criado.id(), tokenDirecao, primeiraAlteracao, AlunoResponse.class);

        assertThat(atualizacao.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(atualizacao.getBody()).isNotNull();

        var requestDesatualizado = new AtualizarAlunoRequest("Nome que deve ser rejeitado", NASCIMENTO, criado.versao());

        // Act
        var resposta = chamar(HttpMethod.PUT, ALUNOS + "/" + criado.id(), tokenDirecao, requestDesatualizado, ErroApi.class);

        // Assert
        verificarErro(resposta, HttpStatus.CONFLICT, "CONFLITO_VERSAO", "Cadastro alterado por outra pessoa. Recarregue os dados.");

        var salvo = alunos.findById(criado.id()).orElseThrow();

        assertThat(salvo.getNome()).isEqualTo("Nome confirmado");
        assertThat(salvo.getVersao()).isEqualTo(atualizacao.getBody().versao());
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