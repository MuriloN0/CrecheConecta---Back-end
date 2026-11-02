package com.crecheconecta.service;

import com.crecheconecta.dto.AtualizarDirecaoRequest;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DirecaoConsultaAtualizacaoServiceTest {

    @Mock
    private UsuarioRepository usuarios;

    @Mock
    private SessaoRepository sessoes;

    @Mock
    private AuditoriaService auditoria;

    private DirecaoService service;
    private Usuario diretor;
    private UsuarioAtual solicitante;

    @BeforeEach
    void preparar() {
        var clock = Clock.fixed(
                Instant.parse("2026-11-02T12:00:00Z"),
                ZoneOffset.UTC
        );

        service = new DirecaoService(
                usuarios,
                sessoes,
                auditoria,
                clock
        );

        diretor = criarDiretor();

        solicitante = new UsuarioAtual(
                diretor.getId(),
                Perfil.DIRECAO
        );
    }

    @Test
    void deveListarDiretoresComPaginacaoEOrdenacao() {
        prepararConsulta();

        var inativo = criarDiretor();
        inativo.setAtivo(false);
        inativo.setSituacaoCadastro(SituacaoCadastro.INATIVO);

        when(usuarios.findByPerfil(
                eq(Perfil.DIRECAO),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(diretor, inativo)));

        var resposta = service.listar(0, 20, solicitante);

        assertEquals(2, resposta.getTotalElements());
        assertEquals(diretor.getId(), resposta.getContent().get(0).id());
        assertFalse(resposta.getContent().get(1).ativo());

        var captor = ArgumentCaptor.forClass(Pageable.class);

        verify(usuarios).findByPerfil(
                eq(Perfil.DIRECAO),
                captor.capture()
        );

        var paginacao = captor.getValue();

        assertEquals(0, paginacao.getPageNumber());
        assertEquals(20, paginacao.getPageSize());

        assertEquals(
                Sort.by("nome").ascending()
                        .and(Sort.by("id").ascending()),
                paginacao.getSort()
        );

        verifyNoInteractions(sessoes, auditoria);
    }

    @ParameterizedTest
    @CsvSource({
            "-1, 20",
            "0, 0",
            "0, -1",
            "0, 101"
    })
    void deveRejeitarPaginacaoInvalida(int pagina, int tamanho) {
        prepararConsulta();

        assertThrows(
                RegraNegocioException.class,
                () -> service.listar(pagina, tamanho, solicitante)
        );

        verify(usuarios, never()).findByPerfil(
                any(Perfil.class),
                any(Pageable.class)
        );
    }

    @Test
    void deveNegarListagemSemAutenticacao() {
        assertThrows(
                AccessDeniedException.class,
                () -> service.listar(0, 20, null)
        );

        verifyNoInteractions(usuarios, sessoes, auditoria);
    }

    @Test
    void deveNegarListagemDeDiretorInativo() {
        diretor.setAtivo(false);
        prepararConsulta();

        assertThrows(
                AccessDeniedException.class,
                () -> service.listar(0, 20, solicitante)
        );

        verify(usuarios, never()).findByPerfil(
                any(Perfil.class),
                any(Pageable.class)
        );
    }

    @Test
    void deveAtualizarSomenteNomeETelefone() {
        prepararAtualizacao();

        String emailAnterior = diretor.getEmail();
        String senhaAnterior = diretor.getSenhaHash();

        var request = new AtualizarDirecaoRequest(
                " Maria da Silva ",
                "21988888888",
                0L
        );

        var resposta = service.atualizarMinhaConta(
                request,
                solicitante
        );

        assertEquals("Maria da Silva", resposta.nome());
        assertEquals("21988888888", resposta.telefone());
        assertEquals(diretor.getId(), resposta.id());

        assertEquals(emailAnterior, diretor.getEmail());
        assertEquals(senhaAnterior, diretor.getSenhaHash());
        assertEquals(Perfil.DIRECAO, diretor.getPerfil());
        assertEquals(
                SituacaoCadastro.APROVADO,
                diretor.getSituacaoCadastro()
        );
        assertTrue(diretor.isAtivo());

        verify(usuarios).buscarPorIdComBloqueio(solicitante.id());
        verify(usuarios).flush();

        verify(auditoria).aposCommit(
                "DIRECAO_ATUALIZADA",
                diretor.getId(),
                diretor.getId()
        );

        verifyNoInteractions(sessoes);
    }

    @Test
    void deveRejeitarVersaoDesatualizada() {
        prepararAtualizacao();

        var request = new AtualizarDirecaoRequest(
                "Maria",
                "11999999999",
                1L
        );

        assertThrows(
                ConflitoVersaoException.class,
                () -> service.atualizarMinhaConta(request, solicitante)
        );

        verificarSemAlteracao();
    }

    @Test
    void deveRejeitarVersaoNula() {
        prepararAtualizacao();

        var request = new AtualizarDirecaoRequest(
                "Maria",
                "11999999999",
                null
        );

        assertThrows(
                ConflitoVersaoException.class,
                () -> service.atualizarMinhaConta(request, solicitante)
        );

        verificarSemAlteracao();
    }

    @Test
    void deveRejeitarNomeEmBranco() {
        prepararAtualizacao();

        var request = new AtualizarDirecaoRequest(
                "   ",
                "11999999999",
                0L
        );

        assertThrows(
                RegraNegocioException.class,
                () -> service.atualizarMinhaConta(request, solicitante)
        );

        verificarSemAlteracao();
    }

    @Test
    void deveRejeitarNomeAcimaDoLimite() {
        prepararAtualizacao();

        var request = new AtualizarDirecaoRequest(
                "A".repeat(151),
                "11999999999",
                0L
        );

        assertThrows(
                RegraNegocioException.class,
                () -> service.atualizarMinhaConta(request, solicitante)
        );

        verificarSemAlteracao();
    }

    @Test
    void deveRejeitarTelefoneInvalido() {
        prepararAtualizacao();

        var request = new AtualizarDirecaoRequest(
                "Maria",
                "123",
                0L
        );

        assertThrows(
                RegraNegocioException.class,
                () -> service.atualizarMinhaConta(request, solicitante)
        );

        verificarSemAlteracao();
    }

    @ParameterizedTest
    @EnumSource(
            value = Perfil.class,
            names = {"PROFESSOR", "RESPONSAVEL"}
    )
    void deveNegarAtualizacaoPorOutroPerfil(Perfil perfil) {
        var outroPerfil = new UsuarioAtual(
                UUID.randomUUID(),
                perfil
        );

        assertThrows(
                AccessDeniedException.class,
                () -> service.atualizarMinhaConta(
                        requestValido(),
                        outroPerfil
                )
        );

        verifyNoInteractions(usuarios, sessoes, auditoria);
    }

    @Test
    void deveNegarAtualizacaoSemAutenticacao() {
        assertThrows(
                AccessDeniedException.class,
                () -> service.atualizarMinhaConta(requestValido(), null)
        );

        verifyNoInteractions(usuarios, sessoes, auditoria);
    }

    @Test
    void deveNegarAtualizacaoDeUsuarioNaoEncontrado() {
        when(usuarios.buscarPorIdComBloqueio(diretor.getId()))
                .thenReturn(Optional.empty());

        assertThrows(
                AccessDeniedException.class,
                () -> service.atualizarMinhaConta(
                        requestValido(),
                        solicitante
                )
        );

        verificarSemAlteracao();
    }

    @Test
    void deveConferirPerfilAtualNoBanco() {
        diretor.setPerfil(Perfil.PROFESSOR);
        prepararAtualizacao();

        assertThrows(
                AccessDeniedException.class,
                () -> service.atualizarMinhaConta(
                        requestValido(),
                        solicitante
                )
        );

        verificarSemAlteracao();
    }

    @Test
    void deveNegarAtualizacaoDeDiretorInativo() {
        diretor.setAtivo(false);
        prepararAtualizacao();

        assertThrows(
                AccessDeniedException.class,
                () -> service.atualizarMinhaConta(
                        requestValido(),
                        solicitante
                )
        );

        verificarSemAlteracao();
    }

    @Test
    void deveNegarAtualizacaoDeDiretorNaoAprovado() {
        diretor.setSituacaoCadastro(
                SituacaoCadastro.PENDENTE_APROVACAO
        );

        prepararAtualizacao();

        assertThrows(
                AccessDeniedException.class,
                () -> service.atualizarMinhaConta(
                        requestValido(),
                        solicitante
                )
        );

        verificarSemAlteracao();
    }

    private void prepararConsulta() {
        when(usuarios.findById(diretor.getId()))
                .thenReturn(Optional.of(diretor));
    }

    private void prepararAtualizacao() {
        when(usuarios.buscarPorIdComBloqueio(diretor.getId()))
                .thenReturn(Optional.of(diretor));
    }

    private void verificarSemAlteracao() {
        assertEquals("Direção de teste", diretor.getNome());
        assertEquals("11999999999", diretor.getTelefone());

        verify(usuarios, never()).flush();
        verifyNoInteractions(sessoes, auditoria);
    }

    private AtualizarDirecaoRequest requestValido() {
        return new AtualizarDirecaoRequest(
                "Maria",
                "21988888888",
                0L
        );
    }

    private Usuario criarDiretor() {
        var usuario = new Usuario();

        usuario.setId(UUID.randomUUID());
        usuario.setNome("Direção de teste");
        usuario.setEmail(usuario.getId() + "@example.com");
        usuario.setTelefone("11999999999");
        usuario.setSenhaHash("hash-de-teste");
        usuario.setPerfil(Perfil.DIRECAO);
        usuario.setAtivo(true);
        usuario.setSituacaoCadastro(SituacaoCadastro.APROVADO);
        usuario.setVersao(0L);

        return usuario;
    }
}