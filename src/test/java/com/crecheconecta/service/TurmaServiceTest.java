package com.crecheconecta.service;

import com.crecheconecta.dto.AtualizarTurmaRequest;
import com.crecheconecta.dto.CadastrarTurmaRequest;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.Turma;
import com.crecheconecta.entity.Usuario;
import com.crecheconecta.exception.ConflitoVersaoException;
import com.crecheconecta.exception.RegraNegocioException;
import com.crecheconecta.exception.TurmaNaoEncontradaException;
import com.crecheconecta.repository.MatriculaTurmaRepository;
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
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TurmaServiceTest {

    @Mock private TurmaRepository turmas;
    @Mock private UsuarioRepository usuarios;
    @Mock private AuditoriaService auditoria;
    @Mock private MatriculaTurmaRepository matriculas;
    @Mock private TurmaProfessorRepository professores;

    private TurmaService service;
    private Usuario diretor;
    private UsuarioAtual solicitante;
    private Turma turma;
    private Instant agora;

    @BeforeEach
    void preparar() {
        agora = Instant.parse("2026-11-02T12:00:00Z");

        service = new TurmaService(
                turmas,
                usuarios,
                auditoria,
                matriculas,
                Clock.fixed(agora, ZoneOffset.UTC),
                professores
        );

        diretor = new Usuario();
        diretor.setId(UUID.randomUUID());
        diretor.setPerfil(Perfil.DIRECAO);
        diretor.setAtivo(true);

        solicitante = new UsuarioAtual(
                diretor.getId(), Perfil.DIRECAO
        );

        turma = new Turma();
        turma.setId(UUID.randomUUID());
        turma.setNome("Berçário");
        turma.setNumeroSala("01");
        turma.setAtivo(true);
        turma.setVersao(0L);
    }

    @Test
    void deveCadastrarNormalizandoNomeESala() {
        autorizar();

        var idCriado = UUID.randomUUID();

        doAnswer(invocation -> {
            Turma salva = invocation.getArgument(0);
            salva.setId(idCriado);
            salva.setVersao(0L);
            return salva;
        }).when(turmas).saveAndFlush(any(Turma.class));

        var resposta = service.cadastrar(
                new CadastrarTurmaRequest(" Maternal ", " 02 "),
                solicitante
        );

        assertEquals(idCriado, resposta.id());
        assertEquals("Maternal", resposta.nome());
        assertEquals("02", resposta.numeroSala());
        assertTrue(resposta.ativo());

        verify(auditoria).aposCommit(
                "TURMA_CADASTRADA",
                diretor.getId(),
                idCriado
        );
    }

    @ParameterizedTest
    @MethodSource("cadastrosInvalidos")
    void deveRejeitarCadastroInvalido(CadastrarTurmaRequest request) {
        autorizar();

        assertThrows(
                RegraNegocioException.class,
                () -> service.cadastrar(request, solicitante)
        );

        verifyNoInteractions(turmas, auditoria);
    }

    @Test
    void deveListarSemFiltroComPaginacaoEOrdenacao() {
        autorizar();

        when(turmas.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(turma)));

        var resposta = service.listar(1, 10, null, solicitante);

        assertEquals(turma.getId(), resposta.getContent().getFirst().id());

        var captor = ArgumentCaptor.forClass(Pageable.class);
        verify(turmas).findAll(captor.capture());

        assertEquals(1, captor.getValue().getPageNumber());
        assertEquals(10, captor.getValue().getPageSize());
        assertEquals(
                Sort.by("nome").ascending()
                        .and(Sort.by("id").ascending()),
                captor.getValue().getSort()
        );
    }

    @ParameterizedTest
    @CsvSource({"true", "false"})
    void deveFiltrarTurmasPorAtivo(boolean ativo) {
        autorizar();
        turma.setAtivo(ativo);

        when(turmas.findByAtivo(eq(ativo), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(turma)));

        var resposta = service.listar(0, 20, ativo, solicitante);

        assertEquals(ativo, resposta.getContent().getFirst().ativo());
        verify(turmas, never()).findAll(any(Pageable.class));
    }

    @ParameterizedTest
    @CsvSource({"-1,20", "0,0", "0,-1", "0,101"})
    void deveRejeitarPaginacaoInvalida(int pagina, int tamanho) {
        autorizar();

        assertThrows(
                RegraNegocioException.class,
                () -> service.listar(pagina, tamanho, null, solicitante)
        );

        verifyNoInteractions(turmas);
    }

    @Test
    void deveVisualizarTurma() {
        autorizar();

        when(turmas.findById(turma.getId()))
                .thenReturn(Optional.of(turma));

        var resposta = service.visualizar(turma.getId(), solicitante);

        assertEquals(turma.getId(), resposta.id());
        assertEquals("Berçário", resposta.nome());
    }

    @Test
    void deveRejeitarConsultaDeTurmaInexistente() {
        autorizar();

        assertThrows(
                TurmaNaoEncontradaException.class,
                () -> service.visualizar(turma.getId(), solicitante)
        );
    }

    @Test
    void deveAtualizarComVersaoAtual() {
        prepararBloqueio();

        var resposta = service.atualizar(
                turma.getId(),
                new AtualizarTurmaRequest(" Maternal ", " 03 ", 0L),
                solicitante
        );

        assertEquals("Maternal", resposta.nome());
        assertEquals("03", resposta.numeroSala());
        assertTrue(resposta.ativo());

        verify(turmas).flush();
        verify(auditoria).aposCommit(
                "TURMA_ATUALIZADA", diretor.getId(), turma.getId()
        );
    }

    @Test
    void deveRejeitarAtualizacaoComVersaoAntiga() {
        prepararBloqueio();

        assertThrows(
                ConflitoVersaoException.class,
                () -> service.atualizar(
                        turma.getId(),
                        new AtualizarTurmaRequest("Maternal", "03", 1L),
                        solicitante
                )
        );

        assertEquals("Berçário", turma.getNome());
        verificarSemEscrita();
    }

    @Test
    void deveInativarEncerrandoMatriculasEVinculos() {
        prepararBloqueio();

        var resposta = service.inativar(turma.getId(), 0L, solicitante);

        assertFalse(resposta.ativo());

        verify(matriculas).encerrarDaTurma(turma.getId(), agora);
        verify(professores).encerrarDaTurma(turma.getId(), agora);
        verify(turmas).flush();

        verify(auditoria).aposCommit(
                "TURMA_INATIVADA", diretor.getId(), turma.getId()
        );

        verify(turmas, never()).delete(any(Turma.class));
    }

    @Test
    void deveRejeitarTurmaJaInativa() {
        turma.setAtivo(false);
        prepararBloqueio();

        assertThrows(
                RegraNegocioException.class,
                () -> service.inativar(turma.getId(), 0L, solicitante)
        );

        verificarSemEscrita();
    }

    @Test
    void deveExigirVersaoNaInativacao() {
        prepararBloqueio();

        assertThrows(
                ConflitoVersaoException.class,
                () -> service.inativar(turma.getId(), null, solicitante)
        );

        assertTrue(turma.isAtivo());
        verificarSemEscrita();
    }

    @Test
    void deveRejeitarVersaoAntigaNaInativacao() {
        prepararBloqueio();

        assertThrows(
                ConflitoVersaoException.class,
                () -> service.inativar(turma.getId(), 1L, solicitante)
        );

        assertTrue(turma.isAtivo());
        verificarSemEscrita();
    }

    @Test
    void deveRejeitarInativacaoDeTurmaInexistente() {
        autorizar();

        assertThrows(
                TurmaNaoEncontradaException.class,
                () -> service.inativar(turma.getId(), 0L, solicitante)
        );

        verificarSemEscrita();
    }

    @Test
    void deveNegarSolicitanteNaoAutenticado() {
        assertThrows(
                AccessDeniedException.class,
                () -> service.inativar(turma.getId(), 0L, null)
        );

        verifyNoInteractions(
                usuarios, turmas, matriculas, professores, auditoria
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
                () -> service.inativar(turma.getId(), 0L, outro)
        );

        verifyNoInteractions(
                usuarios, turmas, matriculas, professores, auditoria
        );
    }

    @Test
    void deveNegarDiretorInativo() {
        diretor.setAtivo(false);
        autorizar();

        assertThrows(
                AccessDeniedException.class,
                () -> service.listar(0, 20, null, solicitante)
        );

        verifyNoInteractions(turmas);
    }

    @Test
    void deveConferirPerfilDoDiretorNoBanco() {
        diretor.setPerfil(Perfil.PROFESSOR);
        autorizar();

        assertThrows(
                AccessDeniedException.class,
                () -> service.listar(0, 20, null, solicitante)
        );

        verifyNoInteractions(turmas);
    }

    static Stream<CadastrarTurmaRequest> cadastrosInvalidos() {
        return Stream.of(
                new CadastrarTurmaRequest(null, "01"),
                new CadastrarTurmaRequest("   ", "01"),
                new CadastrarTurmaRequest("A".repeat(151), "01"),
                new CadastrarTurmaRequest("Maternal", null),
                new CadastrarTurmaRequest("Maternal", "   "),
                new CadastrarTurmaRequest("Maternal", "A".repeat(21))
        );
    }

    private void autorizar() {
        when(usuarios.findById(diretor.getId()))
                .thenReturn(Optional.of(diretor));
    }

    private void prepararBloqueio() {
        autorizar();

        when(turmas.buscarPorIdComBloqueio(turma.getId()))
                .thenReturn(Optional.of(turma));
    }

    private void verificarSemEscrita() {
        verify(turmas, never()).flush();
        verifyNoInteractions(matriculas, professores, auditoria);
    }
}