package com.crecheconecta.controller.atividades;

import com.crecheconecta.controller.base.IntegracaoTestBase;
import com.crecheconecta.dto.AtividadeResponseDTO;
import com.crecheconecta.dto.AtualizarAtividadeRequestDTO;
import com.crecheconecta.dto.NovaAtividadeRequestDTO;
import com.crecheconecta.dto.UsuarioLogadoResponse;
import com.crecheconecta.entity.AtividadeEntity;
import com.crecheconecta.entity.Perfil;
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

class AtividadeControllerIntegrationTest extends IntegracaoTestBase {

    private static final String BASE_PATH = "/api/atividades";

    @Autowired
    private AtividadeRepository atividadeRepository;

    private String token;

    @BeforeEach
    void autenticarProfessor() {
        token = autenticar(criarUsuario(Perfil.PROFESSOR));
    }

    @Test
    void criar_comDadosValidos_deveRetornar201() {
        long ts = System.nanoTime();
        UUID turmaId = UUID.randomUUID();
        UUID professorId = UUID.randomUUID();
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
        UUID turmaId = UUID.randomUUID();
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
        UUID turmaId = UUID.randomUUID();
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
        UUID turmaId = UUID.randomUUID();
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
        UUID turmaId = UUID.randomUUID();
        UUID outraTurmaId = UUID.randomUUID();
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
        UUID turmaId = UUID.randomUUID();
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
        UUID turmaId = UUID.randomUUID();
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
        UUID id = criarAtividade(UUID.randomUUID(), buildNovaAtividade(TipoAtividade.DIA, "Do dia " + ts, null));
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
        UUID id = criarAtividade(UUID.randomUUID(),
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
        UUID turmaId = UUID.randomUUID();
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

    private NovaAtividadeRequestDTO buildNovaAtividade(TipoAtividade tipo, String titulo, LocalDate prazo) {
        return new NovaAtividadeRequestDTO(UUID.randomUUID(), tipo, titulo, "Descricao de teste", prazo);
    }

    private UUID criarAtividade(UUID turmaId, NovaAtividadeRequestDTO request) {
        ResponseEntity<Void> response = client.post().uri(BASE_PATH)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("turmaUUID", turmaId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();

        String location = response.getHeaders().getLocation().toString();
        return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }
}
