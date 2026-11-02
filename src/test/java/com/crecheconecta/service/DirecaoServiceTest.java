package com.crecheconecta.service;

import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.SituacaoCadastro;
import com.crecheconecta.entity.Usuario;
import com.crecheconecta.exception.ConflitoVersaoException;
import com.crecheconecta.exception.RegraNegocioException;
import com.crecheconecta.repository.SessaoRepository;
import com.crecheconecta.repository.UsuarioRepository;
import com.crecheconecta.security.UsuarioAtual;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DirecaoServiceTest {

    @Mock
    private UsuarioRepository usuarios;

    @Mock
    private SessaoRepository sessoes;

    @Mock
    private AuditoriaService auditoria;

    private DirecaoService service;
    private Usuario diretor;
    private Usuario outroDiretor;
    private UsuarioAtual solicitante;
    private Instant agora;

    @BeforeEach
    void preparar() {

        agora = Instant.parse("2026-11-01T12:00:00Z");

        var clock = Clock.fixed(agora, ZoneOffset.UTC);

        service = new DirecaoService(usuarios, sessoes, auditoria, clock);

        diretor = criarDiretor();
        outroDiretor = criarDiretor();

        solicitante = new UsuarioAtual(diretor.getId(), Perfil.DIRECAO);
    }

    @Test
    void deveConsultarMinhaConta() {

        when(usuarios.findById(diretor.getId())).thenReturn(Optional.of(diretor));

        var resposta = service.consultarMinhaConta(solicitante);

        assertEquals(diretor.getId(), resposta.id());
        assertEquals(diretor.getEmail(), resposta.email());
        assertEquals(0L, resposta.versao());
        assertTrue(resposta.ativo());

        verifyNoInteractions(sessoes, auditoria);
    }

    @Test
    void deveNegarConsultaDeUsuarioNaoEncontrado() {

        when(usuarios.findById(diretor.getId())).thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class, () -> service.consultarMinhaConta(solicitante));

        verifyNoInteractions(sessoes, auditoria);
    }

    @Test
    void deveInativarMinhaContaERevogarSessoes() {

        prepararDiretores();

        var resposta = service.inativarMinhaConta(0L, solicitante);

        assertFalse(diretor.isAtivo());
        assertEquals(SituacaoCadastro.INATIVO, diretor.getSituacaoCadastro());
        assertFalse(resposta.ativo());

        assertTrue(outroDiretor.isAtivo());

        var ordem = inOrder(usuarios, sessoes, auditoria);

        ordem.verify(usuarios).buscarPorPerfilComBloqueio(Perfil.DIRECAO);

        ordem.verify(usuarios).flush();

        ordem.verify(sessoes).revogarTodasDoUsuario(diretor.getId(), agora);

        ordem.verify(auditoria).aposCommit("DIRECAO_INATIVADA", diretor.getId(), diretor.getId());
    }

    @Test
    void deveImpedirInativacaoDaUltimaDirecaoAtiva() {

        when(usuarios.buscarPorPerfilComBloqueio(Perfil.DIRECAO)).thenReturn(List.of(diretor));

        assertThrows(RegraNegocioException.class, () -> service.inativarMinhaConta(0L, solicitante));

        verificarSemInativacao();
    }

    @Test
    void naoDeveContarDirecaoInativaComoSubstituta() {

        outroDiretor.setAtivo(false);
        outroDiretor.setSituacaoCadastro(SituacaoCadastro.INATIVO);

        prepararDiretores();

        assertThrows(RegraNegocioException.class, () -> service.inativarMinhaConta(0L, solicitante));

        verificarSemInativacao();
    }

    @Test
    void naoDeveContarDirecaoPendenteComoSubstituta() {

        outroDiretor.setSituacaoCadastro(SituacaoCadastro.PENDENTE_APROVACAO);

        prepararDiretores();

        assertThrows(RegraNegocioException.class, () -> service.inativarMinhaConta(0L, solicitante));

        verificarSemInativacao();
    }

    @Test
    void deveRejeitarVersaoDesatualizada() {

        prepararDiretores();

        assertThrows(ConflitoVersaoException.class, () -> service.inativarMinhaConta(1L, solicitante));

        verificarSemInativacao();
    }

    @Test
    void deveRejeitarVersaoNula() {

        prepararDiretores();

        assertThrows(ConflitoVersaoException.class, () -> service.inativarMinhaConta(null, solicitante));

        verificarSemInativacao();
    }

    @Test
    void deveNegarSolicitanteNaoAutenticado() {

        assertThrows(AccessDeniedException.class, () -> service.inativarMinhaConta(0L, null));

        verifyNoInteractions(usuarios, sessoes, auditoria);
    }

    @Test
    void deveNegarSolicitanteSemId() {

        var semId = new UsuarioAtual(null, Perfil.DIRECAO);

        assertThrows(AccessDeniedException.class, () -> service.inativarMinhaConta(0L, semId));

        verifyNoInteractions(usuarios, sessoes, auditoria);
    }

    @Test
    void deveNegarProfessor() {

        var professor = new UsuarioAtual(UUID.randomUUID(), Perfil.PROFESSOR);

        assertThrows(AccessDeniedException.class, () -> service.inativarMinhaConta(0L, professor));

        verifyNoInteractions(usuarios, sessoes, auditoria);
    }

    @Test
    void deveNegarResponsavel() {

        var responsavel = new UsuarioAtual(UUID.randomUUID(), Perfil.RESPONSAVEL);

        assertThrows(AccessDeniedException.class, () -> service.inativarMinhaConta(0L, responsavel));

        verifyNoInteractions(usuarios, sessoes, auditoria);
    }

    @Test
    void deveNegarDiretorInativoNoBanco() {

        diretor.setAtivo(false);
        prepararDiretores();

        assertThrows(AccessDeniedException.class, () -> service.inativarMinhaConta(0L, solicitante));

        verify(usuarios, never()).flush();
        verifyNoInteractions(sessoes, auditoria);
    }

    @Test
    void deveNegarDiretorNaoAprovado() {

        diretor.setSituacaoCadastro(SituacaoCadastro.PENDENTE_APROVACAO);

        prepararDiretores();

        assertThrows(AccessDeniedException.class, () -> service.inativarMinhaConta(0L, solicitante));

        verificarSemInativacao();
    }

    @Test
    void deveNegarUsuarioQueNaoConstaEntreDiretores() {

        when(usuarios.buscarPorPerfilComBloqueio(Perfil.DIRECAO)).thenReturn(List.of(outroDiretor));

        assertThrows(AccessDeniedException.class, () -> service.inativarMinhaConta(0L, solicitante));

        verificarSemInativacao();
    }

    private void prepararDiretores() {

        when(usuarios.buscarPorPerfilComBloqueio(Perfil.DIRECAO)).thenReturn(List.of(diretor, outroDiretor));
    }

    private void verificarSemInativacao() {

        assertTrue(diretor.isAtivo());
        verify(usuarios, never()).flush();
        verifyNoInteractions(sessoes, auditoria);
    }

    private Usuario criarDiretor() {

        var usuario = new Usuario();

        usuario.setId(UUID.randomUUID());
        usuario.setNome("Direção de teste");
        usuario.setEmail(usuario.getId() + "@example.com");
        usuario.setTelefone("11999999999");
        usuario.setPerfil(Perfil.DIRECAO);
        usuario.setAtivo(true);
        usuario.setSituacaoCadastro(SituacaoCadastro.APROVADO);
        usuario.setVersao(0L);

        return usuario;
    }
}