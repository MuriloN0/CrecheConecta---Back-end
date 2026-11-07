package com.crecheconecta.service;

import com.crecheconecta.dto.AtualizarAtividadeRequestDTO;
import com.crecheconecta.dto.NovaAtividadeRequestDTO;
import com.crecheconecta.entity.AtividadeEntity;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.exception.AtividadeNaoEncontradaException;
import com.crecheconecta.exception.RegraNegocioException;
import com.crecheconecta.model.TipoAtividade;
import com.crecheconecta.repository.AtividadeRepository;
import com.crecheconecta.security.UsuarioAtual;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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

    @Mock
    private AtividadeAcessoService acesso;

    @Mock
    private AuditoriaService auditoria;

    private AtividadeService atividadeService;
    private UsuarioAtual professor;

    @BeforeEach
    void setUp() {
        professor = new UsuarioAtual(UUID.randomUUID(), Perfil.PROFESSOR);
        when(acesso.usuarioAtual()).thenReturn(professor);

        atividadeService = new AtividadeService(
                atividadeRepository,
                new AtividadeTituloValidation(),
                new AtividadePrazoValidation(),
                acesso,
                auditoria,
                Clock.fixed(Instant.parse("2026-11-02T12:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void criaAtividadeDia() {
        UUID turmaId = UUID.randomUUID();
        var request = new NovaAtividadeRequestDTO(
                professor.id(),
                TipoAtividade.DIA,
                "  Pintura  ",
                "Com tinta guache",
                null
        );

        UUID id = atividadeService.criar(turmaId, request);

        var captor = ArgumentCaptor.forClass(AtividadeEntity.class);
        verify(atividadeRepository).save(captor.capture());

        AtividadeEntity salva = captor.getValue();
        assertEquals(id, salva.getId());
        assertEquals(turmaId, salva.getTurmaId());
        assertEquals(professor.id(), salva.getProfessorId());
        assertEquals("Pintura", salva.getTitulo());
        assertEquals(TipoAtividade.DIA, salva.getTipo());
    }

    @Test
    void casaSemPrazoNaoSalva() {
        UUID turmaId = UUID.randomUUID();
        var request = new NovaAtividadeRequestDTO(
                professor.id(),
                TipoAtividade.CASA,
                "Lição de casa",
                null,
                null
        );

        var erro = assertThrows(
                RegraNegocioException.class,
                () -> atividadeService.criar(turmaId, request)
        );

        assertEquals(
                "Prazo de conclusão é obrigatório para atividade de casa",
                erro.getMessage()
        );
        verify(atividadeRepository, never()).save(any(AtividadeEntity.class));
    }

    @Test
    void deletarInexistenteFalha() {
        UUID id = UUID.randomUUID();
        when(atividadeRepository.findById(id)).thenReturn(Optional.empty());

        var erro = assertThrows(
                AtividadeNaoEncontradaException.class,
                () -> atividadeService.deletar(id)
        );

        assertEquals("Atividade não encontrada.", erro.getMessage());
        verify(atividadeRepository, never()).delete(any(AtividadeEntity.class));
    }

    @Test
    void listaPorTurmaETipo() {
        UUID turmaId = UUID.randomUUID();
        var atividade = new AtividadeEntity();
        atividade.setTurmaId(turmaId);
        atividade.setTipo(TipoAtividade.CASA);

        when(atividadeRepository.findByTurmaIdInAndTipoOrderByDataCriacaoDescIdAsc(
                List.of(turmaId), TipoAtividade.CASA
        )).thenReturn(List.of(atividade));

        List<AtividadeEntity> resultado =
                atividadeService.listar(turmaId, TipoAtividade.CASA);

        assertEquals(List.of(atividade), resultado);
        verify(acesso).validarTurma(turmaId, professor);
    }

    @Test
    void atualizaSoDescricao() {
        UUID id = UUID.randomUUID();
        var existente = new AtividadeEntity();
        existente.setId(id);
        existente.setTurmaId(UUID.randomUUID());
        existente.setProfessorId(professor.id());
        existente.setTipo(TipoAtividade.DIA);
        existente.setTitulo("Título original");
        existente.setDescricao("Antiga");

        when(atividadeRepository.findById(id)).thenReturn(Optional.of(existente));

        atividadeService.atualizar(
                id,
                new AtualizarAtividadeRequestDTO(null, null, "Nova descrição", null)
        );

        var captor = ArgumentCaptor.forClass(AtividadeEntity.class);
        verify(atividadeRepository).save(captor.capture());

        AtividadeEntity atualizada = captor.getValue();
        assertEquals("Nova descrição", atualizada.getDescricao());
        assertEquals("Título original", atualizada.getTitulo());
        assertEquals(TipoAtividade.DIA, atualizada.getTipo());
    }
}