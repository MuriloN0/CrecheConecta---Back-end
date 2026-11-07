package com.crecheconecta.service;

import com.crecheconecta.dto.RegistrarPresencaRequest;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.Turma;
import com.crecheconecta.entity.Usuario;
import com.crecheconecta.exception.AlunoNaoEncontradoException;
import com.crecheconecta.exception.RegraNegocioException;
import com.crecheconecta.exception.TurmaNaoEncontradaException;
import com.crecheconecta.repository.AlunoRepository;
import com.crecheconecta.repository.MatriculaTurmaRepository;
import com.crecheconecta.repository.PresencaRepository;
import com.crecheconecta.repository.TurmaProfessorRepository;
import com.crecheconecta.repository.TurmaRepository;
import com.crecheconecta.repository.UsuarioRepository;
import com.crecheconecta.security.UsuarioAtual;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static com.crecheconecta.service.DadosAutenticacaoTest.usuario;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class ChamadaServiceValidacaoTest {

    private final UsuarioRepository usuarios =
            mock(UsuarioRepository.class);

    private final TurmaRepository turmas =
            mock(TurmaRepository.class);

    private final TurmaProfessorRepository professores =
            mock(TurmaProfessorRepository.class);

    private final MatriculaTurmaRepository matriculas =
            mock(MatriculaTurmaRepository.class);

    private final AlunoRepository alunos =
            mock(AlunoRepository.class);

    private final PresencaRepository presencas =
            mock(PresencaRepository.class);

    private final AuditoriaService auditoria =
            mock(AuditoriaService.class);

    private final LocalDate hoje =
            LocalDate.of(2026, 11, 2);

    private final ChamadaService service =
            new ChamadaService(
                    new VinculoProfessorService(usuarios, professores),
                    turmas,
                    matriculas,
                    alunos,
                    presencas,
                    auditoria,
                    Clock.fixed(
                            Instant.parse("2026-11-02T12:00:00Z"),
                            ZoneOffset.UTC
                    )
            );

    private final Usuario professor =
            usuario(Perfil.PROFESSOR);

    private final UsuarioAtual solicitante =
            new UsuarioAtual(
                    professor.getId(),
                    Perfil.PROFESSOR
            );

    private final UUID turmaId = UUID.randomUUID();
    private final UUID alunoId = UUID.randomUUID();

    @ParameterizedTest
    @CsvSource({"-1,20", "0,0", "0,-1", "0,101"})
    void deveRejeitarPaginacaoInvalida(int pagina, int tamanho) {
        autorizar();

        var turma = new Turma();
        turma.setAtivo(true);

        when(turmas.findById(turmaId))
                .thenReturn(Optional.of(turma));

        assertThrows(
                RegraNegocioException.class,
                () -> service.consultar(
                        turmaId,
                        hoje,
                        pagina,
                        tamanho,
                        solicitante
                )
        );

        verifyNoInteractions(matriculas, presencas);
    }

    @Test
    void deveRejeitarTurmaInexistenteNaConsulta() {
        autorizar();

        assertThrows(
                TurmaNaoEncontradaException.class,
                () -> service.consultar(
                        turmaId,
                        hoje,
                        0,
                        20,
                        solicitante
                )
        );
    }

    @Test
    void deveRejeitarAlunoInexistenteNoRegistro() {
        autorizar();

        var turma = new Turma();
        turma.setAtivo(true);

        when(turmas.buscarPorIdComBloqueio(turmaId))
                .thenReturn(Optional.of(turma));

        assertThrows(
                AlunoNaoEncontradoException.class,
                () -> service.registrar(
                        turmaId,
                        alunoId,
                        hoje,
                        new RegistrarPresencaRequest(
                                true,
                                null
                        ),
                        solicitante
                )
        );

        verifyNoInteractions(presencas, auditoria);
    }

    @Test
    void deveRejeitarDataNula() {
        autorizar();

        assertThrows(
                RegraNegocioException.class,
                () -> service.consultar(
                        turmaId,
                        null,
                        0,
                        20,
                        solicitante
                )
        );

        verifyNoInteractions(turmas, presencas);
    }

    @Test
    void deveConferirPerfilAtualDoProfessorNoBanco() {
        professor.setPerfil(Perfil.RESPONSAVEL);

        when(usuarios.findById(professor.getId()))
                .thenReturn(Optional.of(professor));

        assertThrows(
                AccessDeniedException.class,
                () -> service.consultar(
                        turmaId,
                        hoje,
                        0,
                        20,
                        solicitante
                )
        );

        verifyNoInteractions(professores, presencas);
    }

    private void autorizar() {
        when(usuarios.findById(professor.getId()))
                .thenReturn(Optional.of(professor));

        when(professores
                .existsByTurmaIdAndProfessorIdAndEncerradoEmIsNull(
                        turmaId,
                        professor.getId()
                )).thenReturn(true);
    }
}