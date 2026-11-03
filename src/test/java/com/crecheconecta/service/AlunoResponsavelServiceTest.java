package com.crecheconecta.service;

import com.crecheconecta.entity.Aluno;
import com.crecheconecta.entity.AlunoResponsavel;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.Usuario;
import com.crecheconecta.exception.AlunoNaoEncontradoException;
import com.crecheconecta.exception.RegraNegocioException;
import com.crecheconecta.repository.AlunoRepository;
import com.crecheconecta.repository.AlunoResponsavelRepository;
import com.crecheconecta.repository.UsuarioRepository;
import com.crecheconecta.security.UsuarioAtual;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
class AlunoResponsavelServiceTest {

    @Mock
    private AlunoResponsavelRepository vinculos;

    @Mock
    private AlunoRepository alunos;

    @Mock
    private UsuarioRepository usuarios;

    @Mock
    private AuditoriaService auditoria;

    private AlunoResponsavelService service;
    private Usuario diretor;
    private Usuario responsavel;
    private UsuarioAtual solicitante;
    private Aluno aluno;
    private AlunoResponsavel vinculo;
    private Instant agora;

    @BeforeEach
    void preparar() {
        agora = Instant.parse("2026-11-02T12:00:00Z");

        service = new AlunoResponsavelService(
                vinculos,
                alunos,
                usuarios,
                auditoria,
                Clock.fixed(agora, ZoneOffset.UTC)
        );

        diretor = new Usuario();
        diretor.setId(UUID.randomUUID());
        diretor.setPerfil(Perfil.DIRECAO);
        diretor.setAtivo(true);

        solicitante = new UsuarioAtual(
                diretor.getId(),
                Perfil.DIRECAO
        );

        responsavel = new Usuario();
        responsavel.setId(UUID.randomUUID());
        responsavel.setPerfil(Perfil.RESPONSAVEL);
        responsavel.setAtivo(true);

        aluno = new Aluno();
        aluno.setId(UUID.randomUUID());
        aluno.setAtivo(true);

        vinculo = new AlunoResponsavel();
        vinculo.setId(UUID.randomUUID());
        vinculo.setAlunoId(aluno.getId());
        vinculo.setResponsavelId(responsavel.getId());
        vinculo.setCriadoEm(agora.minusSeconds(3600));
    }

    @Test
    void deveVincularResponsavelAoAluno() {
        prepararVinculacao();

        var idCriado = UUID.randomUUID();

        doAnswer(invocation -> {
            AlunoResponsavel salvo = invocation.getArgument(0);
            salvo.setId(idCriado);
            return salvo;
        }).when(vinculos).saveAndFlush(any(AlunoResponsavel.class));

        var resposta = service.vincular(
                aluno.getId(),
                responsavel.getId(),
                solicitante
        );

        assertEquals(idCriado, resposta.id());
        assertEquals(aluno.getId(), resposta.alunoId());
        assertEquals(responsavel.getId(), resposta.responsavelId());
        assertEquals(agora, resposta.criadoEm());
        assertNull(resposta.encerradoEm());

        verify(auditoria).aposCommit(
                "ALUNO_RESPONSAVEL_VINCULADO",
                diretor.getId(),
                idCriado
        );
    }

    @Test
    void deveImpedirSegundoResponsavelAtualDoAluno() {
        prepararVinculacao();

        when(vinculos.existsByAlunoIdAndEncerradoEmIsNull(
                aluno.getId()
        )).thenReturn(true);

        assertThrows(
                RegraNegocioException.class,
                () -> service.vincular(
                        aluno.getId(),
                        responsavel.getId(),
                        solicitante
                )
        );

        verificarSemEscrita();
    }

    @Test
    void deveImpedirResponsavelComOutroAlunoAtual() {
        prepararVinculacao();

        when(vinculos.existsByResponsavelIdAndEncerradoEmIsNull(
                responsavel.getId()
        )).thenReturn(true);

        assertThrows(
                RegraNegocioException.class,
                () -> service.vincular(
                        aluno.getId(),
                        responsavel.getId(),
                        solicitante
                )
        );

        verificarSemEscrita();
    }

    @Test
    void deveRejeitarAlunoInativoNaVinculacao() {
        prepararAluno();
        aluno.setAtivo(false);

        assertThrows(
                RegraNegocioException.class,
                () -> service.vincular(
                        aluno.getId(),
                        responsavel.getId(),
                        solicitante
                )
        );

        verify(usuarios, never()).buscarPorIdComBloqueio(any());
        verificarSemEscrita();
    }

    @Test
    void deveRejeitarAlunoInexistenteNaVinculacao() {
        autorizar();

        assertThrows(
                AlunoNaoEncontradoException.class,
                () -> service.vincular(
                        aluno.getId(),
                        responsavel.getId(),
                        solicitante
                )
        );

        verificarSemEscrita();
    }

    @Test
    void deveRejeitarResponsavelInexistente() {
        prepararAluno();

        assertThrows(
                RegraNegocioException.class,
                () -> service.vincular(
                        aluno.getId(),
                        responsavel.getId(),
                        solicitante
                )
        );

        verificarSemEscrita();
    }

    @ParameterizedTest
    @EnumSource(
            value = Perfil.class,
            names = {"DIRECAO", "PROFESSOR"}
    )
    void deveRejeitarUsuarioQueNaoSejaResponsavel(Perfil perfil) {
        prepararVinculacao();
        responsavel.setPerfil(perfil);

        assertThrows(
                RegraNegocioException.class,
                () -> service.vincular(
                        aluno.getId(),
                        responsavel.getId(),
                        solicitante
                )
        );

        verificarSemEscrita();
    }

    @Test
    void deveListarVinculosAtuaisDoAluno() {
        prepararListagem();

        when(vinculos
                .findByAlunoIdAndEncerradoEmIsNullOrderByCriadoEmAsc(
                        aluno.getId()
                )).thenReturn(List.of(vinculo));

        var resposta = service.listar(aluno.getId(), solicitante);

        assertEquals(1, resposta.size());
        assertEquals(vinculo.getId(), resposta.getFirst().id());
        assertEquals(
                responsavel.getId(),
                resposta.getFirst().responsavelId()
        );
        assertNull(resposta.getFirst().encerradoEm());

        verifyNoInteractions(auditoria);
    }

    @Test
    void deveRetornarListaVaziaQuandoNaoHouverVinculoAtual() {
        prepararListagem();

        when(vinculos
                .findByAlunoIdAndEncerradoEmIsNullOrderByCriadoEmAsc(
                        aluno.getId()
                )).thenReturn(List.of());

        assertTrue(service.listar(aluno.getId(), solicitante).isEmpty());

        verifyNoInteractions(auditoria);
    }

    @Test
    void deveRejeitarListagemDeAlunoInexistente() {
        autorizar();

        assertThrows(
                AlunoNaoEncontradoException.class,
                () -> service.listar(aluno.getId(), solicitante)
        );

        verifyNoInteractions(vinculos, auditoria);
    }

    @Test
    void deveEncerrarVinculoPreservandoHistorico() {
        prepararEncerramento();

        var criadoEm = vinculo.getCriadoEm();

        var resposta = service.encerrar(
                aluno.getId(),
                vinculo.getId(),
                solicitante
        );

        assertEquals(vinculo.getId(), resposta.id());
        assertEquals(aluno.getId(), resposta.alunoId());
        assertEquals(responsavel.getId(), resposta.responsavelId());
        assertEquals(criadoEm, resposta.criadoEm());
        assertEquals(agora, resposta.encerradoEm());

        verify(vinculos).flush();
        verify(vinculos, never()).delete(any(AlunoResponsavel.class));

        verify(auditoria).aposCommit(
                "ALUNO_RESPONSAVEL_DESVINCULADO",
                diretor.getId(),
                vinculo.getId()
        );
    }

    @Test
    void devePermitirEncerrarVinculoDeAlunoInativo() {
        prepararEncerramento();
        aluno.setAtivo(false);

        var resposta = service.encerrar(
                aluno.getId(),
                vinculo.getId(),
                solicitante
        );

        assertEquals(agora, resposta.encerradoEm());
        assertFalse(aluno.isAtivo());
    }

    @Test
    void deveRejeitarVinculoJaEncerrado() {
        prepararEncerramento();

        var encerradoAntes = agora.minusSeconds(60);
        vinculo.setEncerradoEm(encerradoAntes);

        assertThrows(
                RegraNegocioException.class,
                () -> service.encerrar(
                        aluno.getId(),
                        vinculo.getId(),
                        solicitante
                )
        );

        assertEquals(encerradoAntes, vinculo.getEncerradoEm());
        verificarSemEscrita();
    }

    @Test
    void deveRejeitarVinculoInexistenteOuDeOutroAluno() {
        prepararAluno();

        assertThrows(
                RegraNegocioException.class,
                () -> service.encerrar(
                        aluno.getId(),
                        vinculo.getId(),
                        solicitante
                )
        );

        verify(usuarios, never()).buscarPorIdComBloqueio(any());
        verificarSemEscrita();
    }

    @Test
    void deveRejeitarEncerramentoDeAlunoInexistente() {
        autorizar();

        assertThrows(
                AlunoNaoEncontradoException.class,
                () -> service.encerrar(
                        aluno.getId(),
                        vinculo.getId(),
                        solicitante
                )
        );

        verifyNoInteractions(vinculos, auditoria);
    }

    @Test
    void deveRejeitarEncerramentoSeResponsavelNaoExiste() {
        prepararAluno();

        when(vinculos.findByIdAndAlunoId(
                vinculo.getId(),
                aluno.getId()
        )).thenReturn(Optional.of(vinculo));

        assertThrows(
                RegraNegocioException.class,
                () -> service.encerrar(
                        aluno.getId(),
                        vinculo.getId(),
                        solicitante
                )
        );

        assertNull(vinculo.getEncerradoEm());
        verificarSemEscrita();
    }

    @Test
    void naoDeveAuditarSucessoSePersistenciaFalhar() {
        prepararVinculacao();

        when(vinculos.saveAndFlush(any(AlunoResponsavel.class)))
                .thenThrow(new IllegalStateException("Falha simulada"));

        assertThrows(
                IllegalStateException.class,
                () -> service.vincular(
                        aluno.getId(),
                        responsavel.getId(),
                        solicitante
                )
        );

        verifyNoInteractions(auditoria);
    }

    @Test
    void deveNegarSolicitanteNaoAutenticadoNasTresOperacoes() {
        verificarAcessoNegado(null);

        verifyNoInteractions(usuarios, alunos, vinculos, auditoria);
    }

    @ParameterizedTest
    @EnumSource(
            value = Perfil.class,
            names = {"PROFESSOR", "RESPONSAVEL"}
    )
    void deveNegarOutrosPerfisNasTresOperacoes(Perfil perfil) {
        var outro = new UsuarioAtual(UUID.randomUUID(), perfil);

        verificarAcessoNegado(outro);

        verifyNoInteractions(usuarios, alunos, vinculos, auditoria);
    }

    @Test
    void deveNegarDiretorInativoNasTresOperacoes() {
        diretor.setAtivo(false);
        autorizar();

        verificarAcessoNegado(solicitante);

        verifyNoInteractions(alunos, vinculos, auditoria);
    }

    @Test
    void deveConferirPerfilAtualDoDiretorNoBanco() {
        diretor.setPerfil(Perfil.PROFESSOR);
        autorizar();

        verificarAcessoNegado(solicitante);

        verifyNoInteractions(alunos, vinculos, auditoria);
    }

    @Test
    void deveNegarDiretorNaoEncontrado() {
        verificarAcessoNegado(solicitante);

        verifyNoInteractions(alunos, vinculos, auditoria);
    }

    private void verificarAcessoNegado(UsuarioAtual usuario) {
        assertThrows(
                AccessDeniedException.class,
                () -> service.vincular(
                        aluno.getId(),
                        responsavel.getId(),
                        usuario
                )
        );

        assertThrows(
                AccessDeniedException.class,
                () -> service.listar(aluno.getId(), usuario)
        );

        assertThrows(
                AccessDeniedException.class,
                () -> service.encerrar(
                        aluno.getId(),
                        vinculo.getId(),
                        usuario
                )
        );
    }

    private void autorizar() {
        when(usuarios.findById(diretor.getId()))
                .thenReturn(Optional.of(diretor));
    }

    private void prepararAluno() {
        autorizar();

        when(alunos.buscarPorIdComBloqueio(aluno.getId()))
                .thenReturn(Optional.of(aluno));
    }

    private void prepararVinculacao() {
        prepararAluno();

        when(usuarios.buscarPorIdComBloqueio(responsavel.getId()))
                .thenReturn(Optional.of(responsavel));
    }

    private void prepararListagem() {
        autorizar();

        when(alunos.existsById(aluno.getId())).thenReturn(true);
    }

    private void prepararEncerramento() {
        prepararVinculacao();

        when(vinculos.findByIdAndAlunoId(
                vinculo.getId(),
                aluno.getId()
        )).thenReturn(Optional.of(vinculo));
    }

    private void verificarSemEscrita() {
        verify(vinculos, never()).saveAndFlush(any(AlunoResponsavel.class));
        verify(vinculos, never()).flush();
        verifyNoInteractions(auditoria);
    }
}