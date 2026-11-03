package com.crecheconecta.service;

import com.crecheconecta.dto.AtualizarProfessorRequest;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.SituacaoCadastro;
import com.crecheconecta.entity.Usuario;
import com.crecheconecta.exception.ConflitoVersaoException;
import com.crecheconecta.exception.ProfessorNaoEncontradoException;
import com.crecheconecta.exception.RegraNegocioException;
import com.crecheconecta.repository.SessaoRepository;
import com.crecheconecta.repository.TurmaProfessorRepository;
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
class ProfessorServiceTest {

    @Mock
    private UsuarioRepository usuarios;

    @Mock
    private SessaoRepository sessoes;

    @Mock
    private AuditoriaService auditoria;

    @Mock
    private TurmaProfessorRepository vinculos;

    private ProfessorService service;
    private Usuario diretor;
    private Usuario professor;
    private UsuarioAtual solicitante;
    private Instant agora;

    @BeforeEach
    void preparar() {
        agora = Instant.parse("2026-11-02T12:00:00Z");

        service = new ProfessorService(
                usuarios,
                sessoes,
                auditoria,
                vinculos,
                Clock.fixed(agora, ZoneOffset.UTC)
        );

        diretor = criarUsuario(Perfil.DIRECAO);
        professor = criarUsuario(Perfil.PROFESSOR);

        solicitante = new UsuarioAtual(
                diretor.getId(),
                Perfil.DIRECAO
        );
    }

    @Test
    void deveListarProfessoresSemFiltroDeSituacao() {
        autorizar();

        when(usuarios.findByPerfil(
                eq(Perfil.PROFESSOR),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(professor)));

        var resposta = service.listar(0, 20, null, solicitante);

        assertEquals(1, resposta.getTotalElements());
        assertEquals(
                professor.getId(),
                resposta.getContent().getFirst().id()
        );

        verify(usuarios, never()).findByPerfilAndSituacaoCadastro(
                any(), any(), any(Pageable.class)
        );
    }

    @Test
    void deveListarProfessoresFiltrandoSituacao() {
        autorizar();

        when(usuarios.findByPerfilAndSituacaoCadastro(
                eq(Perfil.PROFESSOR),
                eq(SituacaoCadastro.APROVADO),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(professor)));

        var resposta = service.listar(
                0,
                20,
                SituacaoCadastro.APROVADO,
                solicitante
        );

        assertEquals(1, resposta.getTotalElements());

        verify(usuarios, never()).findByPerfil(
                any(), any(Pageable.class)
        );
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

        verify(usuarios, never()).findByPerfil(
                any(), any(Pageable.class)
        );
    }

    @Test
    void deveVisualizarProfessor() {
        autorizar();

        when(usuarios.findById(professor.getId()))
                .thenReturn(Optional.of(professor));

        var resposta = service.visualizar(
                professor.getId(),
                solicitante
        );

        assertEquals(professor.getId(), resposta.id());
        assertEquals(professor.getEmail(), resposta.email());
    }

    @Test
    void deveRejeitarProfessorNaoEncontrado() {
        autorizar();

        assertThrows(
                ProfessorNaoEncontradoException.class,
                () -> service.visualizar(
                        professor.getId(), solicitante
                )
        );
    }

    @Test
    void naoDeveTratarResponsavelComoProfessor() {
        autorizar();

        professor.setPerfil(Perfil.RESPONSAVEL);

        when(usuarios.findById(professor.getId()))
                .thenReturn(Optional.of(professor));

        assertThrows(
                ProfessorNaoEncontradoException.class,
                () -> service.visualizar(
                        professor.getId(), solicitante
                )
        );
    }

    @Test
    void deveAtualizarNomeETelefonePreservandoCredenciais() {
        prepararProfessorComBloqueio();

        String emailAnterior = professor.getEmail();
        String senhaAnterior = professor.getSenhaHash();

        var resposta = service.atualizar(
                professor.getId(),
                new AtualizarProfessorRequest(
                        " Novo nome ",
                        "21988888888",
                        0L
                ),
                solicitante
        );

        assertEquals("Novo nome", resposta.nome());
        assertEquals("21988888888", resposta.telefone());

        assertEquals(emailAnterior, professor.getEmail());
        assertEquals(senhaAnterior, professor.getSenhaHash());
        assertEquals(Perfil.PROFESSOR, professor.getPerfil());
        assertTrue(professor.isAtivo());

        verify(usuarios).flush();

        verify(auditoria).aposCommit(
                "PROFESSOR_ATUALIZADO",
                diretor.getId(),
                professor.getId()
        );
    }

    @Test
    void deveRejeitarAtualizacaoComNomeEmBranco() {
        prepararProfessorComBloqueio();

        assertThrows(
                RegraNegocioException.class,
                () -> service.atualizar(
                        professor.getId(),
                        new AtualizarProfessorRequest(
                                "   ",
                                "11999999999",
                                0L
                        ),
                        solicitante
                )
        );

        assertEquals("Usuário de teste", professor.getNome());
        verificarSemEscrita();
    }

    @Test
    void deveInativarEncerrarVinculosERevogarSessoes() {
        prepararProfessorComBloqueio();

        var resposta = service.inativar(
                professor.getId(),
                0L,
                solicitante
        );

        assertFalse(resposta.ativo());
        assertEquals(
                SituacaoCadastro.INATIVO,
                resposta.situacaoCadastro()
        );

        verify(usuarios).flush();

        verify(vinculos).encerrarDoProfessor(
                professor.getId(), agora
        );

        verify(sessoes).revogarTodasDoUsuario(
                professor.getId(), agora
        );

        verify(auditoria).aposCommit(
                "PROFESSOR_INATIVADO",
                diretor.getId(),
                professor.getId()
        );
    }

    @ParameterizedTest
    @EnumSource(
            value = SituacaoCadastro.class,
            names = {"PENDENTE_APROVACAO", "REJEITADO", "INATIVO"}
    )
    void deveRejeitarInativacaoDeCadastroNaoAprovado(
            SituacaoCadastro situacao
    ) {
        professor.setSituacaoCadastro(situacao);
        prepararProfessorComBloqueio();

        assertThrows(
                RegraNegocioException.class,
                () -> service.inativar(
                        professor.getId(), 0L, solicitante
                )
        );

        verificarSemEscrita();
    }

    @Test
    void deveRejeitarVersaoAntigaNaAtualizacao() {
        prepararProfessorComBloqueio();

        assertThrows(
                ConflitoVersaoException.class,
                () -> service.atualizar(
                        professor.getId(),
                        new AtualizarProfessorRequest(
                                "Novo nome",
                                "21988888888",
                                1L
                        ),
                        solicitante
                )
        );

        assertEquals("Usuário de teste", professor.getNome());
        verificarSemEscrita();
    }

    @Test
    void deveExigirVersaoNaInativacao() {
        prepararProfessorComBloqueio();

        assertThrows(
                ConflitoVersaoException.class,
                () -> service.inativar(
                        professor.getId(), null, solicitante
                )
        );

        assertTrue(professor.isAtivo());
        verificarSemEscrita();
    }

    @Test
    void deveRejeitarVersaoAntigaNaInativacao() {
        prepararProfessorComBloqueio();

        assertThrows(
                ConflitoVersaoException.class,
                () -> service.inativar(
                        professor.getId(), 1L, solicitante
                )
        );

        assertTrue(professor.isAtivo());
        verificarSemEscrita();
    }

    @Test
    void deveNegarSolicitanteNaoAutenticado() {
        assertThrows(
                AccessDeniedException.class,
                () -> service.inativar(
                        professor.getId(), 0L, null
                )
        );

        verifyNoInteractions(usuarios, sessoes, vinculos, auditoria);
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
                () -> service.inativar(
                        professor.getId(), 0L, outro
                )
        );

        verifyNoInteractions(usuarios, sessoes, vinculos, auditoria);
    }

    @Test
    void deveNegarDiretorInativo() {
        diretor.setAtivo(false);
        autorizar();

        assertThrows(
                AccessDeniedException.class,
                () -> service.inativar(
                        professor.getId(), 0L, solicitante
                )
        );

        verificarSemEscrita();
    }

    @Test
    void deveConferirPerfilDoDiretorNoBanco() {
        diretor.setPerfil(Perfil.PROFESSOR);
        autorizar();

        assertThrows(
                AccessDeniedException.class,
                () -> service.inativar(
                        professor.getId(), 0L, solicitante
                )
        );

        verificarSemEscrita();
    }

    private void autorizar() {
        when(usuarios.findById(diretor.getId()))
                .thenReturn(Optional.of(diretor));
    }

    private void prepararProfessorComBloqueio() {
        autorizar();

        when(usuarios.buscarPorIdComBloqueio(professor.getId()))
                .thenReturn(Optional.of(professor));
    }

    private void verificarSemEscrita() {
        verify(usuarios, never()).flush();
        verifyNoInteractions(sessoes, vinculos, auditoria);
    }

    private Usuario criarUsuario(Perfil perfil) {
        var usuario = new Usuario();

        usuario.setId(UUID.randomUUID());
        usuario.setNome("Usuário de teste");
        usuario.setEmail(usuario.getId() + "@example.com");
        usuario.setTelefone("11999999999");
        usuario.setSenhaHash("hash-de-teste");
        usuario.setPerfil(perfil);
        usuario.setAtivo(true);
        usuario.setSituacaoCadastro(SituacaoCadastro.APROVADO);
        usuario.setVersao(0L);

        return usuario;
    }
}