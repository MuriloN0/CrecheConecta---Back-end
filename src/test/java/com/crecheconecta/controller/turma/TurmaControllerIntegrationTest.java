package com.crecheconecta.controller.turma;

import com.crecheconecta.controller.base.IntegracaoTestBase;
import com.crecheconecta.dto.AtualizarTurmaRequest;
import com.crecheconecta.dto.CadastrarTurmaRequest;
import com.crecheconecta.dto.InativarTurmaRequest;
import com.crecheconecta.dto.TurmaResponse;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.Turma;
import com.crecheconecta.repository.TurmaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes de integracao do TurmaController (rotas restritas ao perfil DIRECAO).
 *
 * Fluxo real exercitado em cada teste:
 * RestClient -> HTTP real -> AutenticacaoTokenFilter/SecurityFilterChain -> TurmaController
 * -> TurmaService -> TurmaRepository -> H2 em memoria
 *
 * Token obtido pelos endpoints reais de login (IntegracaoTestBase). Unico mock: EmailService (API Resend).
 * Isolamento: cada teste cria suas proprias turmas com nome unico via System.nanoTime().
 */
class TurmaControllerIntegrationTest extends IntegracaoTestBase {

    private static final String BASE_PATH = "/api/turmas";

    @Autowired
    private TurmaRepository turmaRepository; // usado so para conferir persistencia

    private String tokenDirecao;

    @BeforeEach
    void autenticarDirecao() {
        tokenDirecao = autenticar(criarUsuario(Perfil.DIRECAO));
    }

    // ---------- POST /api/turmas ----------

    @Test
    void cadastrar_comDadosValidos_deveRetornar201ComLocationCorpoEPersistir() {
        long ts = System.nanoTime();
        CadastrarTurmaRequest request = new CadastrarTurmaRequest("  Maternal I " + ts + "  ", "  Sala 3 ");

        ResponseEntity<TurmaResponse> response = client.post().uri(BASE_PATH)
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenDirecao))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toEntity(TurmaResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        TurmaResponse body = response.getBody();
        assertThat(body.id()).isNotNull();
        assertThat(body.nome()).isEqualTo("Maternal I " + ts);
        assertThat(body.numeroSala()).isEqualTo("Sala 3");
        assertThat(body.ativo()).isTrue();
        assertThat(body.versao()).isZero();
        assertThat(response.getHeaders().getLocation()).hasToString(BASE_PATH + "/" + body.id());
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");

        // Checa persistencia: a turma foi gravada com os textos normalizados
        Turma salva = turmaRepository.findById(body.id()).orElseThrow();
        assertThat(salva.getNome()).isEqualTo("Maternal I " + ts);
        assertThat(salva.getNumeroSala()).isEqualTo("Sala 3");
        assertThat(salva.isAtivo()).isTrue();
    }

    @Test
    void cadastrar_comNumeroSalaAcimaDe20Caracteres_deveRetornar400ComCampoNumeroSala() {
        long ts = System.nanoTime();
        String nome = "Turma Sala Longa " + ts;

        ResponseEntity<Map<String, Object>> response = client.post().uri(BASE_PATH)
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenDirecao))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CadastrarTurmaRequest(nome, "S".repeat(21)))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("codigo", "DADOS_INVALIDOS");
        assertThat(campos(response)).containsExactly("numeroSala");
        assertThat(turmaRepository.findAll())
                .extracting(Turma::getNome)
                .doesNotContain(nome);
    }

    @Test
    void cadastrar_comoProfessor_deveRetornar403ENaoPersistir() {
        String tokenProfessor = autenticar(criarUsuario(Perfil.PROFESSOR));
        String nome = "Turma do Professor " + System.nanoTime();

        ResponseEntity<Map<String, Object>> response = client.post().uri(BASE_PATH)
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenProfessor))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CadastrarTurmaRequest(nome, "Sala 9"))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("codigo", "ACESSO_NEGADO");
        assertThat(turmaRepository.findAll())
                .extracting(Turma::getNome)
                .doesNotContain(nome);
    }

    // ---------- GET /api/turmas ----------

    @Test
    void listar_filtrandoInativas_deveRetornar200ComTurmaInativadaESemTurmaAtiva() {
        long ts = System.nanoTime();
        TurmaResponse ativa = criarTurma("Jardim Ativa " + ts, "Sala 1");
        TurmaResponse inativa = criarTurma("Jardim Inativa " + ts, "Sala 2");
        inativarTurma(inativa.id(), inativa.versao());

        ResponseEntity<Map<String, Object>> response = client.get()
                .uri(BASE_PATH + "?ativo=false&tamanho=100")
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenDirecao))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> conteudo = conteudo(response);
        assertThat(conteudo)
                .extracting(turma -> turma.get("id"))
                .contains(inativa.id().toString())
                .doesNotContain(ativa.id().toString());
        assertThat(conteudo)
                .extracting(turma -> turma.get("ativo"))
                .containsOnly(false);
    }

    @Test
    void listar_comTamanhoDePaginaAcimaDe100_deveRetornar400ComMensagem() {
        ResponseEntity<Map<String, Object>> response = client.get()
                .uri(BASE_PATH + "?tamanho=101")
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenDirecao))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("codigo", "REGRA_NEGOCIO");
        assertThat(response.getBody().get("mensagem"))
                .isEqualTo("Informe página a partir de zero e tamanho entre 1 e 100.");
    }

    // ---------- GET /api/turmas/{turmaId} ----------

    @Test
    void visualizar_comIdExistente_deveRetornar200ComTodosOsCampos() {
        long ts = System.nanoTime();
        TurmaResponse criada = criarTurma("Bercario " + ts, "Sala 5");

        ResponseEntity<TurmaResponse> response = client.get().uri(BASE_PATH + "/" + criada.id())
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenDirecao))
                .retrieve()
                .toEntity(TurmaResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        TurmaResponse body = response.getBody();
        assertThat(body.id()).isEqualTo(criada.id());
        assertThat(body.nome()).isEqualTo("Bercario " + ts);
        assertThat(body.numeroSala()).isEqualTo("Sala 5");
        assertThat(body.ativo()).isTrue();
        assertThat(body.versao()).isZero();
    }

    @Test
    void visualizar_comIdInexistente_deveRetornar404ComMensagem() {
        ResponseEntity<Map<String, Object>> response = client.get().uri(BASE_PATH + "/" + UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenDirecao))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("codigo", "TURMA_NAO_ENCONTRADA");
        assertThat(response.getBody().get("mensagem")).isEqualTo("Turma não encontrada.");
    }

    // ---------- PUT /api/turmas/{turmaId} ----------

    @Test
    void atualizar_comVersaoAtual_deveRetornar200EGravarDadosNovosComVersaoIncrementada() {
        long ts = System.nanoTime();
        TurmaResponse criada = criarTurma("Nome antigo " + ts, "Sala 1");

        ResponseEntity<TurmaResponse> response = client.put().uri(BASE_PATH + "/" + criada.id())
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenDirecao))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new AtualizarTurmaRequest("Nome novo " + ts, "Sala 7", 0L))
                .retrieve()
                .toEntity(TurmaResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().id()).isEqualTo(criada.id());
        assertThat(response.getBody().nome()).isEqualTo("Nome novo " + ts);
        assertThat(response.getBody().numeroSala()).isEqualTo("Sala 7");
        assertThat(response.getBody().versao()).isEqualTo(1L);

        // Checa persistencia: nome e sala novos gravados no banco
        Turma salva = turmaRepository.findById(criada.id()).orElseThrow();
        assertThat(salva.getNome()).isEqualTo("Nome novo " + ts);
        assertThat(salva.getNumeroSala()).isEqualTo("Sala 7");
        assertThat(salva.getVersao()).isEqualTo(1L);
    }

    @Test
    void atualizar_comVersaoDesatualizada_deveRetornar409EManterDadosAntigos() {
        long ts = System.nanoTime();
        TurmaResponse criada = criarTurma("Versao inicial " + ts, "Sala 1");

        ResponseEntity<Map<String, Object>> response = client.put().uri(BASE_PATH + "/" + criada.id())
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenDirecao))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new AtualizarTurmaRequest("Edicao concorrente " + ts, "Sala 2", 5L))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("codigo", "CONFLITO_VERSAO");
        assertThat(response.getBody().get("mensagem"))
                .isEqualTo("Cadastro alterado por outra pessoa. Recarregue os dados.");
        assertThat(turmaRepository.findById(criada.id()))
                .get()
                .extracting(Turma::getNome)
                .isEqualTo("Versao inicial " + ts);
    }

    @Test
    void atualizar_comIdInexistente_deveRetornar404() {
        ResponseEntity<Map<String, Object>> response = client.put().uri(BASE_PATH + "/" + UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenDirecao))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new AtualizarTurmaRequest("Fantasma " + System.nanoTime(), "Sala 0", 0L))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("codigo", "TURMA_NAO_ENCONTRADA");
    }

    // ---------- POST /api/turmas/{turmaId}/inativar ----------

    @Test
    void inativar_turmaAtiva_deveRetornar200ComAtivoFalseEGravarNoBanco() {
        TurmaResponse criada = criarTurma("Para inativar " + System.nanoTime(), "Sala 4");

        ResponseEntity<TurmaResponse> response = client.post().uri(BASE_PATH + "/" + criada.id() + "/inativar")
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenDirecao))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new InativarTurmaRequest(criada.versao()))
                .retrieve()
                .toEntity(TurmaResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().id()).isEqualTo(criada.id());
        assertThat(response.getBody().ativo()).isFalse();
        assertThat(response.getBody().versao()).isEqualTo(1L);

        // Checa persistencia: a turma continua existindo, mas inativa
        assertThat(turmaRepository.findById(criada.id()))
                .get()
                .extracting(Turma::isAtivo)
                .isEqualTo(false);
    }

    @Test
    void inativar_turmaJaInativa_deveRetornar400ComMensagemDaRegra() {
        TurmaResponse criada = criarTurma("Ja inativa " + System.nanoTime(), "Sala 6");
        TurmaResponse inativada = inativarTurma(criada.id(), criada.versao());

        ResponseEntity<Map<String, Object>> response = client.post().uri(BASE_PATH + "/" + criada.id() + "/inativar")
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenDirecao))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new InativarTurmaRequest(inativada.versao()))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("codigo", "REGRA_NEGOCIO");
        assertThat(response.getBody().get("mensagem")).isEqualTo("A turma já está inativa.");
    }

    // ---------- Helpers ----------

    /** Cria uma turma pelo endpoint real (como DIRECAO) e devolve o corpo da resposta. */
    private TurmaResponse criarTurma(String nome, String numeroSala) {
        ResponseEntity<TurmaResponse> response = client.post().uri(BASE_PATH)
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenDirecao))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CadastrarTurmaRequest(nome, numeroSala))
                .retrieve()
                .toEntity(TurmaResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    /** Inativa uma turma pelo endpoint real e devolve o corpo da resposta. */
    private TurmaResponse inativarTurma(UUID turmaId, Long versao) {
        ResponseEntity<TurmaResponse> response = client.post().uri(BASE_PATH + "/" + turmaId + "/inativar")
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenDirecao))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new InativarTurmaRequest(versao))
                .retrieve()
                .toEntity(TurmaResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    @SuppressWarnings("unchecked")
    private List<Object> campos(ResponseEntity<Map<String, Object>> response) {
        return (List<Object>) response.getBody().get("campos");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> conteudo(ResponseEntity<Map<String, Object>> response) {
        return (List<Map<String, Object>>) response.getBody().get("content");
    }
}
