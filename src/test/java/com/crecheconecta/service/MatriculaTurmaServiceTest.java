package com.crecheconecta.service;

import com.crecheconecta.entity.Aluno;
import com.crecheconecta.entity.MatriculaTurma;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.Turma;
import com.crecheconecta.entity.Usuario;
import com.crecheconecta.exception.AlunoNaoEncontradoException;
import com.crecheconecta.exception.MatriculaNaoEncontradaException;
import com.crecheconecta.exception.RegraNegocioException;
import com.crecheconecta.exception.TurmaNaoEncontradaException;
import com.crecheconecta.repository.AlunoRepository;
import com.crecheconecta.repository.MatriculaTurmaRepository;
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
class MatriculaTurmaServiceTest {

    @Mock private MatriculaTurmaRepository matriculas;
    @Mock private AlunoRepository alunos;
    @Mock private TurmaRepository turmas;
    @Mock private UsuarioRepository usuarios;
    @Mock private AuditoriaService auditoria;

    private MatriculaTurmaService service;
    private Usuario diretor;
    private UsuarioAtual solicitante;
    private Aluno aluno;
    private Turma turma;
    private MatriculaTurma matricula;
    private Instant agora;

    @BeforeEach
    void preparar() {
        agora = Instant.parse("2026-11-02T12:00:00Z");

        service = new MatriculaTurmaService(
                matriculas,
                alunos,
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

        aluno = new Aluno();
        aluno.setId(UUID.randomUUID());
        aluno.setAtivo(true);

        turma = new Turma();
        turma.setId(UUID.randomUUID());
        turma.setAtivo(true);

        matricula = new MatriculaTurma();
        matricula.setId(UUID.randomUUID());
        matricula.setAlunoId(aluno.getId());
        matricula.setTurmaId(turma.getId());
        matricula.setCriadoEm(agora.minusSeconds(3600));
    }

    @Test
    void deveMatricularAlunoAtivoEmTurmaAtiva() {
        prepararEntidades();

        var idCriado = UUID.randomUUID();

        doAnswer(invocation -> {
            MatriculaTurma salva = invocation.getArgument(0);
            salva.setId(idCriado);
            return salva;
        }).when(matriculas).saveAndFlush(any(MatriculaTurma.class));

        var resposta = service.matricular(
                turma.getId(), aluno.getId(), solicitante
        );

        assertEquals(idCriado, resposta.id());
        assertEquals(aluno.getId(), resposta.alunoId());
        assertEquals(turma.getId(), resposta.turmaId());
        assertEquals(agora, resposta.criadoEm());
        assertNull(resposta.encerradoEm());

        verify(auditoria).aposCommit(
                "ALUNO_MATRICULADO", diretor.getId(), idCriado
        );
    }

    @Test
    void deveImpedirSegundaMatriculaAtual() {
        prepararEntidades();

        when(matriculas.existsByAlunoIdAndEncerradoEmIsNull(
                aluno.getId()
        )).thenReturn(true);

        assertThrows(
                RegraNegocioException.class,
                () -> service.matricular(
                        turma.getId(), aluno.getId(), solicitante
                )
        );

        verificarSemEscrita();
    }

    @Test
    void deveRejeitarAlunoInativo() {
        prepararEntidades();
        aluno.setAtivo(false);

        assertThrows(
                RegraNegocioException.class,
                () -> service.matricular(
                        turma.getId(), aluno.getId(), solicitante
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
                () -> service.matricular(
                        turma.getId(), aluno.getId(), solicitante
                )
        );

        verificarSemEscrita();
    }

    @Test
    void deveRejeitarAlunoInexistente() {
        autorizar();

        assertThrows(
                AlunoNaoEncontradoException.class,
                () -> service.matricular(
                        turma.getId(), aluno.getId(), solicitante
                )
        );

        verificarSemEscrita();
    }

    @Test
    void deveRejeitarTurmaInexistente() {
        autorizar();

        when(alunos.buscarPorIdComBloqueio(aluno.getId()))
                .thenReturn(Optional.of(aluno));

        assertThrows(
                TurmaNaoEncontradaException.class,
                () -> service.matricular(
                        turma.getId(), aluno.getId(), solicitante
                )
        );

        verificarSemEscrita();
    }

    @Test
    void deveListarSomenteMatriculasAtuais() {
        prepararListagem();

        when(matriculas.findByTurmaIdAndEncerradoEmIsNull(
                eq(turma.getId()), any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(matricula)));

        var resposta = service.listar(
                turma.getId(), 0, 20, false, solicitante
        );

        assertEquals(matricula.getId(), resposta.getContent().getFirst().id());
        assertNull(resposta.getContent().getFirst().encerradoEm());

        verify(matriculas, never()).findByTurmaId(
                any(UUID.class), any(Pageable.class)
        );
    }

    @Test
    void devePermitirListagemComHistorico() {
        prepararListagem();
        matricula.setEncerradoEm(agora);

        when(matriculas.findByTurmaId(
                eq(turma.getId()), any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(matricula)));

        var resposta = service.listar(
                turma.getId(), 0, 20, true, solicitante
        );

        assertEquals(
                agora, resposta.getContent().getFirst().encerradoEm()
        );

        verify(matriculas, never()).findByTurmaIdAndEncerradoEmIsNull(
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

        verifyNoInteractions(matriculas);
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
    void deveEncerrarPreservandoHistorico() {
        prepararEncerramento();

        var criadoEm = matricula.getCriadoEm();

        var resposta = service.encerrar(
                turma.getId(), matricula.getId(), solicitante
        );

        assertEquals(agora, resposta.encerradoEm());
        assertEquals(criadoEm, resposta.criadoEm());
        assertEquals(matricula.getId(), resposta.id());

        verify(matriculas).flush();
        verify(matriculas, never()).delete(any(MatriculaTurma.class));

        verify(auditoria).aposCommit(
                "MATRICULA_ENCERRADA",
                diretor.getId(),
                matricula.getId()
        );
    }

    @Test
    void deveRejeitarMatriculaJaEncerrada() {
        prepararEncerramento();
        matricula.setEncerradoEm(agora.minusSeconds(60));

        assertThrows(
                RegraNegocioException.class,
                () -> service.encerrar(
                        turma.getId(), matricula.getId(), solicitante
                )
        );

        assertEquals(
                agora.minusSeconds(60), matricula.getEncerradoEm()
        );

        verificarSemEscrita();
    }

    @Test
    void deveRejeitarMatriculaInexistenteOuDeOutraTurma() {
        autorizar();

        assertThrows(
                MatriculaNaoEncontradaException.class,
                () -> service.encerrar(
                        turma.getId(), matricula.getId(), solicitante
                )
        );

        verifyNoInteractions(alunos, turmas);
        verificarSemEscrita();
    }

    @Test
    void deveNegarSolicitanteNaoAutenticado() {
        assertThrows(
                AccessDeniedException.class,
                () -> service.matricular(
                        turma.getId(), aluno.getId(), null
                )
        );

        verifyNoInteractions(
                usuarios, alunos, turmas, matriculas, auditoria
        );
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
                () -> service.matricular(
                        turma.getId(), aluno.getId(), outro
                )
        );

        verifyNoInteractions(
                usuarios, alunos, turmas, matriculas, auditoria
        );
    }

    @Test
    void deveNegarDiretorInativo() {
        diretor.setAtivo(false);
        autorizar();

        assertThrows(
                AccessDeniedException.class,
                () -> service.encerrar(
                        turma.getId(), matricula.getId(), solicitante
                )
        );

        verifyNoInteractions(matriculas, alunos, turmas);
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

        verifyNoInteractions(matriculas, turmas);
    }

    private void autorizar() {
        when(usuarios.findById(diretor.getId()))
                .thenReturn(Optional.of(diretor));
    }

    private void prepararEntidades() {
        autorizar();

        when(alunos.buscarPorIdComBloqueio(aluno.getId()))
                .thenReturn(Optional.of(aluno));

        when(turmas.buscarPorIdComBloqueio(turma.getId()))
                .thenReturn(Optional.of(turma));
    }

    private void prepararListagem() {
        autorizar();

        when(turmas.existsById(turma.getId())).thenReturn(true);
    }

    private void prepararEncerramento() {
        prepararEntidades();

        when(matriculas.buscarAlunoId(
                matricula.getId(), turma.getId()
        )).thenReturn(Optional.of(aluno.getId()));

        when(matriculas.findByIdAndTurmaId(
                matricula.getId(), turma.getId()
        )).thenReturn(Optional.of(matricula));
    }

    private void verificarSemEscrita() {
        verify(matriculas, never()).saveAndFlush(any(MatriculaTurma.class));
        verify(matriculas, never()).flush();
        verifyNoInteractions(auditoria);
    }
}