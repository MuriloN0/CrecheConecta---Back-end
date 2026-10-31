package com.crecheconecta.controller.atividades;

import com.crecheconecta.controller.AtividadeController;
import com.crecheconecta.dto.AtividadeResponseDTO;
import com.crecheconecta.dto.NovaAtividadeRequestDTO;
import com.crecheconecta.entity.AtividadeEntity;
import com.crecheconecta.exception.AtividadeNaoEncontradaException;
import com.crecheconecta.model.TipoAtividade;
import com.crecheconecta.service.AtividadeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AtividadeControllerTest {

    @Mock
    private AtividadeService atividadeService;

    @InjectMocks
    private AtividadeController atividadeController;

    private UUID turmaId;
    private UUID atividadeId;

    @BeforeEach
    void setUp() {
        turmaId = UUID.randomUUID();
        atividadeId = UUID.randomUUID();
    }

    @Test
    void postRetorna201() {
        // Arrange
        NovaAtividadeRequestDTO request = new NovaAtividadeRequestDTO(
                UUID.randomUUID(),
                TipoAtividade.DIA,
                "Rodinha",
                null,
                null
        );
        when(atividadeService.criar(turmaId, request)).thenReturn(atividadeId);

        // Act
        ResponseEntity<Void> resposta = atividadeController.criar(turmaId, request);

        // Assert
        assertEquals(HttpStatus.CREATED, resposta.getStatusCode());
        assertEquals(
                URI.create("/api/atividades/" + atividadeId),
                resposta.getHeaders().getLocation()
        );
        verify(atividadeService).criar(turmaId, request);
    }

    @Test
    void deleteRetorna204() {
        // Arrange
        UUID id = atividadeId;

        // Act
        ResponseEntity<Void> resposta = atividadeController.deletar(id);

        // Assert
        assertEquals(HttpStatus.NO_CONTENT, resposta.getStatusCode());
        verify(atividadeService).deletar(atividadeId);
    }

    @Test
    void getPorIdRetorna200() {
        // Arrange
        AtividadeEntity entity = new AtividadeEntity();
        entity.setId(atividadeId);
        entity.setTurmaId(turmaId);
        entity.setProfessorId(UUID.randomUUID());
        entity.setTipo(TipoAtividade.CASA);
        entity.setTitulo("Tarefa");
        entity.setDescricao("Descricao");
        entity.setDataCriacao(Instant.parse("2026-01-01T12:00:00Z"));
        entity.setPrazoConclusao(LocalDate.of(2026, 2, 1));
        when(atividadeService.buscarPorId(atividadeId)).thenReturn(entity);

        // Act
        ResponseEntity<AtividadeResponseDTO> resposta =
                atividadeController.buscarPorId(atividadeId);

        // Assert
        assertEquals(HttpStatus.OK, resposta.getStatusCode());
        assertNotNull(resposta.getBody());
        assertEquals("Tarefa", resposta.getBody().titulo());
        assertEquals(TipoAtividade.CASA, resposta.getBody().tipo());
    }

    @Test
    void getInexistentePropagaErro() {
        // Arrange
        when(atividadeService.buscarPorId(atividadeId))
                .thenThrow(new AtividadeNaoEncontradaException());

        // Act & Assert
        assertThrows(
                AtividadeNaoEncontradaException.class,
                () -> atividadeController.buscarPorId(atividadeId)
        );
    }

    @Test
    void listarComFiltro() {
        // Arrange
        AtividadeEntity entity = new AtividadeEntity();
        entity.setId(atividadeId);
        entity.setTurmaId(turmaId);
        entity.setProfessorId(UUID.randomUUID());
        entity.setTipo(TipoAtividade.DIA);
        entity.setTitulo("Brincadeira");
        entity.setDataCriacao(Instant.now());
        when(atividadeService.listar(turmaId, TipoAtividade.DIA))
                .thenReturn(List.of(entity));

        // Act
        ResponseEntity<List<AtividadeResponseDTO>> resposta =
                atividadeController.listarTodas(turmaId, TipoAtividade.DIA);

        // Assert
        assertEquals(HttpStatus.OK, resposta.getStatusCode());
        assertNotNull(resposta.getBody());
        assertEquals(1, resposta.getBody().size());
        assertEquals("Brincadeira", resposta.getBody().getFirst().titulo());
        verify(atividadeService).listar(turmaId, TipoAtividade.DIA);
    }
}
