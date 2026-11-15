package com.crecheconecta.controller;

import com.crecheconecta.controller.base.IntegracaoTestBase;
import com.crecheconecta.dto.AtividadeResponseDTO;
import com.crecheconecta.dto.AtualizarAtividadeRequestDTO;
import com.crecheconecta.dto.CadastrarTurmaRequest;
import com.crecheconecta.dto.NovaAtividadeRequestDTO;
import com.crecheconecta.dto.TurmaProfessorResponse;
import com.crecheconecta.dto.TurmaResponse;
import com.crecheconecta.dto.UsuarioLogadoResponse;
import com.crecheconecta.dto.VincularProfessorTurmaRequest;
import com.crecheconecta.entity.AtividadeEntity;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.Usuario;
import com.crecheconecta.model.TipoAtividade;
import com.crecheconecta.repository.AtividadeRepository;
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
 * Regra de acesso (AtividadeAcessoService): só o PROFESSOR cria, edita e exclui; a turma precisa ser
 * uma em que ele leciona; e o professorId da atividade precisa ser o do próprio usuário logado.
 * Por isso o cenário cria duas turmas reais (pela direção) e vincula o professor logado a elas.
 */
class AtividadeControllerIntegrationTest extends IntegracaoTestBase {

    private static final String BASE_PATH = "/api/atividades";

    @Autowired
    private AtividadeRepository atividadeRepository;

    private String token;
    private String tokenDirecao;
    private UUID professorId;
    private UUID turmaId;
    private UUID outraTurmaId;

    @BeforeEach
    void prepararProfessorETurmas() {
        Usuario professor = criarUsuario(Perfil.PROFESSOR);
        professorId = professor.getId();
        token = autenticar(professor);
        tokenDirecao = autenticar(criarUsuario(Perfil.DIRECAO));

        turmaId = criarTurma();
        outraTurmaId = criarTurma();
        vincularProfessor(turmaId, professorId);
        vincularProfessor(outraTurmaId, professorId);
    }

    @Test
    void criar_comDadosValidos_deveRetornar201() {
        long ts = System.nanoTime();
        NovaAtividadeRequestDTO request = new NovaAtividadeRequestDTO(
                professorId,
                TipoAtividade.CASA,
                "  Pintura com guache " + ts + "  ",
                "Pintar o desenho da familia",
                LocalDate.of(2026, 12, 15)
        );

        ResponseEntity<Void> response = client.post().uri(BASE_PATH)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("turmaUUID", turmaId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String location = response.getHeaders().getLocation().toString();
        assertThat(location).startsWith(BASE_PATH + "/");

        UUID id = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
        AtividadeEntity salva = atividadeRepository.findById(id).orElseThrow();
        assertThat(salva.getTurmaId()).isEqualTo(turmaId);
        assertThat(salva.getProfessorId()).isEqualTo(professorId);
        assertThat(salva.getTipo()).isEqualTo(TipoAtividade.CASA);
        assertThat(salva.getTitulo()).isEqualTo("Pintura com guache " + ts);
        assertThat(salva.getDescricao()).isEqualTo("Pintar o desenho da familia");
        assertThat(salva.getPrazoConclusao()).isEqualTo(LocalDate.of(2026, 12, 15));
        assertThat(salva.getDataCriacao()).isNotNull();
    }

    @Test
    void criar_semTitulo_deveRetornar400() {
        NovaAtividadeRequestDTO request = buildNovaAtividade(TipoAtividade.DIA, "   ", null);

        ResponseEntity<Map<String, Object>> response = client.post().uri(BASE_PATH)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("turmaUUID", turmaId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("codigo")).isEqualTo("DADOS_INVALIDOS");
        assertThat(response.getBody().get("mensagem")).isEqualTo("Confira os campos enviados.");
        assertThat(response.getBody().get("campos")).isEqualTo(List.of("titulo"));
        assertThat(atividadeRepository.findByTurmaId(turmaId)).isEmpty();
    }

    @Test
    void criar_atividadeDeCasaSemPrazo_deveRetornar400() {
        NovaAtividadeRequestDTO request =
                buildNovaAtividade(TipoAtividade.CASA, "Leitura " + System.nanoTime(), null);

        ResponseEntity<Map<String, Object>> response = client.post().uri(BASE_PATH)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("turmaUUID", turmaId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("codigo")).isEqualTo("REGRA_NEGOCIO");
        assertThat(response.getBody().get("mensagem"))
                .isEqualTo("Prazo de conclusão é obrigatório para atividade de casa");
        assertThat(atividadeRepository.findByTurmaId(turmaId)).isEmpty();
    }

    @Test
    void criar_semLogin_deveRetornar401() {
        NovaAtividadeRequestDTO request =
                buildNovaAtividade(TipoAtividade.DIA, "Sem login " + System.nanoTime(), null);

        ResponseEntity<Map<String, Object>> response = client.post().uri(BASE_PATH)
                .header("turmaUUID", turmaId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("codigo")).isEqualTo("NAO_AUTENTICADO");
        assertThat(atividadeRepository.findByTurmaId(turmaId)).isEmpty();
    }

    @Test
    void listar_porTurmaETipo_deveTrazerSoAsAtividadesFiltradas() {
        long ts = System.nanoTime();
        UUID atividadeDoDia = criarAtividade(turmaId, buildNovaAtividade(TipoAtividade.DIA, "Roda " + ts, null));
        UUID atividadeDeCasa = criarAtividade(turmaId,
                buildNovaAtividade(TipoAtividade.CASA, "Casa " + ts, LocalDate.of(2026, 12, 1)));
        UUID atividadeOutraTurma = criarAtividade(outraTurmaId,
                buildNovaAtividade(TipoAtividade.DIA, "Outra turma " + ts, null));

        ResponseEntity<List<AtividadeResponseDTO>> response = client.get()
                .uri(BASE_PATH + "?tipo=DIA")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("turmaUUID", turmaId.toString())
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .extracting(AtividadeResponseDTO::id)
                .containsExactly(atividadeDoDia)
                .doesNotContain(atividadeDeCasa, atividadeOutraTurma);
        assertThat(response.getBody().get(0).titulo()).isEqualTo("Roda " + ts);
        assertThat(response.getBody().get(0).turmaId()).isEqualTo(turmaId);
    }

    @Test
    void listar_comTipoInvalido_deveRetornar400() {
        ResponseEntity<Map<String, Object>> response = client.get()
                .uri(BASE_PATH + "?tipo=NOITE")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("codigo")).isEqualTo("PARAMETRO_INVALIDO");
        assertThat(response.getBody().get("mensagem"))
                .isEqualTo("Confira os identificadores e os parâmetros enviados.");
    }

    @Test
    void buscarPorId_comIdExistente_deveRetornar200() {
        long ts = System.nanoTime();
        NovaAtividadeRequestDTO request =
                buildNovaAtividade(TipoAtividade.CASA, "Massinha " + ts, LocalDate.of(2026, 11, 20));
        UUID id = criarAtividade(turmaId, request);

        ResponseEntity<AtividadeResponseDTO> response = client.get().uri(BASE_PATH + "/" + id)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .retrieve()
                .toEntity(AtividadeResponseDTO.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        AtividadeResponseDTO body = response.getBody();
        assertThat(body.id()).isEqualTo(id);
        assertThat(body.turmaId()).isEqualTo(turmaId);
        assertThat(body.professorId()).isEqualTo(request.professorId());
        assertThat(body.tipo()).isEqualTo(TipoAtividade.CASA);
        assertThat(body.titulo()).isEqualTo("Massinha " + ts);
        assertThat(body.descricao()).isEqualTo("Descricao de teste");
        assertThat(body.prazoConclusao()).isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(body.dataCriacao()).isNotNull();
    }

    @Test
    void buscarPorId_comIdInexistente_deveRetornar404() {
        ResponseEntity<Map<String, Object>> response = client.get().uri(BASE_PATH + "/" + UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("codigo")).isEqualTo("ATIVIDADE_NAO_ENCONTRADA");
        assertThat(response.getBody().get("mensagem")).isEqualTo("Atividade não encontrada.");
    }

    @Test
    void atualizar_soOTitulo_deveManterOsOutrosCampos() {
        long ts = System.nanoTime();
        UUID id = criarAtividade(turmaId, buildNovaAtividade(TipoAtividade.DIA, "Original " + ts, null));
        AtualizarAtividadeRequestDTO request =
                new AtualizarAtividadeRequestDTO(null, "  Atualizado " + ts + " ", null, null);

        ResponseEntity<Void> response = client.patch().uri(BASE_PATH + "/" + id)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        AtividadeEntity salva = atividadeRepository.findById(id).orElseThrow();
        assertThat(salva.getTitulo()).isEqualTo("Atualizado " + ts);
        assertThat(salva.getTipo()).isEqualTo(TipoAtividade.DIA);
        assertThat(salva.getDescricao()).isEqualTo("Descricao de teste");
        assertThat(salva.getTurmaId()).isEqualTo(turmaId);
    }

    @Test
    void atualizar_paraCasaSemPrazo_deveRetornar400() {
        long ts = System.nanoTime();
        UUID id = criarAtividade(turmaId, buildNovaAtividade(TipoAtividade.DIA, "Do dia " + ts, null));
        AtualizarAtividadeRequestDTO request =
                new AtualizarAtividadeRequestDTO(TipoAtividade.CASA, null, null, null);

        ResponseEntity<Map<String, Object>> response = client.patch().uri(BASE_PATH + "/" + id)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("codigo")).isEqualTo("REGRA_NEGOCIO");
        assertThat(response.getBody().get("mensagem"))
                .isEqualTo("Prazo de conclusão é obrigatório para atividade de casa");
        assertThat(atividadeRepository.findById(id).orElseThrow().getTipo()).isEqualTo(TipoAtividade.DIA);
    }

    @Test
    void atualizar_comIdInexistente_deveRetornar404() {
        AtualizarAtividadeRequestDTO request =
                new AtualizarAtividadeRequestDTO(null, "Fantasma " + System.nanoTime(), null, null);

        ResponseEntity<Map<String, Object>> response = client.patch().uri(BASE_PATH + "/" + UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("codigo")).isEqualTo("ATIVIDADE_NAO_ENCONTRADA");
    }

    @Test
    void deletar_comIdExistente_deveRemoverAtividade() {
        UUID id = criarAtividade(turmaId,
                buildNovaAtividade(TipoAtividade.DIA, "Para remover " + System.nanoTime(), null));

        ResponseEntity<Void> response = client.delete().uri(BASE_PATH + "/" + id)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .retrieve()
                .toBodilessEntity();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Void> busca = client.get().uri(BASE_PATH + "/" + id)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .retrieve()
                .toBodilessEntity();
        assertThat(busca.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(atividadeRepository.existsById(id)).isFalse();
    }

    @Test
    void deletar_comIdInexistente_deveRetornar404() {
        ResponseEntity<Map<String, Object>> response = client.delete().uri(BASE_PATH + "/" + UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("codigo")).isEqualTo("ATIVIDADE_NAO_ENCONTRADA");
    }

    @Test
    void fluxo_professorCriaAtividade_deveAparecerNaListaDaTurma() {
        long ts = System.nanoTime();
        UsuarioLogadoResponse professor = client.get().uri("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .retrieve()
                .toEntity(UsuarioLogadoResponse.class)
                .getBody();
        NovaAtividadeRequestDTO request = new NovaAtividadeRequestDTO(
                professor.id(), TipoAtividade.DIA, "Musicalizacao " + ts, "Cantar em roda", null);

        UUID id = criarAtividade(turmaId, request);
        ResponseEntity<List<AtividadeResponseDTO>> response = client.get().uri(BASE_PATH)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("turmaUUID", turmaId.toString())
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        AtividadeResponseDTO atividade = response.getBody().get(0);
        assertThat(atividade.id()).isEqualTo(id);
        assertThat(atividade.professorId()).isEqualTo(professor.id());
        assertThat(atividade.turmaId()).isEqualTo(turmaId);
        assertThat(atividade.titulo()).isEqualTo("Musicalizacao " + ts);
    }

    // ---------- Helpers ----------

    /** A atividade sempre pertence ao professor logado (a API recusa professorId de outra pessoa). */
    private NovaAtividadeRequestDTO buildNovaAtividade(TipoAtividade tipo, String titulo, LocalDate prazo) {
        return new NovaAtividadeRequestDTO(professorId, tipo, titulo, "Descricao de teste", prazo);
    }

    private UUID criarAtividade(UUID turma, NovaAtividadeRequestDTO request) {
        ResponseEntity<Void> response = client.post().uri(BASE_PATH)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("turmaUUID", turma.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String location = response.getHeaders().getLocation().toString();
        return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }

    /** Cria a turma pelo endpoint real da direção. */
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

    /** Vincula o professor à turma pelo endpoint real da direção. */
    private void vincularProfessor(UUID turma, UUID professor) {
        ResponseEntity<TurmaProfessorResponse> response = client.post().uri("/api/turmas/" + turma + "/professores")
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenDirecao))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new VincularProfessorTurmaRequest(professor))
                .retrieve()
                .toEntity(TurmaProfessorResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
}
