package com.crecheconecta.service;

import com.crecheconecta.dto.RegistrarPresencaRequest;
import com.crecheconecta.entity.*;
import com.crecheconecta.exception.ConflitoVersaoException;
import com.crecheconecta.exception.RegraNegocioException;
import com.crecheconecta.repository.*;
import com.crecheconecta.security.UsuarioAtual;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChamadaServiceTest {

    @Mock private UsuarioRepository usuarios;
    @Mock private TurmaRepository turmas;
    @Mock private TurmaProfessorRepository professores;
    @Mock private MatriculaTurmaRepository matriculas;
    @Mock private AlunoRepository alunos;
    @Mock private PresencaRepository presencas;
    @Mock private AuditoriaService auditoria;

    private ChamadaService service;
    private Usuario professor;
    private UsuarioAtual solicitante;
    private Turma turma;
    private Aluno aluno;
    private Presenca registro;
    private LocalDate hoje;
    private Instant agora;

    @BeforeEach
    void preparar() {
        agora = Instant.parse("2026-11-02T12:00:00Z");
        hoje = LocalDate.of(2026, 11, 2);

        service = criarService(
                Clock.fixed(agora, ZoneOffset.UTC)
        );

        professor = new Usuario();
        professor.setId(UUID.randomUUID());
        professor.setPerfil(Perfil.PROFESSOR);
        professor.setAtivo(true);
        professor.setSituacaoCadastro(SituacaoCadastro.APROVADO);

        solicitante = new UsuarioAtual(
                professor.getId(),
                Perfil.PROFESSOR
        );

        turma = new Turma();
        turma.setId(UUID.randomUUID());
        turma.setAtivo(true);

        aluno = new Aluno();
        aluno.setId(UUID.randomUUID());
        aluno.setNome("Aluno de teste");
        aluno.setAtivo(true);

        registro = new Presenca();
        registro.setId(UUID.randomUUID());
        registro.setTurmaId(turma.getId());
        registro.setAlunoId(aluno.getId());
        registro.setData(hoje);
        registro.setPresente(true);
        registro.setVersao(0L);
    }

    @Test
    void deveRegistrarPresenca() {
        prepararRegistro();

        var resposta = registrar(true, null);

        assertTrue(resposta.presente());
        assertEquals(hoje, resposta.data());

        verify(presencas).saveAndFlush(argThat(p ->
                p.getAlunoId().equals(aluno.getId())
                        && p.getTurmaId().equals(turma.getId())
                        && p.getRegistradoPor().equals(professor.getId())
                        && p.getAtualizadoEm().equals(agora)
        ));

        verify(auditoria).aposCommit(
                eq("PRESENCA_REGISTRADA"),
                eq(professor.getId()),
                nullable(UUID.class)
        );
    }

    @Test
    void deveRegistrarAusencia() {
        prepararRegistro();

        assertFalse(registrar(false, null).presente());
    }

    @Test
    void deveEditarPresencaDuranteODia() {
        prepararRegistroExistente();

        var resposta = registrar(false, 0L);

        assertFalse(resposta.presente());
        assertEquals(registro.getId(), resposta.id());

        verify(auditoria).aposCommit(
                "PRESENCA_ATUALIZADA",
                professor.getId(),
                registro.getId()
        );
    }

    @Test
    void deveRejeitarVersaoAntiga() {
        prepararRegistroExistente();

        assertThrows(
                ConflitoVersaoException.class,
                () -> registrar(false, 1L)
        );

        assertTrue(registro.isPresente());
        verificarSemEscrita();
    }

    @Test
    void deveExigirVersaoNaEdicao() {
        prepararRegistroExistente();

        assertThrows(
                ConflitoVersaoException.class,
                () -> registrar(false, null)
        );

        verificarSemEscrita();
    }

    @Test
    void deveRejeitarVersaoNaPrimeiraMarcacao() {
        prepararRegistro();

        assertThrows(
                ConflitoVersaoException.class,
                () -> registrar(true, 0L)
        );

        verificarSemEscrita();
    }

    @Test
    void deveNegarProfessorSemVinculoAtual() {
        when(usuarios.findById(professor.getId()))
                .thenReturn(Optional.of(professor));

        assertThrows(
                AccessDeniedException.class,
                () -> registrar(true, null)
        );

        verifyNoInteractions(turmas, alunos, presencas);
    }

    @ParameterizedTest
    @EnumSource(
            value = Perfil.class,
            names = {"DIRECAO", "RESPONSAVEL"}
    )
    void deveNegarOutrosPerfis(Perfil perfil) {
        solicitante = new UsuarioAtual(UUID.randomUUID(), perfil);

        assertThrows(
                AccessDeniedException.class,
                () -> registrar(true, null)
        );

        verifyNoInteractions(usuarios, turmas, presencas);
    }

    @Test
    void deveNegarUsuarioNaoAutenticado() {
        solicitante = null;

        assertThrows(
                AccessDeniedException.class,
                () -> registrar(true, null)
        );

        verifyNoInteractions(usuarios, presencas);
    }

    @Test
    void deveNegarProfessorInativo() {
        professor.setAtivo(false);

        when(usuarios.findById(professor.getId()))
                .thenReturn(Optional.of(professor));

        assertThrows(
                AccessDeniedException.class,
                () -> registrar(true, null)
        );

        verifyNoInteractions(presencas);
    }

    @Test
    void deveNegarProfessorNaoAprovado() {
        professor.setSituacaoCadastro(
                SituacaoCadastro.PENDENTE_APROVACAO
        );

        when(usuarios.findById(professor.getId()))
                .thenReturn(Optional.of(professor));

        assertThrows(
                AccessDeniedException.class,
                () -> registrar(true, null)
        );

        verifyNoInteractions(presencas);
    }

    @Test
    void deveRejeitarDiaPassadoEFuturo() {
        autorizar();

        for (var data : List.of(hoje.minusDays(1), hoje.plusDays(1))) {
            assertThrows(
                    RegraNegocioException.class,
                    () -> service.registrar(
                            turma.getId(),
                            aluno.getId(),
                            data,
                            new RegistrarPresencaRequest(true, null),
                            solicitante
                    )
            );
        }

        verifyNoInteractions(turmas, presencas);
    }

    @Test
    void deveRejeitarTurmaInativa() {
        autorizar();
        turma.setAtivo(false);

        when(turmas.buscarPorIdComBloqueio(turma.getId()))
                .thenReturn(Optional.of(turma));

        assertThrows(
                RegraNegocioException.class,
                () -> registrar(true, null)
        );

        verifyNoInteractions(alunos, presencas);
    }

    @Test
    void deveRejeitarAlunoInativo() {
        autorizar();
        prepararTurmaEAluno();

        aluno.setAtivo(false);

        assertThrows(
                RegraNegocioException.class,
                () -> registrar(true, null)
        );

        verifyNoInteractions(presencas);
    }

    @Test
    void deveRejeitarAlunoSemMatriculaAtual() {
        autorizar();
        prepararTurmaEAluno();

        assertThrows(
                RegraNegocioException.class,
                () -> registrar(true, null)
        );

        verifyNoInteractions(presencas);
    }

    @Test
    void deveRejeitarPresencaNula() {
        prepararRegistro();

        assertThrows(
                RegraNegocioException.class,
                () -> registrar(null, null)
        );

        verificarSemEscrita();
    }

    @Test
    void deveListarAlunoPendenteSemTransformarEmAusente() {
        prepararConsulta();

        when(presencas.findByTurmaIdAndData(turma.getId(), hoje))
                .thenReturn(List.of());

        var resposta = service.consultar(
                turma.getId(), hoje, 0, 20, solicitante
        );

        var item = resposta.getContent().getFirst();

        assertEquals(aluno.getId(), item.alunoId());
        assertNull(item.presente());
        assertNull(item.versao());
    }

    @Test
    void deveListarPresencaJaRegistrada() {
        prepararConsulta();

        when(presencas.findByTurmaIdAndData(turma.getId(), hoje))
                .thenReturn(List.of(registro));

        var resposta = service.consultar(
                turma.getId(), hoje, 0, 20, solicitante
        );

        assertEquals(
                Boolean.TRUE,
                resposta.getContent().getFirst().presente()
        );

        assertEquals(
                0L,
                resposta.getContent().getFirst().versao()
        );
    }

    @Test
    void deveUsarDiaDeSaoPauloEmVezDoDiaUtc() {
        service = criarService(
                Clock.fixed(
                        Instant.parse("2026-11-03T01:00:00Z"),
                        ZoneOffset.UTC
                )
        );

        prepararRegistro();

        assertDoesNotThrow(() -> registrar(true, null));
    }

    @Test
    void deveRecusarSeODiaMudarDuranteAOperacao() {
        var clock = mock(Clock.class);

        when(clock.instant()).thenReturn(
                Instant.parse("2026-11-03T02:59:59Z"),
                Instant.parse("2026-11-03T03:00:00Z")
        );

        service = criarService(clock);
        prepararRegistro();

        assertThrows(
                RegraNegocioException.class,
                () -> registrar(true, null)
        );

        verifyNoInteractions(auditoria);
    }

    private ChamadaService criarService(Clock clock) {
        return new ChamadaService(
                usuarios,
                turmas,
                professores,
                matriculas,
                alunos,
                presencas,
                auditoria,
                clock
        );
    }

    private com.crecheconecta.dto.PresencaResponse registrar(
            Boolean presente,
            Long versao
    ) {
        return service.registrar(
                turma.getId(),
                aluno.getId(),
                hoje,
                new RegistrarPresencaRequest(presente, versao),
                solicitante
        );
    }

    private void autorizar() {
        when(usuarios.findById(professor.getId()))
                .thenReturn(Optional.of(professor));

        when(professores
                .existsByTurmaIdAndProfessorIdAndEncerradoEmIsNull(
                        turma.getId(), professor.getId()
                )).thenReturn(true);
    }

    private void prepararTurmaEAluno() {
        when(turmas.buscarPorIdComBloqueio(turma.getId()))
                .thenReturn(Optional.of(turma));

        when(alunos.findById(aluno.getId()))
                .thenReturn(Optional.of(aluno));
    }

    private void prepararRegistro() {
        autorizar();
        prepararTurmaEAluno();

        when(matriculas
                .existsByTurmaIdAndAlunoIdAndEncerradoEmIsNull(
                        turma.getId(), aluno.getId()
                )).thenReturn(true);
    }

    private void prepararRegistroExistente() {
        prepararRegistro();

        when(presencas.findByTurmaIdAndAlunoIdAndData(
                turma.getId(), aluno.getId(), hoje
        )).thenReturn(Optional.of(registro));
    }

    private void prepararConsulta() {
        autorizar();

        when(turmas.findById(turma.getId()))
                .thenReturn(Optional.of(turma));

        when(matriculas.buscarAlunosAtuaisDaChamada(
                eq(turma.getId()), any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(aluno)));
    }

    private void verificarSemEscrita() {
        verify(presencas, never()).saveAndFlush(any());
        verifyNoInteractions(auditoria);
    }
}