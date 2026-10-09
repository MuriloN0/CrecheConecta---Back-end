package com.crecheconecta.service.atividades;

import com.crecheconecta.dto.AtualizarAtividadeRequestDTO;
import com.crecheconecta.dto.NovaAtividadeRequestDTO;
import com.crecheconecta.entity.AtividadeEntity;
import com.crecheconecta.exception.AtividadeNaoEncontradaException;
import com.crecheconecta.exception.RegraNegocioException;
import com.crecheconecta.model.TipoAtividade;
import com.crecheconecta.repository.AtividadeRepository;
import com.crecheconecta.service.AtividadeService;
import com.crecheconecta.service.AtividadePrazoValidation;
import com.crecheconecta.service.AtividadeTituloValidation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AtividadeServiceTest {

    @Mock
    private AtividadeRepository atividadeRepository;

    private AtividadeService atividadeService;

    @BeforeEach
    void setUp() {
        atividadeService = new AtividadeService(
                atividadeRepository,
                new AtividadeTituloValidation(),
                new AtividadePrazoValidation()
        );
    }

    @Test
    void criaAtividadeDia() {
        // Arrange
        UUID turmaId = UUID.randomUUID();
        UUID professorId = UUID.randomUUID();
        NovaAtividadeRequestDTO request = new NovaAtividadeRequestDTO(
                professorId,
                TipoAtividade.DIA,
                "  Pintura  ",
                "Com tinta guache",
                null
        );
        when(atividadeRepository.save(any(AtividadeEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        UUID id = atividadeService.criar(turmaId, request);

        // Assert
        ArgumentCaptor<AtividadeEntity> captor = ArgumentCaptor.forClass(AtividadeEntity.class);
        verify(atividadeRepository).save(captor.capture());
        AtividadeEntity salva = captor.getValue();
        assertEquals(id, salva.getId());
        assertEquals(turmaId, salva.getTurmaId());
        assertEquals("Pintura", salva.getTitulo());
        assertEquals(TipoAtividade.DIA, salva.getTipo());
    }

    @Test
    void casaSemPrazoNaoSalva() {
        // Arrange
        UUID turmaId = UUID.randomUUID();
        NovaAtividadeRequestDTO request = new NovaAtividadeRequestDTO(
                UUID.randomUUID(),
                TipoAtividade.CASA,
                "Lição de casa",
                null,
                null
        );

        // Act
        RegraNegocioException ex = assertThrows(
                RegraNegocioException.class,
                () -> atividadeService.criar(turmaId, request)
        );

        // Assert
        assertEquals(
                "Prazo de conclusão é obrigatório para atividade de casa",
                ex.getMessage()
        );
        verify(atividadeRepository, never()).save(any());
    }

    @Test
    void deletarInexistenteFalha() {
        // Arrange
        UUID id = UUID.randomUUID();
        when(atividadeRepository.existsById(id)).thenReturn(false);

        // Act
        AtividadeNaoEncontradaException ex = assertThrows(
                AtividadeNaoEncontradaException.class,
                () -> atividadeService.deletar(id)
        );

        // Assert
        assertEquals("Atividade não encontrada.", ex.getMessage());
        verify(atividadeRepository, never()).deleteById(id);
    }

    @Test
    void listaPorTurmaETipo() {
        // Arrange
        UUID turmaId = UUID.randomUUID();
        when(atividadeRepository.findByTurmaIdAndTipo(turmaId, TipoAtividade.CASA))
                .thenReturn(List.of());

        // Act
        List<AtividadeEntity> resultado =
                atividadeService.listar(turmaId, TipoAtividade.CASA);

        // Assert
        assertEquals(0, resultado.size());
        verify(atividadeRepository).findByTurmaIdAndTipo(turmaId, TipoAtividade.CASA);
    }

    @Test
    void atualizaSoDescricao() {
        // Arrange
        UUID id = UUID.randomUUID();
        AtividadeEntity existente = new AtividadeEntity();
        existente.setId(id);
        existente.setTurmaId(UUID.randomUUID());
        existente.setProfessorId(UUID.randomUUID());
        existente.setTipo(TipoAtividade.DIA);
        existente.setTitulo("Titulo original");
        existente.setDescricao("Antiga");
        existente.setPrazoConclusao(null);
        when(atividadeRepository.findById(id)).thenReturn(Optional.of(existente));
        when(atividadeRepository.save(any(AtividadeEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        atividadeService.atualizar(
                id,
                new AtualizarAtividadeRequestDTO(null, null, "Nova descricao", null)
        );

        // Assert
        ArgumentCaptor<AtividadeEntity> captor = ArgumentCaptor.forClass(AtividadeEntity.class);
        verify(atividadeRepository).save(captor.capture());
        assertEquals("Nova descricao", captor.getValue().getDescricao());
        assertEquals("Titulo original", captor.getValue().getTitulo());
        assertEquals(TipoAtividade.DIA, captor.getValue().getTipo());
    }
}
