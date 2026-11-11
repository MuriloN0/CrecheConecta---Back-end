package com.crecheconecta.controller.fichasaude;

import com.crecheconecta.controller.base.IntegracaoTestBase;
import com.crecheconecta.dto.AlunoResponse;
import com.crecheconecta.dto.AtualizarFichaSaudeRequest;
import com.crecheconecta.dto.CadastrarAlunoRequest;
import com.crecheconecta.dto.FichaSaudeRequest;
import com.crecheconecta.dto.FichaSaudeResponse;
import com.crecheconecta.dto.FichaSaudeResumoResponse;
import com.crecheconecta.entity.FichaSaude;
import com.crecheconecta.entity.Perfil;
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
 * Testes de integracao do FichaSaudeController (rotas restritas ao perfil DIRECAO).
 *
 * Fluxo real exercitado em cada teste:
 * RestClient -> HTTP real -> AutenticacaoTokenFilter/SecurityFilterChain -> FichaSaudeController
 * -> FichaSaudeService (+ CriptografiaService, AlunoRepository) -> FichaSaudeRepository -> H2 em memoria
 *
 * Token obtido pelos endpoints reais de login (IntegracaoTestBase). Unico mock: EmailService (API Resend).
 * Isolamento: cada teste cadastra seus proprios alunos pelo endpoint /api/alunos e usa nomes com System.nanoTime().
 */
class FichaSaudeControllerIntegrationTest extends IntegracaoTestBase {

    @Autowired
    private FichaSaudeRepository fichaSaudeRepository; // usado so para conferir persistencia

    private String tokenDirecao;

    @BeforeEach
    void autenticarDirecao() {
        tokenDirecao = autenticar(criarUsuario(Perfil.DIRECAO));
    }

    // ---------- POST /api/alunos/{alunoId}/fichas-saude ----------

    @Test
    void cadastrar_comoDirecao_deveRetornar201ComLocationEPersistirObservacoesCifradas() {
        long ts = System.nanoTime();
        UUID alunoId = criarAluno("Aluno Ficha " + ts);
        FichaSaudeRequest request = new FichaSaudeRequest("  Alergias " + ts + "  ", "Alergia a amendoim");

        ResponseEntity<Void> response = client.post().uri(fichasPath(alunoId))
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenDirecao))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String location = response.getHeaders().getLocation().toString();
        assertThat(location).startsWith(fichasPath(alunoId) + "/");
        UUID fichaId = idDoLocation(location);

        // Checa persistencia: ficha do aluno, nome sem espacos e observacoes gravadas cifradas
        FichaSaude salva = fichaSaudeRepository.findById(fichaId).orElseThrow();
        assertThat(salva.getAlunoId()).isEqualTo(alunoId);
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
        assertThat(response.getBody().get("detail"))
                .isEqualTo("Você não possui permissão para esta operação.");
        assertThat(fichaSaudeRepository.findByAlunoId(alunoId)).isEmpty();
    }

    @Test
    void cadastrar_comNomeEmBranco_deveRetornar400ComCampoNome() {
        UUID alunoId = criarAluno("Aluno Nome Branco " + System.nanoTime());

        ResponseEntity<Map<String, Object>> response = client.post().uri(fichasPath(alunoId))
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenDirecao))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new FichaSaudeRequest("   ", "Observacao qualquer"))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("codigo", "DADOS_INVALIDOS");
        assertThat(campos(response)).containsExactly("nome");
        assertThat(fichaSaudeRepository.findByAlunoId(alunoId)).isEmpty();
    }

    @Test
    void cadastrar_paraAlunoInexistente_deveRetornar404ComMensagemENaoPersistir() {
        UUID alunoInexistente = UUID.randomUUID();

        ResponseEntity<Map<String, Object>> response = client.post().uri(fichasPath(alunoInexistente))
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenDirecao))
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
    void listar_aposCadastrarAlunoEFichas_deveRetornar200SomenteComFichasDaqueleAluno() {
        long ts = System.nanoTime();
        UUID alunoA = criarAluno("Aluno A " + ts);
        UUID alunoB = criarAluno("Aluno B " + ts);
        UUID fichaA1 = criarFicha(alunoA, "Alergias " + ts, "Lactose");
        UUID fichaA2 = criarFicha(alunoA, "Medicamentos " + ts, "Antialergico");
        UUID fichaB = criarFicha(alunoB, "Vacinas " + ts, null);

        ResponseEntity<List<FichaSaudeResumoResponse>> response = client.get().uri(fichasPath(alunoA))
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenDirecao))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
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

    // ---------- GET /api/alunos/{alunoId}/fichas-saude/{fichaId} ----------

    @Test
    void visualizar_comFichaExistente_deveRetornar200ComObservacoesDecifradas() {
        long ts = System.nanoTime();
        UUID alunoId = criarAluno("Aluno Visualizar " + ts);
        UUID fichaId = criarFicha(alunoId, "Restricoes alimentares " + ts, "Nao pode comer gluten");

        ResponseEntity<FichaSaudeResponse> response = client.get().uri(fichasPath(alunoId) + "/" + fichaId)
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenDirecao))
                .retrieve()
                .toEntity(FichaSaudeResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        FichaSaudeResponse body = response.getBody();
        assertThat(body.id()).isEqualTo(fichaId);
        assertThat(body.alunoId()).isEqualTo(alunoId);
        assertThat(body.nome()).isEqualTo("Restricoes alimentares " + ts);
        assertThat(body.observacoes()).isEqualTo("Nao pode comer gluten");
        assertThat(body.versao()).isZero();
        assertThat(body.dataCriacao()).isNotNull();
        assertThat(body.dataAtualizacao()).isNotNull();
        assertThat(body.anexos()).isEmpty();
    }

    @Test
    void visualizar_comFichaInexistente_deveRetornar404ComMensagem() {
        UUID alunoId = criarAluno("Aluno Ficha Inexistente " + System.nanoTime());

        ResponseEntity<Map<String, Object>> response = client.get()
                .uri(fichasPath(alunoId) + "/" + UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenDirecao))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("codigo", "FICHA_SAUDE_NAO_ENCONTRADA");
        assertThat(response.getBody().get("mensagem")).isEqualTo("Ficha de saúde não encontrada.");
    }

    @Test
    void visualizar_fichaPeloCaminhoDeOutroAluno_deveRetornar404() {
        long ts = System.nanoTime();
        UUID donoDaFicha = criarAluno("Dono da Ficha " + ts);
        UUID outroAluno = criarAluno("Outro Aluno " + ts);
        UUID fichaId = criarFicha(donoDaFicha, "Ficha privada " + ts, "Asma");

        ResponseEntity<Map<String, Object>> response = client.get()
                .uri(fichasPath(outroAluno) + "/" + fichaId)
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenDirecao))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("codigo", "FICHA_SAUDE_NAO_ENCONTRADA");
    }

    // ---------- PUT /api/alunos/{alunoId}/fichas-saude/{fichaId} ----------

    @Test
    void atualizar_comVersaoAtual_deveRetornar200EGravarDadosNovosComVersaoIncrementada() {
        long ts = System.nanoTime();
        UUID alunoId = criarAluno("Aluno Atualizar " + ts);
        UUID fichaId = criarFicha(alunoId, "Nome antigo " + ts, "Observacao antiga");
        AtualizarFichaSaudeRequest request =
                new AtualizarFichaSaudeRequest("Nome novo " + ts, "Observacao nova", 0L);

        ResponseEntity<FichaSaudeResponse> response = client.put().uri(fichasPath(alunoId) + "/" + fichaId)
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenDirecao))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toEntity(FichaSaudeResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().id()).isEqualTo(fichaId);
        assertThat(response.getBody().nome()).isEqualTo("Nome novo " + ts);
        assertThat(response.getBody().observacoes()).isEqualTo("Observacao nova");

        // Efeito observavel: nova consulta traz os dados novos e a versao incrementada
        FichaSaudeResponse depois = client.get().uri(fichasPath(alunoId) + "/" + fichaId)
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenDirecao))
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
        UUID alunoId = criarAluno("Aluno Conflito " + ts);
        UUID fichaId = criarFicha(alunoId, "Versao inicial " + ts, null);
        AtualizarFichaSaudeRequest request =
                new AtualizarFichaSaudeRequest("Edicao concorrente " + ts, null, 7L);

        ResponseEntity<Map<String, Object>> response = client.put().uri(fichasPath(alunoId) + "/" + fichaId)
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenDirecao))
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
        long ts = System.nanoTime();
        UUID alunoId = criarAluno("Aluno Sem Versao " + ts);
        UUID fichaId = criarFicha(alunoId, "Sem versao " + ts, null);

        ResponseEntity<Map<String, Object>> response = client.put().uri(fichasPath(alunoId) + "/" + fichaId)
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenDirecao))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new AtualizarFichaSaudeRequest("Nome qualquer", null, null))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("codigo", "DADOS_INVALIDOS");
        assertThat(campos(response)).containsExactly("versao");
    }

    // ---------- DELETE /api/alunos/{alunoId}/fichas-saude/{fichaId} ----------

    @Test
    void excluir_comFichaExistente_deveRetornar204EFichaDeixaDeExistir() {
        long ts = System.nanoTime();
        UUID alunoId = criarAluno("Aluno Excluir " + ts);
        UUID fichaId = criarFicha(alunoId, "Para excluir " + ts, "Temporaria");

        ResponseEntity<Void> response = client.delete().uri(fichasPath(alunoId) + "/" + fichaId)
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenDirecao))
                .retrieve()
                .toBodilessEntity();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Efeito observavel: GET seguinte retorna 404 e o registro sumiu do banco
        ResponseEntity<Void> busca = client.get().uri(fichasPath(alunoId) + "/" + fichaId)
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenDirecao))
                .retrieve()
                .toBodilessEntity();
        assertThat(busca.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(fichaSaudeRepository.existsById(fichaId)).isFalse();
    }

    @Test
    void excluir_comFichaInexistente_deveRetornar404ComMensagem() {
        UUID alunoId = criarAluno("Aluno Excluir Inexistente " + System.nanoTime());

        ResponseEntity<Map<String, Object>> response = client.delete()
                .uri(fichasPath(alunoId) + "/" + UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenDirecao))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("codigo", "FICHA_SAUDE_NAO_ENCONTRADA");
        assertThat(response.getBody().get("mensagem")).isEqualTo("Ficha de saúde não encontrada.");
    }

    // ---------- Helpers ----------

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

    /** Cria uma ficha pelo endpoint real (como DIRECAO) e devolve o id do header Location. */
    private UUID criarFicha(UUID alunoId, String nome, String observacoes) {
        ResponseEntity<Void> response = client.post().uri(fichasPath(alunoId))
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenDirecao))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new FichaSaudeRequest(nome, observacoes))
                .retrieve()
                .toBodilessEntity();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return idDoLocation(response.getHeaders().getLocation().toString());
    }

    private UUID idDoLocation(String location) {
        return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }

    @SuppressWarnings("unchecked")
    private List<Object> campos(ResponseEntity<Map<String, Object>> response) {
        return (List<Object>) response.getBody().get("campos");
    }
}
