package com.crecheconecta.service;

import com.crecheconecta.dto.AtualizarAlunoRequest;
import com.crecheconecta.dto.CadastrarAlunoRequest;
import com.crecheconecta.entity.Aluno;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.Usuario;
import com.crecheconecta.exception.AlunoNaoEncontradoException;
import com.crecheconecta.exception.ConflitoVersaoException;
import com.crecheconecta.exception.RegraNegocioException;
import com.crecheconecta.repository.AlunoRepository;
import com.crecheconecta.repository.MatriculaTurmaRepository;
import com.crecheconecta.repository.UsuarioRepository;
import com.crecheconecta.security.UsuarioAtual;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlunoServiceTest {

    @Mock
    private AlunoRepository alunos;

    @Mock
    private UsuarioRepository usuarios;

    @Mock
    private AuditoriaService auditoria;

    @Mock
    private MatriculaTurmaRepository matriculas;

    private AlunoService service;
    private Usuario diretor;
    private UsuarioAtual solicitante;
    private Aluno aluno;
    private Instant agora;

    @BeforeEach
    void preparar() {
        agora = Instant.parse("2026-11-02T12:00:00Z");

        service = new AlunoService(
                alunos,
                usuarios,
                auditoria,
                Clock.fixed(agora, ZoneOffset.UTC),
                matriculas
        );

        diretor = new Usuario();
        diretor.setId(UUID.randomUUID());
        diretor.setPerfil(Perfil.DIRECAO);
        diretor.setAtivo(true);

        solicitante = new UsuarioAtual(
                diretor.getId(),
                Perfil.DIRECAO
        );

        aluno = new Aluno();
        aluno.setId(UUID.randomUUID());
        aluno.setNome("Aluno de teste");
        aluno.setDataNascimento(LocalDate.of(2023, 5, 10));
        aluno.setAtivo(true);
        aluno.setVersao(0L);
    }

    @Test
    void deveCadastrarAlunoNormalizandoNome() {
        autorizar();

        var idCriado = UUID.randomUUID();

        doAnswer(invocation -> {
            Aluno salvo = invocation.getArgument(0);
            salvo.setId(idCriado);
            salvo.setVersao(0L);
            return salvo;
        }).when(alunos).saveAndFlush(any(Aluno.class));

        var resposta = service.cadastrar(
                new CadastrarAlunoRequest(
                        " Ana ",
                        LocalDate.of(2023, 5, 10)
                ),
                solicitante
        );

        assertEquals(idCriado, resposta.id());
        assertEquals("Ana", resposta.nome());
        assertTrue(resposta.ativo());

        verify(auditoria).aposCommit(
                "ALUNO_CADASTRADO",
                diretor.getId(),
                idCriado
        );
    }

    @ParameterizedTest
    @MethodSource("cadastrosInvalidos")
    void deveRejeitarCadastroInvalido(CadastrarAlunoRequest request) {
        autorizar();

        assertThrows(
                RegraNegocioException.class,
                () -> service.cadastrar(request, solicitante)
        );

        verifyNoInteractions(alunos, auditoria, matriculas);
    }

    @Test
    void deveListarSemFiltroDeAtivo() {
        autorizar();

        when(alunos.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(aluno)));

        var resposta = service.listar(0, 20, null, solicitante);

        assertEquals(1, resposta.getTotalElements());
        assertEquals(
                aluno.getId(),
                resposta.getContent().getFirst().id()
        );

        verify(alunos, never()).findByAtivo(
                anyBoolean(), any(Pageable.class)
        );
    }

    @ParameterizedTest
    @CsvSource({"true", "false"})
    void deveAplicarFiltroDeAtivo(boolean ativo) {
        autorizar();
        aluno.setAtivo(ativo);

        when(alunos.findByAtivo(
                eq(ativo), any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(aluno)));

        var resposta = service.listar(0, 20, ativo, solicitante);

        assertEquals(
                ativo,
                resposta.getContent().getFirst().ativo()
        );

        verify(alunos, never()).findAll(any(Pageable.class));
    }

    @ParameterizedTest
    @CsvSource({"-1,20", "0,0", "0,-1", "0,101"})
    void deveRejeitarPaginacaoInvalida(int pagina, int tamanho) {
        autorizar();

        assertThrows(
                RegraNegocioException.class,
                () -> service.listar(
                        pagina, tamanho, null, solicitante
                )
        );

        verifyNoInteractions(alunos);
    }

    @Test
    void deveVisualizarAluno() {
        autorizar();

        when(alunos.findById(aluno.getId()))
                .thenReturn(Optional.of(aluno));

        var resposta = service.visualizar(
                aluno.getId(), solicitante
        );

        assertEquals(aluno.getId(), resposta.id());
        assertEquals(aluno.getNome(), resposta.nome());
    }

    @Test
    void deveRejeitarConsultaDeAlunoInexistente() {
        autorizar();

        assertThrows(
                AlunoNaoEncontradoException.class,
                () -> service.visualizar(
                        aluno.getId(), solicitante
                )
        );
    }

    @Test
    void deveAtualizarAlunoComVersaoAtual() {
        prepararAlunoComBloqueio();

        var novaData = LocalDate.of(2023, 6, 15);

        var resposta = service.atualizar(
                aluno.getId(),
                new AtualizarAlunoRequest(
                        " Novo nome ",
                        novaData,
                        0L
                ),
                solicitante
        );

        assertEquals("Novo nome", resposta.nome());
        assertEquals(novaData, resposta.dataNascimento());
        assertTrue(resposta.ativo());

        verify(alunos).flush();

        verify(auditoria).aposCommit(
                "ALUNO_ATUALIZADO",
                diretor.getId(),
                aluno.getId()
        );
    }

    @Test
    void deveRejeitarAtualizacaoComVersaoAntiga() {
        prepararAlunoComBloqueio();

        assertThrows(
                ConflitoVersaoException.class,
                () -> service.atualizar(
                        aluno.getId(),
                        new AtualizarAlunoRequest(
                                "Novo nome",
                                aluno.getDataNascimento(),
                                1L
                        ),
                        solicitante
                )
        );

        assertEquals("Aluno de teste", aluno.getNome());
        verificarSemEscrita();
    }

    @Test
    void deveRejeitarAtualizacaoComDataFutura() {
        prepararAlunoComBloqueio();

        assertThrows(
                RegraNegocioException.class,
                () -> service.atualizar(
                        aluno.getId(),
                        new AtualizarAlunoRequest(
                                "Novo nome",
                                LocalDate.of(2026, 11, 3),
                                0L
                        ),
                        solicitante
                )
        );

        assertEquals("Aluno de teste", aluno.getNome());
        verificarSemEscrita();
    }

    @Test
    void deveInativarAlunoEEncerrarMatriculas() {
        prepararAlunoComBloqueio();

        var resposta = service.inativar(
                aluno.getId(), 0L, solicitante
        );

        assertFalse(resposta.ativo());
        assertEquals(aluno.getId(), resposta.id());

        verify(matriculas).encerrarDoAluno(
                aluno.getId(), agora
        );

        verify(alunos).flush();

        verify(auditoria).aposCommit(
                "ALUNO_INATIVADO",
                diretor.getId(),
                aluno.getId()
        );

        verify(alunos, never()).delete(any(Aluno.class));
    }

    @Test
    void deveRejeitarAlunoJaInativo() {
        aluno.setAtivo(false);
        prepararAlunoComBloqueio();

        assertThrows(
                RegraNegocioException.class,
                () -> service.inativar(
                        aluno.getId(), 0L, solicitante
                )
        );

        verificarSemEscrita();
    }

    @Test
    void deveExigirVersaoNaInativacao() {
        prepararAlunoComBloqueio();

        assertThrows(
                ConflitoVersaoException.class,
                () -> service.inativar(
                        aluno.getId(), null, solicitante
                )
        );

        assertTrue(aluno.isAtivo());
        verificarSemEscrita();
    }

    @Test
    void deveRejeitarInativacaoComVersaoAntiga() {
        prepararAlunoComBloqueio();

        assertThrows(
                ConflitoVersaoException.class,
                () -> service.inativar(
                        aluno.getId(), 1L, solicitante
                )
        );

        assertTrue(aluno.isAtivo());
        verificarSemEscrita();
    }

    @Test
    void deveRejeitarInativacaoDeAlunoInexistente() {
        autorizar();

        assertThrows(
                AlunoNaoEncontradoException.class,
                () -> service.inativar(
                        aluno.getId(), 0L, solicitante
                )
        );

        verificarSemEscrita();
    }

    @Test
    void deveNegarSolicitanteNaoAutenticado() {
        assertThrows(
                AccessDeniedException.class,
                () -> service.inativar(aluno.getId(), 0L, null)
        );

        verifyNoInteractions(alunos, usuarios, matriculas, auditoria);
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
                () -> service.inativar(aluno.getId(), 0L, outro)
        );

        verifyNoInteractions(alunos, usuarios, matriculas, auditoria);
    }

    @Test
    void deveNegarDiretorInativo() {
        diretor.setAtivo(false);
        autorizar();

        assertThrows(
                AccessDeniedException.class,
                () -> service.listar(0, 20, null, solicitante)
        );

        verifyNoInteractions(alunos);
    }

    @Test
    void deveConferirPerfilDoDiretorNoBanco() {
        diretor.setPerfil(Perfil.PROFESSOR);
        autorizar();

        assertThrows(
                AccessDeniedException.class,
                () -> service.listar(0, 20, null, solicitante)
        );

        verifyNoInteractions(alunos);
    }

    static Stream<CadastrarAlunoRequest> cadastrosInvalidos() {
        var nascimento = LocalDate.of(2023, 5, 10);

        return Stream.of(
                new CadastrarAlunoRequest(null, nascimento),
                new CadastrarAlunoRequest("   ", nascimento),
                new CadastrarAlunoRequest("A".repeat(151), nascimento),
                new CadastrarAlunoRequest("Ana", null),
                new CadastrarAlunoRequest(
                        "Ana",
                        LocalDate.of(2026, 11, 3)
                )
        );
    }

    private void autorizar() {
        when(usuarios.findById(diretor.getId()))
                .thenReturn(Optional.of(diretor));
    }

    private void prepararAlunoComBloqueio() {
        autorizar();

        when(alunos.buscarPorIdComBloqueio(aluno.getId()))
                .thenReturn(Optional.of(aluno));
    }

    private void verificarSemEscrita() {
        verify(alunos, never()).flush();
        verifyNoInteractions(matriculas, auditoria);
    }
}