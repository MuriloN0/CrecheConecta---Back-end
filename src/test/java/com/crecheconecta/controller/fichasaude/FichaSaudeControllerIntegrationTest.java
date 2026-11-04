package com.crecheconecta.controller.fichasaude;

import com.crecheconecta.controller.base.IntegracaoTestBase;
import com.crecheconecta.dto.AlunoResponsavelResponse;
import com.crecheconecta.dto.AlunoResponse;
import com.crecheconecta.dto.AtualizarFichaSaudeRequest;
import com.crecheconecta.dto.CadastrarAlunoRequest;
import com.crecheconecta.dto.CadastrarTurmaRequest;
import com.crecheconecta.dto.FichaSaudeRequest;
import com.crecheconecta.dto.FichaSaudeResponse;
import com.crecheconecta.dto.FichaSaudeResumoResponse;
import com.crecheconecta.dto.MatriculaTurmaResponse;
import com.crecheconecta.dto.MatricularAlunoRequest;
import com.crecheconecta.dto.TurmaProfessorResponse;
import com.crecheconecta.dto.TurmaResponse;
import com.crecheconecta.dto.VincularProfessorTurmaRequest;
import com.crecheconecta.dto.VincularResponsavelRequest;
import com.crecheconecta.entity.FichaSaude;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.Usuario;
import com.crecheconecta.repository.FichaSaudeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes de integracao do FichaSaudeController (Informacoes Relevantes).
 *
 * Regra de acesso:
 * - RESPONSAVEL com vinculo atual com o aluno: escreve e le;
 * - DIRECAO: le as fichas de qualquer aluno (nao escreve);
 * - PROFESSOR: le apenas de alunos matriculados numa turma em que leciona (nao escreve).
 *
 * Fluxo real exercitado em cada teste:
 * RestClient -> HTTP real -> AutenticacaoTokenFilter/SecurityFilterChain -> FichaSaudeController
 * -> FichaSaudeService (+ VinculoResponsavelService, CriptografiaService) -> FichaSaudeRepository -> H2 em memoria
 *
 * O cenario (aluno, vinculo, turma, matricula, professor) e montado pelos endpoints reais da direcao.
 * Unico mock: EmailService (API Resend).
 */
class FichaSaudeControllerIntegrationTest extends IntegracaoTestBase {

    private static final String SEM_PERMISSAO = "Você não possui permissão para esta operação.";

    @Autowired
    private FichaSaudeRepository fichaSaudeRepository; // usado so para conferir persistencia

    private String tokenDirecao;

    @BeforeEach
    void autenticarDirecao() {
        tokenDirecao = autenticar(criarUsuario(Perfil.DIRECAO));
    }

    // ---------- POST /api/alunos/{alunoId}/fichas-saude ----------

    @Test
    void cadastrar_comoResponsavelDoAluno_deveRetornar201ComLocationEPersistirObservacoesCifradas() {
        long ts = System.nanoTime();
        Familia familia = criarFamilia();
        FichaSaudeRequest request = new FichaSaudeRequest("  Alergias " + ts + "  ", "Alergia a amendoim");

        ResponseEntity<Void> response = client.post().uri(fichasPath(familia.alunoId()))
                .header(HttpHeaders.AUTHORIZATION, bearer(familia.token()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String location = response.getHeaders().getLocation().toString();
        assertThat(location).startsWith(fichasPath(familia.alunoId()) + "/");
        UUID fichaId = idDoLocation(location);

        // Checa persistencia: ficha do aluno, nome sem espacos e observacoes gravadas cifradas
        FichaSaude salva = fichaSaudeRepository.findById(fichaId).orElseThrow();
        assertThat(salva.getAlunoId()).isEqualTo(familia.alunoId());
        assertThat(salva.getNome()).isEqualTo("Alergias " + ts);
        assertThat(salva.getObservacoes())
                .isNotBlank()
                .isNotEqualTo("Alergia a amendoim");
        assertThat(salva.getVersao()).isZero();
    }

    @Test
    void cadastrar_semToken_deveRetornar401ENaoPersistir() {
        UUID alunoId = criarAluno("Aluno Sem Token " + System.nanoTime());

        ResponseEntity<Map<String, Object>> response = client.post().uri(fichasPath(alunoId))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new FichaSaudeRequest("Sem token", null))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("codigo", "NAO_AUTENTICADO");
        assertThat(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
        assertThat(fichaSaudeRepository.findByAlunoId(alunoId)).isEmpty();
    }

    @Test
    void cadastrar_comoProfessor_deveRetornar403ENaoPersistir() {
        String tokenProfessor = autenticar(criarUsuario(Perfil.PROFESSOR));
        UUID alunoId = criarAluno("Aluno Professor " + System.nanoTime());

        ResponseEntity<Map<String, Object>> response = client.post().uri(fichasPath(alunoId))
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenProfessor))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new FichaSaudeRequest("Tentativa do professor", null))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("codigo", "ACESSO_NEGADO");
        assertThat(response.getBody().get("detail")).isEqualTo(SEM_PERMISSAO);
        assertThat(fichaSaudeRepository.findByAlunoId(alunoId)).isEmpty();
    }

    @Test
    void cadastrar_comoDirecao_deveRetornar403ENaoPersistir() {
        UUID alunoId = criarAluno("Aluno Direcao Escreve " + System.nanoTime());

        ResponseEntity<Map<String, Object>> response = client.post().uri(fichasPath(alunoId))
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenDirecao))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new FichaSaudeRequest("Tentativa da direcao", null))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("codigo", "ACESSO_NEGADO");
        assertThat(fichaSaudeRepository.findByAlunoId(alunoId)).isEmpty();
    }

    @Test
    void cadastrar_paraFilhoDeOutroResponsavel_deveRetornar404ENaoPersistir() {
        Familia familiaA = criarFamilia();
        Familia familiaB = criarFamilia();

        ResponseEntity<Map<String, Object>> response = client.post().uri(fichasPath(familiaB.alunoId()))
                .header(HttpHeaders.AUTHORIZATION, bearer(familiaA.token()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new FichaSaudeRequest("Invasao", "Nao deveria gravar"))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("codigo", "ALUNO_NAO_ENCONTRADO");
        assertThat(fichaSaudeRepository.findByAlunoId(familiaB.alunoId())).isEmpty();
    }

    @Test
    void cadastrar_comNomeEmBranco_deveRetornar400ComCampoNome() {
        Familia familia = criarFamilia();

        ResponseEntity<Map<String, Object>> response = client.post().uri(fichasPath(familia.alunoId()))
                .header(HttpHeaders.AUTHORIZATION, bearer(familia.token()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new FichaSaudeRequest("   ", "Observacao qualquer"))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("codigo", "DADOS_INVALIDOS");
        assertThat(campos(response)).containsExactly("nome");
        assertThat(fichaSaudeRepository.findByAlunoId(familia.alunoId())).isEmpty();
    }

    @Test
    void cadastrar_paraAlunoInexistente_deveRetornar404ComMensagemENaoPersistir() {
        Familia familia = criarFamilia();
        UUID alunoInexistente = UUID.randomUUID();

        ResponseEntity<Map<String, Object>> response = client.post().uri(fichasPath(alunoInexistente))
                .header(HttpHeaders.AUTHORIZATION, bearer(familia.token()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new FichaSaudeRequest("Ficha orfa", null))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("codigo", "ALUNO_NAO_ENCONTRADO");
        assertThat(response.getBody().get("mensagem")).isEqualTo("Aluno não encontrado.");
        assertThat(fichaSaudeRepository.findByAlunoId(alunoInexistente)).isEmpty();
    }

    // ---------- GET /api/alunos/{alunoId}/fichas-saude ----------

    @Test
    void listar_comoResponsavel_deveRetornar200SomenteComFichasDoProprioFilho() {
        long ts = System.nanoTime();
        Familia familiaA = criarFamilia();
        Familia familiaB = criarFamilia();
        UUID fichaA1 = criarFicha(familiaA, "Alergias " + ts, "Lactose");
        UUID fichaA2 = criarFicha(familiaA, "Medicamentos " + ts, "Antialergico");
        UUID fichaB = criarFicha(familiaB, "Vacinas " + ts, null);

        ResponseEntity<List<FichaSaudeResumoResponse>> response = client.get().uri(fichasPath(familiaA.alunoId()))
                .header(HttpHeaders.AUTHORIZATION, bearer(familiaA.token()))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        assertThat(response.getBody())
                .extracting(FichaSaudeResumoResponse::id)
                .containsExactlyInAnyOrder(fichaA1, fichaA2)
                .doesNotContain(fichaB);
        assertThat(response.getBody())
                .extracting(FichaSaudeResumoResponse::nome)
                .containsExactlyInAnyOrder("Alergias " + ts, "Medicamentos " + ts);
        assertThat(response.getBody())
                .extracting(FichaSaudeResumoResponse::quantidadeAnexos)
                .containsOnly(0);
    }

    @Test
    void listar_fichasDoFilhoDeOutroResponsavel_deveRetornar404() {
        Familia familiaA = criarFamilia();
        Familia familiaB = criarFamilia();
        criarFicha(familiaB, "Ficha do B " + System.nanoTime(), "Asma");

        ResponseEntity<Map<String, Object>> response = client.get().uri(fichasPath(familiaB.alunoId()))
                .header(HttpHeaders.AUTHORIZATION, bearer(familiaA.token()))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("codigo", "ALUNO_NAO_ENCONTRADO");
    }

    @Test
    void listar_comoDirecao_deveRetornar200ComFichasDoAluno() {
        long ts = System.nanoTime();
        Familia familia = criarFamilia();
        UUID fichaId = criarFicha(familia, "Ficha vista pela direcao " + ts, "Bronquite");

        ResponseEntity<List<FichaSaudeResumoResponse>> response = client.get().uri(fichasPath(familia.alunoId()))
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenDirecao))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .extracting(FichaSaudeResumoResponse::id)
                .containsExactly(fichaId);
    }

    @Test
    void listar_comoDirecaoParaAlunoInexistente_deveRetornar404() {
        ResponseEntity<Map<String, Object>> response = client.get().uri(fichasPath(UUID.randomUUID()))
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenDirecao))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("codigo", "ALUNO_NAO_ENCONTRADO");
    }

    @Test
    void listar_comoProfessorDaTurmaDoAluno_deveRetornar200() {
        long ts = System.nanoTime();
        Familia familia = criarFamilia();
        UUID fichaId = criarFicha(familia, "Ficha vista pelo professor " + ts, "Epilepsia");
        Usuario professor = criarUsuario(Perfil.PROFESSOR);
        UUID turmaId = criarTurma();
        matricular(turmaId, familia.alunoId());
        vincularProfessor(turmaId, professor.getId());

        ResponseEntity<List<FichaSaudeResumoResponse>> response = client.get().uri(fichasPath(familia.alunoId()))
                .header(HttpHeaders.AUTHORIZATION, bearer(autenticar(professor)))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .extracting(FichaSaudeResumoResponse::id)
                .containsExactly(fichaId);
    }

    @Test
    void listar_comoProfessorDeOutraTurma_deveRetornar404() {
        Familia familia = criarFamilia();
        criarFicha(familia, "Ficha de outra turma " + System.nanoTime(), null);
        Usuario professor = criarUsuario(Perfil.PROFESSOR);
        UUID turmaDoAluno = criarTurma();
        UUID turmaDoProfessor = criarTurma();
        matricular(turmaDoAluno, familia.alunoId());
        vincularProfessor(turmaDoProfessor, professor.getId());

        ResponseEntity<Map<String, Object>> response = client.get().uri(fichasPath(familia.alunoId()))
                .header(HttpHeaders.AUTHORIZATION, bearer(autenticar(professor)))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("codigo", "ALUNO_NAO_ENCONTRADO");
    }

    @Test
    void listar_comoProfessorParaAlunoSemMatricula_deveRetornar404() {
        Familia familia = criarFamilia();
        Usuario professor = criarUsuario(Perfil.PROFESSOR);
        vincularProfessor(criarTurma(), professor.getId());

        ResponseEntity<Map<String, Object>> response = client.get().uri(fichasPath(familia.alunoId()))
                .header(HttpHeaders.AUTHORIZATION, bearer(autenticar(professor)))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("codigo", "ALUNO_NAO_ENCONTRADO");
    }

    // ---------- GET /api/alunos/{alunoId}/fichas-saude/{fichaId} ----------

    @Test
    void visualizar_comFichaExistente_deveRetornar200ComObservacoesDecifradas() {
        long ts = System.nanoTime();
        Familia familia = criarFamilia();
        UUID fichaId = criarFicha(familia, "Restricoes alimentares " + ts, "Nao pode comer gluten");

        ResponseEntity<FichaSaudeResponse> response = client.get().uri(fichasPath(familia.alunoId()) + "/" + fichaId)
                .header(HttpHeaders.AUTHORIZATION, bearer(familia.token()))
                .retrieve()
                .toEntity(FichaSaudeResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        FichaSaudeResponse body = response.getBody();
        assertThat(body.id()).isEqualTo(fichaId);
        assertThat(body.alunoId()).isEqualTo(familia.alunoId());
        assertThat(body.nome()).isEqualTo("Restricoes alimentares " + ts);
        assertThat(body.observacoes()).isEqualTo("Nao pode comer gluten");
        assertThat(body.versao()).isZero();
        assertThat(body.dataCriacao()).isNotNull();
        assertThat(body.dataAtualizacao()).isNotNull();
        assertThat(body.anexos()).isEmpty();
    }

    @Test
    void visualizar_comoDirecao_deveRetornar200ComObservacoesDecifradas() {
        Familia familia = criarFamilia();
        UUID fichaId = criarFicha(familia, "Ficha detalhada " + System.nanoTime(), "Usa bombinha");

        ResponseEntity<FichaSaudeResponse> response = client.get().uri(fichasPath(familia.alunoId()) + "/" + fichaId)
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenDirecao))
                .retrieve()
                .toEntity(FichaSaudeResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().observacoes()).isEqualTo("Usa bombinha");
    }

    @Test
    void visualizar_comFichaInexistente_deveRetornar404ComMensagem() {
        Familia familia = criarFamilia();

        ResponseEntity<Map<String, Object>> response = client.get()
                .uri(fichasPath(familia.alunoId()) + "/" + UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, bearer(familia.token()))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("codigo", "FICHA_SAUDE_NAO_ENCONTRADA");
        assertThat(response.getBody().get("mensagem")).isEqualTo("Ficha de saúde não encontrada.");
    }

    @Test
    void visualizar_fichaDeOutroAlunoPeloCaminhoDoProprioFilho_deveRetornar404() {
        Familia familiaA = criarFamilia();
        Familia familiaB = criarFamilia();
        UUID fichaDoB = criarFicha(familiaB, "Ficha privada " + System.nanoTime(), "Asma");

        // Pai A usa o ID do PROPRIO filho no caminho, mas o ID da ficha do filho do B
        ResponseEntity<Map<String, Object>> response = client.get()
                .uri(fichasPath(familiaA.alunoId()) + "/" + fichaDoB)
                .header(HttpHeaders.AUTHORIZATION, bearer(familiaA.token()))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("codigo", "FICHA_SAUDE_NAO_ENCONTRADA");
    }

    // ---------- PUT /api/alunos/{alunoId}/fichas-saude/{fichaId} ----------

    @Test
    void atualizar_comVersaoAtual_deveRetornar200EGravarDadosNovosComVersaoIncrementada() {
        long ts = System.nanoTime();
        Familia familia = criarFamilia();
        UUID fichaId = criarFicha(familia, "Nome antigo " + ts, "Observacao antiga");
        AtualizarFichaSaudeRequest request =
                new AtualizarFichaSaudeRequest("Nome novo " + ts, "Observacao nova", 0L);

        ResponseEntity<FichaSaudeResponse> response = client.put().uri(fichasPath(familia.alunoId()) + "/" + fichaId)
                .header(HttpHeaders.AUTHORIZATION, bearer(familia.token()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toEntity(FichaSaudeResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().id()).isEqualTo(fichaId);
        assertThat(response.getBody().nome()).isEqualTo("Nome novo " + ts);
        assertThat(response.getBody().observacoes()).isEqualTo("Observacao nova");
        // A propria resposta do PUT ja traz a versao nova (pronta para a proxima edicao)
        assertThat(response.getBody().versao()).isEqualTo(1L);

        // Efeito observavel: nova consulta traz os dados novos e a versao incrementada
        FichaSaudeResponse depois = client.get().uri(fichasPath(familia.alunoId()) + "/" + fichaId)
                .header(HttpHeaders.AUTHORIZATION, bearer(familia.token()))
                .retrieve()
                .body(FichaSaudeResponse.class);
        assertThat(depois.nome()).isEqualTo("Nome novo " + ts);
        assertThat(depois.observacoes()).isEqualTo("Observacao nova");
        assertThat(depois.versao()).isEqualTo(1L);
        assertThat(fichaSaudeRepository.findById(fichaId))
                .get()
                .extracting(FichaSaude::getNome)
                .isEqualTo("Nome novo " + ts);
    }

    @Test
    void atualizar_comVersaoDesatualizada_deveRetornar409EManterDadosAntigos() {
        long ts = System.nanoTime();
        Familia familia = criarFamilia();
        UUID fichaId = criarFicha(familia, "Versao inicial " + ts, null);
        AtualizarFichaSaudeRequest request =
                new AtualizarFichaSaudeRequest("Edicao concorrente " + ts, null, 7L);

        ResponseEntity<Map<String, Object>> response = client.put().uri(fichasPath(familia.alunoId()) + "/" + fichaId)
                .header(HttpHeaders.AUTHORIZATION, bearer(familia.token()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("codigo", "CONFLITO_VERSAO");
        assertThat(response.getBody().get("mensagem"))
                .isEqualTo("Cadastro alterado por outra pessoa. Recarregue os dados.");
        assertThat(fichaSaudeRepository.findById(fichaId))
                .get()
                .extracting(FichaSaude::getNome)
                .isEqualTo("Versao inicial " + ts);
    }

    @Test
    void atualizar_semVersao_deveRetornar400ComCampoVersao() {
        Familia familia = criarFamilia();
        UUID fichaId = criarFicha(familia, "Sem versao " + System.nanoTime(), null);

        ResponseEntity<Map<String, Object>> response = client.put().uri(fichasPath(familia.alunoId()) + "/" + fichaId)
                .header(HttpHeaders.AUTHORIZATION, bearer(familia.token()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new AtualizarFichaSaudeRequest("Nome qualquer", null, null))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("codigo", "DADOS_INVALIDOS");
        assertThat(campos(response)).containsExactly("versao");
    }

    @Test
    void atualizar_comoDirecao_deveRetornar403EManterDados() {
        long ts = System.nanoTime();
        Familia familia = criarFamilia();
        UUID fichaId = criarFicha(familia, "Original " + ts, null);

        ResponseEntity<Map<String, Object>> response = client.put().uri(fichasPath(familia.alunoId()) + "/" + fichaId)
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenDirecao))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new AtualizarFichaSaudeRequest("Alterado pela direcao", null, 0L))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("codigo", "ACESSO_NEGADO");
        assertThat(fichaSaudeRepository.findById(fichaId))
                .get()
                .extracting(FichaSaude::getNome)
                .isEqualTo("Original " + ts);
    }

    // ---------- DELETE /api/alunos/{alunoId}/fichas-saude/{fichaId} ----------

    @Test
    void excluir_comFichaExistente_deveRetornar204EFichaDeixaDeExistir() {
        Familia familia = criarFamilia();
        UUID fichaId = criarFicha(familia, "Para excluir " + System.nanoTime(), "Temporaria");

        ResponseEntity<Void> response = client.delete().uri(fichasPath(familia.alunoId()) + "/" + fichaId)
                .header(HttpHeaders.AUTHORIZATION, bearer(familia.token()))
                .retrieve()
                .toBodilessEntity();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Efeito observavel: GET seguinte retorna 404 e o registro sumiu do banco
        ResponseEntity<Void> busca = client.get().uri(fichasPath(familia.alunoId()) + "/" + fichaId)
                .header(HttpHeaders.AUTHORIZATION, bearer(familia.token()))
                .retrieve()
                .toBodilessEntity();
        assertThat(busca.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(fichaSaudeRepository.existsById(fichaId)).isFalse();
    }

    @Test
    void excluir_comFichaInexistente_deveRetornar404ComMensagem() {
        Familia familia = criarFamilia();

        ResponseEntity<Map<String, Object>> response = client.delete()
                .uri(fichasPath(familia.alunoId()) + "/" + UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, bearer(familia.token()))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("codigo", "FICHA_SAUDE_NAO_ENCONTRADA");
        assertThat(response.getBody().get("mensagem")).isEqualTo("Ficha de saúde não encontrada.");
    }

    @Test
    void excluir_fichaDoFilhoDeOutroResponsavel_deveRetornar404EManterFicha() {
        Familia familiaA = criarFamilia();
        Familia familiaB = criarFamilia();
        UUID fichaDoB = criarFicha(familiaB, "Ficha do B " + System.nanoTime(), null);

        ResponseEntity<Map<String, Object>> response = client.delete()
                .uri(fichasPath(familiaB.alunoId()) + "/" + fichaDoB)
                .header(HttpHeaders.AUTHORIZATION, bearer(familiaA.token()))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("codigo", "ALUNO_NAO_ENCONTRADO");
        assertThat(fichaSaudeRepository.existsById(fichaDoB)).isTrue();
    }

    // ---------- Helpers (montam o cenario pelos endpoints reais) ----------

    /** Um responsavel autenticado + um aluno vinculado a ele. */
    private record Familia(String token, UUID alunoId) {
    }

    private Familia criarFamilia() {
        Usuario pai = criarUsuario(Perfil.RESPONSAVEL);
        UUID alunoId = criarAluno("Aluno da Familia " + System.nanoTime());

        ResponseEntity<AlunoResponsavelResponse> vinculo = client.post().uri("/api/alunos/" + alunoId + "/responsaveis")
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenDirecao))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new VincularResponsavelRequest(pai.getId()))
                .retrieve()
                .toEntity(AlunoResponsavelResponse.class);
        assertThat(vinculo.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        return new Familia(autenticar(pai), alunoId);
    }

    private String fichasPath(UUID alunoId) {
        return "/api/alunos/" + alunoId + "/fichas-saude";
    }

    /** Cadastra um aluno pelo endpoint real /api/alunos (como DIRECAO) e devolve o id gerado. */
    private UUID criarAluno(String nome) {
        ResponseEntity<AlunoResponse> response = client.post().uri("/api/alunos")
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenDirecao))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CadastrarAlunoRequest(nome, LocalDate.of(2022, 3, 15)))
                .retrieve()
                .toEntity(AlunoResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().id();
    }

    /** Cria uma ficha pelo endpoint real (como o RESPONSAVEL do aluno) e devolve o id do header Location. */
    private UUID criarFicha(Familia familia, String nome, String observacoes) {
        ResponseEntity<Void> response = client.post().uri(fichasPath(familia.alunoId()))
                .header(HttpHeaders.AUTHORIZATION, bearer(familia.token()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new FichaSaudeRequest(nome, observacoes))
                .retrieve()
                .toBodilessEntity();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return idDoLocation(response.getHeaders().getLocation().toString());
    }

    private UUID criarTurma() {
        String sufixo = String.valueOf(System.nanoTime());
        ResponseEntity<TurmaResponse> response = client.post().uri("/api/turmas")
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenDirecao))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CadastrarTurmaRequest("Turma " + sufixo, "S" + sufixo.substring(sufixo.length() - 6)))
                .retrieve()
                .toEntity(TurmaResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().id();
    }

    private void matricular(UUID turmaId, UUID alunoId) {
        ResponseEntity<MatriculaTurmaResponse> response = client.post().uri("/api/turmas/" + turmaId + "/matriculas")
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenDirecao))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new MatricularAlunoRequest(alunoId))
                .retrieve()
                .toEntity(MatriculaTurmaResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private void vincularProfessor(UUID turmaId, UUID professorId) {
        ResponseEntity<TurmaProfessorResponse> response = client.post().uri("/api/turmas/" + turmaId + "/professores")
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenDirecao))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new VincularProfessorTurmaRequest(professorId))
                .retrieve()
                .toEntity(TurmaProfessorResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private UUID idDoLocation(String location) {
        return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }

    @SuppressWarnings("unchecked")
    private List<Object> campos(ResponseEntity<Map<String, Object>> response) {
        return (List<Object>) response.getBody().get("campos");
    }
}
