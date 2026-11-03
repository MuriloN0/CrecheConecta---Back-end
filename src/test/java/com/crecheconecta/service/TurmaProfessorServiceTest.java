package com.crecheconecta.service;

import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.SituacaoCadastro;
import com.crecheconecta.entity.Turma;
import com.crecheconecta.entity.TurmaProfessor;
import com.crecheconecta.entity.Usuario;
import com.crecheconecta.exception.ProfessorNaoEncontradoException;
import com.crecheconecta.exception.RegraNegocioException;
import com.crecheconecta.exception.TurmaNaoEncontradaException;
import com.crecheconecta.exception.VinculoProfessorNaoEncontradoException;
import com.crecheconecta.repository.TurmaProfessorRepository;
import com.crecheconecta.repository.TurmaRepository;
import com.crecheconecta.repository.UsuarioRepository;
import com.crecheconecta.security.UsuarioAtual;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TurmaProfessorServiceTest {

    @Mock private TurmaProfessorRepository vinculos;
    @Mock private TurmaRepository turmas;
    @Mock private UsuarioRepository usuarios;
    @Mock private AuditoriaService auditoria;

    private TurmaProfessorService service;
    private Usuario diretor;
    private Usuario professor;
    private UsuarioAtual solicitante;
    private Turma turma;
    private TurmaProfessor vinculo;
    private Instant agora;

    @BeforeEach
    void preparar() {
        agora = Instant.parse("2026-11-02T12:00:00Z");

        service = new TurmaProfessorService(
                vinculos,
                turmas,
                usuarios,
                auditoria,
                Clock.fixed(agora, ZoneOffset.UTC)
        );

        diretor = new Usuario();
        diretor.setId(UUID.randomUUID());
        diretor.setPerfil(Perfil.DIRECAO);
        diretor.setAtivo(true);

        solicitante = new UsuarioAtual(
                diretor.getId(), Perfil.DIRECAO
        );

        professor = new Usuario();
        professor.setId(UUID.randomUUID());
        professor.setPerfil(Perfil.PROFESSOR);
        professor.setAtivo(true);
        professor.setSituacaoCadastro(SituacaoCadastro.APROVADO);

        turma = new Turma();
        turma.setId(UUID.randomUUID());
        turma.setAtivo(true);

        vinculo = new TurmaProfessor();
        vinculo.setId(UUID.randomUUID());
        vinculo.setTurmaId(turma.getId());
        vinculo.setProfessorId(professor.getId());
        vinculo.setCriadoEm(agora.minusSeconds(3600));
    }

    @Test
    void deveVincularProfessorAprovadoEAtivo() {
        prepararEntidades();

        var idCriado = UUID.randomUUID();

        doAnswer(invocation -> {
            TurmaProfessor salvo = invocation.getArgument(0);
            salvo.setId(idCriado);
            return salvo;
        }).when(vinculos).saveAndFlush(any(TurmaProfessor.class));

        var resposta = service.vincular(
                turma.getId(), professor.getId(), solicitante
        );

        assertEquals(idCriado, resposta.id());
        assertEquals(turma.getId(), resposta.turmaId());
        assertEquals(professor.getId(), resposta.professorId());
        assertEquals(agora, resposta.criadoEm());
        assertNull(resposta.encerradoEm());

        verify(auditoria).aposCommit(
                "TURMA_PROFESSOR_VINCULADO",
                diretor.getId(),
                idCriado
        );
    }

    @Test
    void deveImpedirVinculoAtualDuplicado() {
        prepararEntidades();

        when(vinculos.existsByTurmaIdAndProfessorIdAndEncerradoEmIsNull(
                turma.getId(), professor.getId()
        )).thenReturn(true);

        assertThrows(
                RegraNegocioException.class,
                () -> service.vincular(
                        turma.getId(), professor.getId(), solicitante
                )
        );

        verificarSemEscrita();
    }

    @Test
    void deveRejeitarProfessorInativo() {
        prepararEntidades();
        professor.setAtivo(false);

        assertThrows(
                RegraNegocioException.class,
                () -> service.vincular(
                        turma.getId(), professor.getId(), solicitante
                )
        );

        verificarSemEscrita();
    }

    @ParameterizedTest
    @EnumSource(
            value = SituacaoCadastro.class,
            names = {"PENDENTE_APROVACAO", "REJEITADO", "INATIVO"}
    )
    void deveRejeitarProfessorNaoAprovado(SituacaoCadastro situacao) {
        prepararEntidades();
        professor.setSituacaoCadastro(situacao);

        assertThrows(
                RegraNegocioException.class,
                () -> service.vincular(
                        turma.getId(), professor.getId(), solicitante
                )
        );

        verificarSemEscrita();
    }

    @Test
    void deveRejeitarTurmaInativa() {
        prepararEntidades();
        turma.setAtivo(false);

        assertThrows(
                RegraNegocioException.class,
                () -> service.vincular(
                        turma.getId(), professor.getId(), solicitante
                )
        );

        verificarSemEscrita();
    }

    @Test
    void deveRejeitarProfessorInexistente() {
        autorizar();

        assertThrows(
                ProfessorNaoEncontradoException.class,
                () -> service.vincular(
                        turma.getId(), professor.getId(), solicitante
                )
        );

        verificarSemEscrita();
    }

    @Test
    void naoDeveVincularUsuarioComOutroPerfil() {
        autorizar();
        professor.setPerfil(Perfil.RESPONSAVEL);

        when(usuarios.buscarPorIdComBloqueio(professor.getId()))
                .thenReturn(Optional.of(professor));

        assertThrows(
                ProfessorNaoEncontradoException.class,
                () -> service.vincular(
                        turma.getId(), professor.getId(), solicitante
                )
        );

        verifyNoInteractions(turmas);
        verificarSemEscrita();
    }

    @Test
    void deveRejeitarTurmaInexistente() {
        autorizar();

        when(usuarios.buscarPorIdComBloqueio(professor.getId()))
                .thenReturn(Optional.of(professor));

        assertThrows(
                TurmaNaoEncontradaException.class,
                () -> service.vincular(
                        turma.getId(), professor.getId(), solicitante
                )
        );

        verificarSemEscrita();
    }

    @Test
    void deveListarSomenteVinculosAtuais() {
        prepararListagem();

        when(vinculos.findByTurmaIdAndEncerradoEmIsNull(
                eq(turma.getId()), any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(vinculo)));

        var resposta = service.listar(
                turma.getId(), 0, 20, false, solicitante
        );

        assertEquals(vinculo.getId(), resposta.getContent().getFirst().id());
        assertNull(resposta.getContent().getFirst().encerradoEm());

        verify(vinculos, never()).findByTurmaId(
                any(UUID.class), any(Pageable.class)
        );
    }

    @Test
    void deveListarHistoricoQuandoSolicitado() {
        prepararListagem();
        vinculo.setEncerradoEm(agora);

        when(vinculos.findByTurmaId(
                eq(turma.getId()), any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(vinculo)));

        var resposta = service.listar(
                turma.getId(), 0, 20, true, solicitante
        );

        assertEquals(
                agora, resposta.getContent().getFirst().encerradoEm()
        );

        verify(vinculos, never()).findByTurmaIdAndEncerradoEmIsNull(
                any(UUID.class), any(Pageable.class)
        );
    }

    @ParameterizedTest
    @CsvSource({"-1,20", "0,0", "0,-1", "0,101"})
    void deveRejeitarPaginacaoInvalida(int pagina, int tamanho) {
        autorizar();

        assertThrows(
                RegraNegocioException.class,
                () -> service.listar(
                        turma.getId(), pagina, tamanho, false, solicitante
                )
        );

        verifyNoInteractions(vinculos);
    }

    @Test
    void deveRejeitarListagemDeTurmaInexistente() {
        autorizar();

        assertThrows(
                TurmaNaoEncontradaException.class,
                () -> service.listar(
                        turma.getId(), 0, 20, false, solicitante
                )
        );
    }

    @Test
    void deveEncerrarVinculoPreservandoHistorico() {
        prepararEncerramento();

        var criadoEm = vinculo.getCriadoEm();

        var resposta = service.encerrar(
                turma.getId(), vinculo.getId(), solicitante
        );

        assertEquals(agora, resposta.encerradoEm());
        assertEquals(criadoEm, resposta.criadoEm());
        assertEquals(professor.getId(), resposta.professorId());

        verify(vinculos).flush();
        verify(vinculos, never()).delete(any(TurmaProfessor.class));

        verify(auditoria).aposCommit(
                "TURMA_PROFESSOR_DESVINCULADO",
                diretor.getId(),
                vinculo.getId()
        );
    }

    @Test
    void deveRejeitarVinculoJaEncerrado() {
        prepararEncerramento();
        vinculo.setEncerradoEm(agora.minusSeconds(60));

        assertThrows(
                RegraNegocioException.class,
                () -> service.encerrar(
                        turma.getId(), vinculo.getId(), solicitante
                )
        );

        assertEquals(agora.minusSeconds(60), vinculo.getEncerradoEm());
        verificarSemEscrita();
    }

    @Test
    void deveRejeitarVinculoInexistenteOuDeOutraTurma() {
        autorizar();

        assertThrows(
                VinculoProfessorNaoEncontradoException.class,
                () -> service.encerrar(
                        turma.getId(), vinculo.getId(), solicitante
                )
        );

        verifyNoInteractions(turmas);
        verificarSemEscrita();
    }

    @Test
    void deveNegarSolicitanteNaoAutenticado() {
        assertThrows(
                AccessDeniedException.class,
                () -> service.vincular(
                        turma.getId(), professor.getId(), null
                )
        );

        verifyNoInteractions(usuarios, turmas, vinculos, auditoria);
    }

    @ParameterizedTest
    @EnumSource(
            value = Perfil.class,
            names = {"PROFESSOR", "RESPONSAVEL"}
    )
    void deveNegarOutrosPerfis(Perfil perfil) {
        var outro = new UsuarioAtual(UUID.randomUUID(), perfil);

        assertThrows(
                AccessDeniedException.class,
                () -> service.vincular(
                        turma.getId(), professor.getId(), outro
                )
        );

        verifyNoInteractions(usuarios, turmas, vinculos, auditoria);
    }

    @Test
    void deveNegarDiretorInativo() {
        diretor.setAtivo(false);
        autorizar();

        assertThrows(
                AccessDeniedException.class,
                () -> service.encerrar(
                        turma.getId(), vinculo.getId(), solicitante
                )
        );

        verifyNoInteractions(vinculos, turmas);
    }

    @Test
    void deveConferirPerfilDoDiretorNoBanco() {
        diretor.setPerfil(Perfil.PROFESSOR);
        autorizar();

        assertThrows(
                AccessDeniedException.class,
                () -> service.listar(
                        turma.getId(), 0, 20, false, solicitante
                )
        );

        verifyNoInteractions(vinculos, turmas);
    }

    private void autorizar() {
        when(usuarios.findById(diretor.getId()))
                .thenReturn(Optional.of(diretor));
    }

    private void prepararEntidades() {
        autorizar();

        when(usuarios.buscarPorIdComBloqueio(professor.getId()))
                .thenReturn(Optional.of(professor));

        when(turmas.buscarPorIdComBloqueio(turma.getId()))
                .thenReturn(Optional.of(turma));
    }

    private void prepararListagem() {
        autorizar();

        when(turmas.existsById(turma.getId())).thenReturn(true);
    }

    private void prepararEncerramento() {
        prepararEntidades();

        when(vinculos.buscarProfessorId(
                vinculo.getId(), turma.getId()
        )).thenReturn(Optional.of(professor.getId()));

        when(vinculos.findByIdAndTurmaId(
                vinculo.getId(), turma.getId()
        )).thenReturn(Optional.of(vinculo));
    }

    private void verificarSemEscrita() {
        verify(vinculos, never()).saveAndFlush(any(TurmaProfessor.class));
        verify(vinculos, never()).flush();
        verifyNoInteractions(auditoria);
    }
}