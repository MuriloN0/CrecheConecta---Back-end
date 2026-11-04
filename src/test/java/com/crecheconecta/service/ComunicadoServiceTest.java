package com.crecheconecta.service;

import com.crecheconecta.dto.AtualizarComunicadoRequest;
import com.crecheconecta.dto.CriarComunicadoRequest;
import com.crecheconecta.entity.*;
import com.crecheconecta.exception.ComunicadoNaoEncontradoException;
import com.crecheconecta.exception.ConflitoVersaoException;
import com.crecheconecta.exception.RegraNegocioException;
import com.crecheconecta.repository.ComunicadoRepository;
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
class ComunicadoServiceTest {

    @Mock
    private ComunicadoRepository comunicados;

    @Mock
    private UsuarioRepository usuarios;

    @Mock
    private AuditoriaService auditoria;

    private ComunicadoService service;
    private Usuario usuario;
    private UsuarioAtual solicitante;
    private Comunicado comunicado;
    private Instant agora;

    @BeforeEach
    void preparar() {
        agora = Instant.parse("2026-11-02T12:00:00Z");

        service = new ComunicadoService(
                comunicados,
                usuarios,
                auditoria,
                Clock.fixed(agora, ZoneOffset.UTC)
        );

        usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        usuario.setPerfil(Perfil.DIRECAO);
        usuario.setAtivo(true);
        usuario.setSituacaoCadastro(SituacaoCadastro.APROVADO);

        definirPerfil(Perfil.DIRECAO);

        comunicado = new Comunicado();
        comunicado.setId(UUID.randomUUID());
        comunicado.setTitulo("Reunião");
        comunicado.setConteudo("Reunião de responsáveis.");
        comunicado.setCriadoPor(usuario);
        comunicado.setCriadoEm(agora.minusSeconds(3600));
        comunicado.setAtualizadoEm(comunicado.getCriadoEm());
        comunicado.setAtivo(true);
        comunicado.setVersao(0L);
    }

    @Test
    void deveCriarComunicado() {

        autorizar();

        doAnswer(invocation -> {
            Comunicado salvo = invocation.getArgument(0);
            salvo.setId(UUID.randomUUID());
            salvo.setVersao(0L);
            return salvo;
        }).when(comunicados).saveAndFlush(any(Comunicado.class));

        var resposta = service.criar(
                new CriarComunicadoRequest(" Reunião ", " Amanhã às 18h. "),
                solicitante
        );

        assertEquals("Reunião", resposta.titulo());
        assertEquals("Amanhã às 18h.", resposta.conteudo());
        assertEquals(agora, resposta.criadoEm());
        assertEquals(agora, resposta.atualizadoEm());
        assertTrue(resposta.ativo());

        var captor = ArgumentCaptor.forClass(Comunicado.class);
        verify(comunicados).saveAndFlush(captor.capture());

        assertSame(usuario, captor.getValue().getCriadoPor());

        verify(auditoria).aposCommit("COMUNICADO_CRIADO", usuario.getId(), resposta.id());
    }

    @ParameterizedTest
    @EnumSource(Perfil.class)
    void devePermitirLeituraParaOsTresPerfis(Perfil perfil) {

        definirPerfil(perfil);
        autorizar();

        when(comunicados.findByIdAndAtivoTrue(comunicado.getId()))
                .thenReturn(Optional.of(comunicado));

        var resposta = service.visualizar(comunicado.getId(), solicitante);

        assertEquals(comunicado.getId(), resposta.id());
    }

    @Test
    void deveListarSomenteAtivosComOrdenacao() {

        autorizar();

        when(comunicados.findByAtivoTrue(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(comunicado)));

        var resposta = service.listar(0, 20, solicitante);

        assertEquals(1, resposta.getTotalElements());

        var captor = ArgumentCaptor.forClass(Pageable.class);
        verify(comunicados).findByAtivoTrue(captor.capture());

        assertEquals(0, captor.getValue().getPageNumber());
        assertEquals(20, captor.getValue().getPageSize());

        assertEquals(Sort.by("criadoEm").descending().and(Sort.by("id").descending()), captor.getValue().getSort());
    }

    @ParameterizedTest
    @CsvSource({"-1,20", "0,0", "0,-1", "0,101"})
    void deveRejeitarPaginacaoInvalida(int pagina, int tamanho) {

        autorizar();

        assertThrows(RegraNegocioException.class, () -> service.listar(pagina, tamanho, solicitante));

        verifyNoInteractions(comunicados);
    }

    @Test
    void deveRetornarNaoEncontradoParaAusenteOuInativo() {
        autorizar();

        when(comunicados.findByIdAndAtivoTrue(comunicado.getId()))
                .thenReturn(Optional.empty());

        assertThrows(ComunicadoNaoEncontradoException.class, () -> service.visualizar(comunicado.getId(), solicitante));
    }

    @Test
    void deveAtualizarPreservandoAutoriaEDataDeCriacao() {

        autorizar();
        prepararBloqueio();

        var autorOriginal = new Usuario();
        autorOriginal.setId(UUID.randomUUID());
        comunicado.setCriadoPor(autorOriginal);

        var criadoEm = comunicado.getCriadoEm();

        var resposta = service.atualizar(
                comunicado.getId(),
                new AtualizarComunicadoRequest(" Novo título ", " Novo conteúdo ", 0L),
                solicitante
        );

        assertEquals("Novo título", resposta.titulo());
        assertEquals("Novo conteúdo", resposta.conteudo());
        assertEquals(criadoEm, resposta.criadoEm());
        assertEquals(agora, resposta.atualizadoEm());
        assertSame(autorOriginal, comunicado.getCriadoPor());

        verify(comunicados).flush();

        verify(auditoria).aposCommit("COMUNICADO_ATUALIZADO", usuario.getId(), comunicado.getId());
    }

    @Test
    void deveInativarSemExcluirHistorico() {
        autorizar();
        prepararBloqueio();

        var resposta = service.inativar(comunicado.getId(), 0L, solicitante);

        assertFalse(resposta.ativo());
        assertEquals(agora, resposta.atualizadoEm());
        assertEquals("Reunião", comunicado.getTitulo());

        verify(comunicados).flush();

        verify(auditoria).aposCommit("COMUNICADO_INATIVADO", usuario.getId(), comunicado.getId());
    }

    @Test
    void deveRejeitarAtualizacaoComVersaoAntiga() {

        autorizar();
        prepararBloqueio();

        assertThrows(
                ConflitoVersaoException.class,
                () -> service.atualizar(
                        comunicado.getId(),
                        new AtualizarComunicadoRequest("Novo título", "Novo conteúdo", 1L),
                        solicitante
                )
        );

        assertEquals("Reunião", comunicado.getTitulo());
        verificarSemEscrita();
    }

    @Test
    void deveRejeitarInativacaoComVersaoNula() {

        autorizar();
        prepararBloqueio();

        assertThrows(
                ConflitoVersaoException.class,
                () -> service.inativar(
                        comunicado.getId(),
                        null,
                        solicitante
                )
        );

        assertTrue(comunicado.isAtivo());
        verificarSemEscrita();
    }

    @Test
    void deveRejeitarInativacaoComVersaoAntiga() {

        autorizar();
        prepararBloqueio();

        assertThrows(
                ConflitoVersaoException.class,
                () -> service.inativar(
                        comunicado.getId(),
                        1L,
                        solicitante
                )
        );

        assertTrue(comunicado.isAtivo());
        verificarSemEscrita();
    }

    @Test
    void deveRejeitarAlteracaoDeComunicadoAusenteOuInativo() {

        autorizar();

        when(comunicados.buscarAtivoComBloqueio(comunicado.getId()))
                .thenReturn(Optional.empty());

        assertThrows(
                ComunicadoNaoEncontradoException.class,
                () -> service.inativar(
                        comunicado.getId(),
                        0L,
                        solicitante
                )
        );

        verificarSemEscrita();
    }

    @ParameterizedTest
    @EnumSource(value = Perfil.class, names = {"PROFESSOR", "RESPONSAVEL"})
    void deveImpedirEscritaDeProfessorEResponsavel(Perfil perfil) {

        definirPerfil(perfil);
        autorizar();

        assertThrows(
                AccessDeniedException.class,
                () -> service.criar(
                        new CriarComunicadoRequest("Título", "Conteúdo"),
                        solicitante
                )
        );

        assertThrows(
                AccessDeniedException.class,
                () -> service.atualizar(
                        comunicado.getId(),
                        new AtualizarComunicadoRequest("Título", "Conteúdo", 0L),
                        solicitante
                )
        );

        assertThrows(AccessDeniedException.class, () -> service.inativar(comunicado.getId(), 0L, solicitante));

        verifyNoInteractions(comunicados, auditoria);
    }

    @Test
    void deveNegarUsuarioNaoAutenticado() {

        assertThrows(AccessDeniedException.class, () -> service.listar(0, 20, null));

        verifyNoInteractions(usuarios, comunicados, auditoria);
    }

    @Test
    void deveNegarUsuarioInativo() {

        usuario.setAtivo(false);
        autorizar();

        assertThrows(AccessDeniedException.class, () -> service.listar(0, 20, solicitante));

        verifyNoInteractions(comunicados);
    }

    @Test
    void deveNegarUsuarioNaoAprovado() {

        usuario.setSituacaoCadastro(SituacaoCadastro.PENDENTE_APROVACAO);
        autorizar();

        assertThrows(AccessDeniedException.class, () -> service.listar(0, 20, solicitante));

        verifyNoInteractions(comunicados);
    }

    @Test
    void deveConferirPerfilAtualNoBanco() {

        autorizar();
        usuario.setPerfil(Perfil.PROFESSOR);

        assertThrows(AccessDeniedException.class, () -> service.criar(new CriarComunicadoRequest("Título", "Conteúdo"), solicitante));

        verifyNoInteractions(comunicados);
    }

    @ParameterizedTest
    @MethodSource("conteudosInvalidos")
    void deveRejeitarCriacaoComConteudoInvalido(CriarComunicadoRequest request) {

        autorizar();

        assertThrows(RegraNegocioException.class, () -> service.criar(request, solicitante));

        verifyNoInteractions(comunicados, auditoria);
    }

    @Test
    void deveRejeitarAtualizacaoComConteudoEmBranco() {

        autorizar();
        prepararBloqueio();

        assertThrows(RegraNegocioException.class, () -> service.atualizar(comunicado.getId(), new AtualizarComunicadoRequest("Novo título", "   ", 0L), solicitante));

        assertEquals("Reunião", comunicado.getTitulo());
        verificarSemEscrita();
    }

    static Stream<CriarComunicadoRequest> conteudosInvalidos() {
        return Stream.of(
                new CriarComunicadoRequest(null, "Conteúdo"),
                new CriarComunicadoRequest("   ", "Conteúdo"),
                new CriarComunicadoRequest("A".repeat(151), "Conteúdo"),
                new CriarComunicadoRequest("Título", null),
                new CriarComunicadoRequest("Título", "   "),
                new CriarComunicadoRequest("Título", "A".repeat(5001))
        );
    }

    private void definirPerfil(Perfil perfil) {

        usuario.setPerfil(perfil);
        solicitante = new UsuarioAtual(usuario.getId(), perfil);
    }

    private void autorizar() {

        when(usuarios.findById(usuario.getId()))
                .thenReturn(Optional.of(usuario));
    }

    private void prepararBloqueio() {

        when(comunicados.buscarAtivoComBloqueio(comunicado.getId()))
                .thenReturn(Optional.of(comunicado));
    }

    private void verificarSemEscrita() {

        verify(comunicados, never()).flush();
        verify(comunicados, never()).saveAndFlush(any());
        verifyNoInteractions(auditoria);
    }
}