package com.crecheconecta.controller.informacoesrelevantes;

import com.crecheconecta.controller.base.IntegracaoTestBase;
import com.crecheconecta.dto.AlunoResponsavelResponse;
import com.crecheconecta.dto.AtualizarInformacaoRelevanteRequest;
import com.crecheconecta.dto.CadastrarTurmaRequest;
import com.crecheconecta.dto.InformacaoRelevanteRequest;
import com.crecheconecta.dto.InformacaoRelevanteResponse;
import com.crecheconecta.dto.InformacaoRelevanteResumoResponse;
import com.crecheconecta.dto.MatriculaTurmaResponse;
import com.crecheconecta.dto.MatricularAlunoRequest;
import com.crecheconecta.dto.TurmaProfessorResponse;
import com.crecheconecta.dto.TurmaResponse;
import com.crecheconecta.dto.VincularProfessorTurmaRequest;
import com.crecheconecta.dto.VincularResponsavelRequest;
import com.crecheconecta.entity.Aluno;
import com.crecheconecta.entity.InformacaoRelevante;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.Usuario;
import com.crecheconecta.repository.AlunoRepository;
import com.crecheconecta.repository.InformacaoRelevanteRepository;
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
 * Testes de integracao do InformacaoRelevanteController (Informacoes Relevantes).
 *
 * Regra de acesso:
 * - RESPONSAVEL com vinculo atual com o aluno: escreve e le;
 * - DIRECAO: le as informacoes de qualquer aluno (nao escreve);
 * - PROFESSOR: le apenas de alunos matriculados numa turma em que leciona (nao escreve).
 *
 * Fluxo real exercitado em cada teste:
 * RestClient -> HTTP real -> AutenticacaoTokenFilter/SecurityFilterChain -> InformacaoRelevanteController
 * -> InformacaoRelevanteService (+ VinculoResponsavelService, CriptografiaService) -> InformacaoRelevanteRepository -> H2 em memoria
 *
 * O aluno e gravado direto no banco; vinculo, turma, matricula e professor sao montados pelos endpoints reais da direcao.
 * Unico mock: EmailService (API Resend).
 */
class InformacaoRelevanteControllerIntegrationTest extends IntegracaoTestBase {

    private static final String SEM_PERMISSAO = "Você não possui permissão para esta operação.";

    @Autowired
    private InformacaoRelevanteRepository informacaoRelevanteRepository; // usado so para conferir persistencia

    @Autowired
    private AlunoRepository alunoRepository; // aluno é só cenário: o POST /api/alunos saiu da API

    private String tokenDirecao;

    @BeforeEach
    void autenticarDirecao() {
        tokenDirecao = autenticar(criarUsuario(Perfil.DIRECAO));
    }

    // ---------- POST /api/alunos/{alunoId}/informacoes-relevantes ----------

    @Test
    void cadastrar_comoResponsavelDoAluno_deveRetornar201ComLocationEPersistirObservacoesCifradas() {
        long ts = System.nanoTime();
        Familia familia = criarFamilia();
        InformacaoRelevanteRequest request = new InformacaoRelevanteRequest("  Alergias " + ts + "  ", "Alergia a amendoim");

        ResponseEntity<Void> response = client.post().uri(rotaInformacoes(familia.alunoId()))
                .header(HttpHeaders.AUTHORIZATION, bearer(familia.token()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String location = response.getHeaders().getLocation().toString();
        assertThat(location).startsWith(rotaInformacoes(familia.alunoId()) + "/");
        UUID informacaoId = idDoLocation(location);

        // Checa persistencia: informacao do aluno, nome sem espacos e observacoes gravadas cifradas
        InformacaoRelevante salva = informacaoRelevanteRepository.findById(informacaoId).orElseThrow();
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

        ResponseEntity<Map<String, Object>> response = client.post().uri(rotaInformacoes(alunoId))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new InformacaoRelevanteRequest("Sem token", null))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("codigo", "NAO_AUTENTICADO");
        assertThat(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
        assertThat(informacaoRelevanteRepository.findByAlunoId(alunoId)).isEmpty();
    }

    @Test
    void cadastrar_comoProfessor_deveRetornar403ENaoPersistir() {
        String tokenProfessor = autenticar(criarUsuario(Perfil.PROFESSOR));
        UUID alunoId = criarAluno("Aluno Professor " + System.nanoTime());

        ResponseEntity<Map<String, Object>> response = client.post().uri(rotaInformacoes(alunoId))
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenProfessor))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new InformacaoRelevanteRequest("Tentativa do professor", null))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("codigo", "ACESSO_NEGADO");
        assertThat(response.getBody().get("detail")).isEqualTo(SEM_PERMISSAO);
        assertThat(informacaoRelevanteRepository.findByAlunoId(alunoId)).isEmpty();
    }

    @Test
    void cadastrar_comoDirecao_deveRetornar403ENaoPersistir() {
        UUID alunoId = criarAluno("Aluno Direcao Escreve " + System.nanoTime());

        ResponseEntity<Map<String, Object>> response = client.post().uri(rotaInformacoes(alunoId))
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenDirecao))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new InformacaoRelevanteRequest("Tentativa da direcao", null))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("codigo", "ACESSO_NEGADO");
        assertThat(informacaoRelevanteRepository.findByAlunoId(alunoId)).isEmpty();
    }

    @Test
    void cadastrar_paraFilhoDeOutroResponsavel_deveRetornar404ENaoPersistir() {
        Familia familiaA = criarFamilia();
        Familia familiaB = criarFamilia();

        ResponseEntity<Map<String, Object>> response = client.post().uri(rotaInformacoes(familiaB.alunoId()))
                .header(HttpHeaders.AUTHORIZATION, bearer(familiaA.token()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new InformacaoRelevanteRequest("Invasao", "Nao deveria gravar"))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("codigo", "ALUNO_NAO_ENCONTRADO");
        assertThat(informacaoRelevanteRepository.findByAlunoId(familiaB.alunoId())).isEmpty();
    }

    @Test
    void cadastrar_comNomeEmBranco_deveRetornar400ComCampoNome() {
        Familia familia = criarFamilia();

        ResponseEntity<Map<String, Object>> response = client.post().uri(rotaInformacoes(familia.alunoId()))
                .header(HttpHeaders.AUTHORIZATION, bearer(familia.token()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new InformacaoRelevanteRequest("   ", "Observacao qualquer"))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("codigo", "DADOS_INVALIDOS");
        assertThat(campos(response)).containsExactly("nome");
        assertThat(informacaoRelevanteRepository.findByAlunoId(familia.alunoId())).isEmpty();
    }

    @Test
    void cadastrar_paraAlunoInexistente_deveRetornar404ComMensagemENaoPersistir() {
        Familia familia = criarFamilia();
        UUID alunoInexistente = UUID.randomUUID();

        ResponseEntity<Map<String, Object>> response = client.post().uri(rotaInformacoes(alunoInexistente))
                .header(HttpHeaders.AUTHORIZATION, bearer(familia.token()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new InformacaoRelevanteRequest("Ficha orfa", null))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("codigo", "ALUNO_NAO_ENCONTRADO");
        assertThat(response.getBody().get("mensagem")).isEqualTo("Aluno não encontrado.");
        assertThat(informacaoRelevanteRepository.findByAlunoId(alunoInexistente)).isEmpty();
    }

    // ---------- GET /api/alunos/{alunoId}/informacoes-relevantes ----------

    @Test
    void listar_comoResponsavel_deveRetornar200SomenteComInformacoesDoProprioFilho() {
        long ts = System.nanoTime();
        Familia familiaA = criarFamilia();
        Familia familiaB = criarFamilia();
        UUID informacaoA1 = criarInformacao(familiaA, "Alergias " + ts, "Lactose");
        UUID informacaoA2 = criarInformacao(familiaA, "Medicamentos " + ts, "Antialergico");
        UUID informacaoB = criarInformacao(familiaB, "Vacinas " + ts, null);

        ResponseEntity<List<InformacaoRelevanteResumoResponse>> response = client.get().uri(rotaInformacoes(familiaA.alunoId()))
                .header(HttpHeaders.AUTHORIZATION, bearer(familiaA.token()))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        assertThat(response.getBody())
                .extracting(InformacaoRelevanteResumoResponse::id)
                .containsExactlyInAnyOrder(informacaoA1, informacaoA2)
                .doesNotContain(informacaoB);
        assertThat(response.getBody())
                .extracting(InformacaoRelevanteResumoResponse::nome)
                .containsExactlyInAnyOrder("Alergias " + ts, "Medicamentos " + ts);
    }

    @Test
    void listar_informacoesDoFilhoDeOutroResponsavel_deveRetornar404() {
        Familia familiaA = criarFamilia();
        Familia familiaB = criarFamilia();
        criarInformacao(familiaB, "Ficha do B " + System.nanoTime(), "Asma");

        ResponseEntity<Map<String, Object>> response = client.get().uri(rotaInformacoes(familiaB.alunoId()))
                .header(HttpHeaders.AUTHORIZATION, bearer(familiaA.token()))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("codigo", "ALUNO_NAO_ENCONTRADO");
    }

    @Test
    void listar_comoDirecao_deveRetornar200ComInformacoesDoAluno() {
        long ts = System.nanoTime();
        Familia familia = criarFamilia();
        UUID informacaoId = criarInformacao(familia, "Ficha vista pela direcao " + ts, "Bronquite");

        ResponseEntity<List<InformacaoRelevanteResumoResponse>> response = client.get().uri(rotaInformacoes(familia.alunoId()))
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenDirecao))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .extracting(InformacaoRelevanteResumoResponse::id)
                .containsExactly(informacaoId);
    }

    @Test
    void listar_comoDirecaoParaAlunoInexistente_deveRetornar404() {
        ResponseEntity<Map<String, Object>> response = client.get().uri(rotaInformacoes(UUID.randomUUID()))
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
        UUID informacaoId = criarInformacao(familia, "Ficha vista pelo professor " + ts, "Epilepsia");
        Usuario professor = criarUsuario(Perfil.PROFESSOR);
        UUID turmaId = criarTurma();
        matricular(turmaId, familia.alunoId());
        vincularProfessor(turmaId, professor.getId());

        ResponseEntity<List<InformacaoRelevanteResumoResponse>> response = client.get().uri(rotaInformacoes(familia.alunoId()))
                .header(HttpHeaders.AUTHORIZATION, bearer(autenticar(professor)))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .extracting(InformacaoRelevanteResumoResponse::id)
                .containsExactly(informacaoId);
    }

    @Test
    void listar_comoProfessorDeOutraTurma_deveRetornar404() {
        Familia familia = criarFamilia();
        criarInformacao(familia, "Ficha de outra turma " + System.nanoTime(), null);
        Usuario professor = criarUsuario(Perfil.PROFESSOR);
        UUID turmaDoAluno = criarTurma();
        UUID turmaDoProfessor = criarTurma();
        matricular(turmaDoAluno, familia.alunoId());
        vincularProfessor(turmaDoProfessor, professor.getId());

        ResponseEntity<Map<String, Object>> response = client.get().uri(rotaInformacoes(familia.alunoId()))
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

        ResponseEntity<Map<String, Object>> response = client.get().uri(rotaInformacoes(familia.alunoId()))
                .header(HttpHeaders.AUTHORIZATION, bearer(autenticar(professor)))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("codigo", "ALUNO_NAO_ENCONTRADO");
    }

    // ---------- GET /api/alunos/{alunoId}/informacoes-relevantes/{informacaoId} ----------

    @Test
    void visualizar_comInformacaoExistente_deveRetornar200ComObservacoesDecifradas() {
        long ts = System.nanoTime();
        Familia familia = criarFamilia();
        UUID informacaoId = criarInformacao(familia, "Restricoes alimentares " + ts, "Nao pode comer gluten");

        ResponseEntity<InformacaoRelevanteResponse> response = client.get().uri(rotaInformacoes(familia.alunoId()) + "/" + informacaoId)
                .header(HttpHeaders.AUTHORIZATION, bearer(familia.token()))
                .retrieve()
                .toEntity(InformacaoRelevanteResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        InformacaoRelevanteResponse body = response.getBody();
        assertThat(body.id()).isEqualTo(informacaoId);
        assertThat(body.alunoId()).isEqualTo(familia.alunoId());
        assertThat(body.nome()).isEqualTo("Restricoes alimentares " + ts);
        assertThat(body.observacoes()).isEqualTo("Nao pode comer gluten");
        assertThat(body.versao()).isZero();
        assertThat(body.dataCriacao()).isNotNull();
        assertThat(body.dataAtualizacao()).isNotNull();
    }

    @Test
    void visualizar_comoDirecao_deveRetornar200ComObservacoesDecifradas() {
        Familia familia = criarFamilia();
        UUID informacaoId = criarInformacao(familia, "Ficha detalhada " + System.nanoTime(), "Usa bombinha");

        ResponseEntity<InformacaoRelevanteResponse> response = client.get().uri(rotaInformacoes(familia.alunoId()) + "/" + informacaoId)
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenDirecao))
                .retrieve()
                .toEntity(InformacaoRelevanteResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().observacoes()).isEqualTo("Usa bombinha");
    }

    @Test
    void visualizar_comInformacaoInexistente_deveRetornar404ComMensagem() {
        Familia familia = criarFamilia();

        ResponseEntity<Map<String, Object>> response = client.get()
                .uri(rotaInformacoes(familia.alunoId()) + "/" + UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, bearer(familia.token()))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("codigo", "INFORMACAO_RELEVANTE_NAO_ENCONTRADA");
        assertThat(response.getBody().get("mensagem")).isEqualTo("Informação relevante não encontrada.");
    }

    @Test
    void visualizar_informacaoDeOutroAlunoPeloCaminhoDoProprioFilho_deveRetornar404() {
        Familia familiaA = criarFamilia();
        Familia familiaB = criarFamilia();
        UUID informacaoDoB = criarInformacao(familiaB, "Ficha privada " + System.nanoTime(), "Asma");

        // Pai A usa o ID do PROPRIO filho no caminho, mas o ID da informacao do filho do B
        ResponseEntity<Map<String, Object>> response = client.get()
                .uri(rotaInformacoes(familiaA.alunoId()) + "/" + informacaoDoB)
                .header(HttpHeaders.AUTHORIZATION, bearer(familiaA.token()))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("codigo", "INFORMACAO_RELEVANTE_NAO_ENCONTRADA");
    }

    // ---------- PUT /api/alunos/{alunoId}/informacoes-relevantes/{informacaoId} ----------

    @Test
    void atualizar_comVersaoAtual_deveRetornar200EGravarDadosNovosComVersaoIncrementada() {
        long ts = System.nanoTime();
        Familia familia = criarFamilia();
        UUID informacaoId = criarInformacao(familia, "Nome antigo " + ts, "Observacao antiga");
        AtualizarInformacaoRelevanteRequest request =
                new AtualizarInformacaoRelevanteRequest("Nome novo " + ts, "Observacao nova", 0L);

        ResponseEntity<InformacaoRelevanteResponse> response = client.put().uri(rotaInformacoes(familia.alunoId()) + "/" + informacaoId)
                .header(HttpHeaders.AUTHORIZATION, bearer(familia.token()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toEntity(InformacaoRelevanteResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().id()).isEqualTo(informacaoId);
        assertThat(response.getBody().nome()).isEqualTo("Nome novo " + ts);
        assertThat(response.getBody().observacoes()).isEqualTo("Observacao nova");
        // A propria resposta do PUT ja traz a versao nova (pronta para a proxima edicao)
        assertThat(response.getBody().versao()).isEqualTo(1L);

        // Efeito observavel: nova consulta traz os dados novos e a versao incrementada
        InformacaoRelevanteResponse depois = client.get().uri(rotaInformacoes(familia.alunoId()) + "/" + informacaoId)
                .header(HttpHeaders.AUTHORIZATION, bearer(familia.token()))
                .retrieve()
                .body(InformacaoRelevanteResponse.class);
        assertThat(depois.nome()).isEqualTo("Nome novo " + ts);
        assertThat(depois.observacoes()).isEqualTo("Observacao nova");
        assertThat(depois.versao()).isEqualTo(1L);
        assertThat(informacaoRelevanteRepository.findById(informacaoId))
                .get()
                .extracting(InformacaoRelevante::getNome)
                .isEqualTo("Nome novo " + ts);
    }

    @Test
    void atualizar_comVersaoDesatualizada_deveRetornar409EManterDadosAntigos() {
        long ts = System.nanoTime();
        Familia familia = criarFamilia();
        UUID informacaoId = criarInformacao(familia, "Versao inicial " + ts, null);
        AtualizarInformacaoRelevanteRequest request =
                new AtualizarInformacaoRelevanteRequest("Edicao concorrente " + ts, null, 7L);

        ResponseEntity<Map<String, Object>> response = client.put().uri(rotaInformacoes(familia.alunoId()) + "/" + informacaoId)
                .header(HttpHeaders.AUTHORIZATION, bearer(familia.token()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("codigo", "CONFLITO_VERSAO");
        assertThat(response.getBody().get("mensagem"))
                .isEqualTo("Cadastro alterado por outra pessoa. Recarregue os dados.");
        assertThat(informacaoRelevanteRepository.findById(informacaoId))
                .get()
                .extracting(InformacaoRelevante::getNome)
                .isEqualTo("Versao inicial " + ts);
    }

    @Test
    void atualizar_semVersao_deveRetornar400ComCampoVersao() {
        Familia familia = criarFamilia();
        UUID informacaoId = criarInformacao(familia, "Sem versao " + System.nanoTime(), null);

        ResponseEntity<Map<String, Object>> response = client.put().uri(rotaInformacoes(familia.alunoId()) + "/" + informacaoId)
                .header(HttpHeaders.AUTHORIZATION, bearer(familia.token()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new AtualizarInformacaoRelevanteRequest("Nome qualquer", null, null))
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
        UUID informacaoId = criarInformacao(familia, "Original " + ts, null);

        ResponseEntity<Map<String, Object>> response = client.put().uri(rotaInformacoes(familia.alunoId()) + "/" + informacaoId)
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenDirecao))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new AtualizarInformacaoRelevanteRequest("Alterado pela direcao", null, 0L))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("codigo", "ACESSO_NEGADO");
        assertThat(informacaoRelevanteRepository.findById(informacaoId))
                .get()
                .extracting(InformacaoRelevante::getNome)
                .isEqualTo("Original " + ts);
    }

    // ---------- DELETE /api/alunos/{alunoId}/informacoes-relevantes/{informacaoId} ----------

    @Test
    void excluir_comInformacaoExistente_deveRetornar204EInformacaoDeixaDeExistir() {
        Familia familia = criarFamilia();
        UUID informacaoId = criarInformacao(familia, "Para excluir " + System.nanoTime(), "Temporaria");

        ResponseEntity<Void> response = client.delete().uri(rotaInformacoes(familia.alunoId()) + "/" + informacaoId)
                .header(HttpHeaders.AUTHORIZATION, bearer(familia.token()))
                .retrieve()
                .toBodilessEntity();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Efeito observavel: GET seguinte retorna 404 e o registro sumiu do banco
        ResponseEntity<Void> busca = client.get().uri(rotaInformacoes(familia.alunoId()) + "/" + informacaoId)
                .header(HttpHeaders.AUTHORIZATION, bearer(familia.token()))
                .retrieve()
                .toBodilessEntity();
        assertThat(busca.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(informacaoRelevanteRepository.existsById(informacaoId)).isFalse();
    }

    @Test
    void excluir_comInformacaoInexistente_deveRetornar404ComMensagem() {
        Familia familia = criarFamilia();

        ResponseEntity<Map<String, Object>> response = client.delete()
                .uri(rotaInformacoes(familia.alunoId()) + "/" + UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, bearer(familia.token()))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("codigo", "INFORMACAO_RELEVANTE_NAO_ENCONTRADA");
        assertThat(response.getBody().get("mensagem")).isEqualTo("Informação relevante não encontrada.");
    }

    @Test
    void excluir_informacaoDoFilhoDeOutroResponsavel_deveRetornar404EManterInformacao() {
        Familia familiaA = criarFamilia();
        Familia familiaB = criarFamilia();
        UUID informacaoDoB = criarInformacao(familiaB, "Ficha do B " + System.nanoTime(), null);

        ResponseEntity<Map<String, Object>> response = client.delete()
                .uri(rotaInformacoes(familiaB.alunoId()) + "/" + informacaoDoB)
                .header(HttpHeaders.AUTHORIZATION, bearer(familiaA.token()))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("codigo", "ALUNO_NAO_ENCONTRADO");
        assertThat(informacaoRelevanteRepository.existsById(informacaoDoB)).isTrue();
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

    private String rotaInformacoes(UUID alunoId) {
        return "/api/alunos/" + alunoId + "/informacoes-relevantes";
    }

    /** Grava o aluno direto no banco: o cadastro direto saiu da API (o aluno nasce no convite do responsável). */
    private UUID criarAluno(String nome) {
        var aluno = new Aluno();
        aluno.setNome(nome);
        aluno.setDataNascimento(LocalDate.of(2022, 3, 15));
        aluno.setAtivo(true);
        return alunoRepository.saveAndFlush(aluno).getId();
    }

    /** Cria uma informacao pelo endpoint real (como o RESPONSAVEL do aluno) e devolve o id do header Location. */
    private UUID criarInformacao(Familia familia, String nome, String observacoes) {
        ResponseEntity<Void> response = client.post().uri(rotaInformacoes(familia.alunoId()))
                .header(HttpHeaders.AUTHORIZATION, bearer(familia.token()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new InformacaoRelevanteRequest(nome, observacoes))
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
