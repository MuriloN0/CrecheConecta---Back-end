package com.crecheconecta.service;

import com.crecheconecta.dto.AtualizarTermoVersaoRequest;
import com.crecheconecta.dto.TermoVersaoRequest;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.StatusTermo;
import com.crecheconecta.entity.TermoVersao;
import com.crecheconecta.exception.ConflitoVersaoException;
import com.crecheconecta.exception.RegraNegocioException;
import com.crecheconecta.exception.TermoNaoEncontradoException;
import com.crecheconecta.repository.AceiteTermoRepository;
import com.crecheconecta.repository.TermoVersaoRepository;
import com.crecheconecta.security.UsuarioAtual;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TermoVersaoServiceTest {
    private final TermoVersaoRepository termos = mock(TermoVersaoRepository.class);
    private final AceiteTermoRepository aceites = mock(AceiteTermoRepository.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final Instant agora = Instant.parse("2026-11-20T12:00:00Z");
    private final TermoVersaoService service = new TermoVersaoService(
            termos, aceites, auditoria, Clock.fixed(agora, ZoneOffset.UTC));

    private final UsuarioAtual direcao =
            new UsuarioAtual(UUID.randomUUID(), Perfil.DIRECAO);
    private final TermoVersao rascunho = termo(StatusTermo.RASCUNHO, "v2");

    @Test
    void criarGeraIdentificadorENormalizaTextosComoRascunho() {
        when(termos.proximoNumeroIdentificador()).thenReturn(3L);
        when(termos.saveAndFlush(any())).thenAnswer(chamada -> {
            TermoVersao termo = chamada.getArgument(0);
            termo.setId(UUID.randomUUID());
            return termo;
        });

        var resposta = service.criar(
                new TermoVersaoRequest("  Uso  ", "  Privacidade  "), direcao);

        var captor = ArgumentCaptor.forClass(TermoVersao.class);
        verify(termos).saveAndFlush(captor.capture());
        var salvo = captor.getValue();

        assertEquals("v3", resposta.identificador());
        assertEquals(StatusTermo.RASCUNHO, resposta.status());
        assertEquals("Uso", resposta.termoUso());
        assertEquals("Privacidade", resposta.politicaPrivacidade());
        assertEquals(0, resposta.aceites());
        assertEquals(direcao.id(), salvo.getCriadoPor());
        assertEquals(agora, salvo.getCriadoEm());
        assertEquals(agora, salvo.getAtualizadoEm());
        assertNull(salvo.getPublicadoEm());

        verify(auditoria).aposCommit(
                "TERMO_RASCUNHO_CRIADO", direcao.id(), resposta.id());
    }

    @Test
    void listarAssociaTotaisDeAceiteSemInventarAceitesParaRascunho() {
        var vigente = termo(StatusTermo.VIGENTE, "v1");
        var total = mock(AceiteTermoRepository.AceitesPorVersao.class);

        when(total.getIdentificador()).thenReturn("v1");
        when(total.getTotal()).thenReturn(7L);
        when(aceites.contarPorVersao()).thenReturn(List.of(total));
        when(termos.findAllByOrderByCriadoEmDescIdDesc())
                .thenReturn(List.of(rascunho, vigente));

        var resposta = service.listar();

        assertEquals(2, resposta.size());
        assertEquals(0, resposta.getFirst().aceites());
        assertEquals(7, resposta.getLast().aceites());
        assertEquals("v1", resposta.getLast().identificador());
    }

    @Test
    void listarPaginadoPreservaTotalEPossuiTermoVigente() {
        var pagina = PageRequest.of(
                1, 10, Sort.by(Sort.Direction.DESC, "criadoEm", "id"));

        when(termos.findAll(pagina))
                .thenReturn(new PageImpl<>(List.of(rascunho), pagina, 11));
        when(termos.findByStatus(StatusTermo.VIGENTE))
                .thenReturn(Optional.of(termo(StatusTermo.VIGENTE, "v1")));

        var resposta = service.listarPaginado(1, null);

        assertEquals(2, resposta.totalPages());
        assertEquals(rascunho.getId(), resposta.content().getFirst().id());
        assertEquals("v1", resposta.vigente().identificador());
    }

    @Test
    void listarPaginadoFiltraStatusEAceitaAusenciaDeVigente() {
        var pagina = PageRequest.of(
                0, 10, Sort.by(Sort.Direction.DESC, "criadoEm", "id"));

        when(termos.findAllByStatus(StatusTermo.RASCUNHO, pagina))
                .thenReturn(new PageImpl<>(List.of(rascunho), pagina, 1));

        var resposta = service.listarPaginado(0, StatusTermo.RASCUNHO);

        assertEquals(1, resposta.content().size());
        assertNull(resposta.vigente());
        verify(termos, never()).findAll(any(PageRequest.class));
    }

    @Test
    void paginaNegativaNaoAcessaBanco() {
        assertThrows(RegraNegocioException.class,
                () -> service.listarPaginado(-1, null));

        verifyNoInteractions(termos, aceites, auditoria);
    }

    @Test
    void visualizarInexistenteFalhaEExistenteRetornaConteudo() {
        assertThrows(TermoNaoEncontradoException.class,
                () -> service.visualizar(rascunho.getId()));

        when(termos.findById(rascunho.getId())).thenReturn(Optional.of(rascunho));

        assertEquals(
                "Uso anterior",
                service.visualizar(rascunho.getId()).termoUso());
    }

    @Test
    void atualizarRascunhoPreservaCriacaoERegistraAuditoria() {
        when(termos.findById(rascunho.getId())).thenReturn(Optional.of(rascunho));
        Instant criacao = rascunho.getCriadoEm();

        var resposta = service.atualizar(
                rascunho.getId(),
                new AtualizarTermoVersaoRequest(
                        "  Uso novo  ", "  Política nova  ", 2L),
                direcao);

        assertEquals("Uso novo", resposta.termoUso());
        assertEquals("Política nova", resposta.politicaPrivacidade());
        assertEquals(criacao, resposta.criadoEm());
        assertEquals(agora, resposta.atualizadoEm());

        verify(termos).saveAndFlush(rascunho);
        verify(auditoria).aposCommit(
                "TERMO_RASCUNHO_EDITADO", direcao.id(), rascunho.getId());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {1, 3})
    void versaoIncorretaImpedeEdicaoEPublicacao(Long versao) {
        when(termos.findById(rascunho.getId())).thenReturn(Optional.of(rascunho));

        assertThrows(ConflitoVersaoException.class,
                () -> service.atualizar(
                        rascunho.getId(),
                        new AtualizarTermoVersaoRequest("Uso", "Política", versao),
                        direcao));
        assertThrows(ConflitoVersaoException.class,
                () -> service.publicar(rascunho.getId(), versao, direcao));

        assertEquals("Uso anterior", rascunho.getTermoUso());
        assertEquals(StatusTermo.RASCUNHO, rascunho.getStatus());

        verify(termos, never()).saveAndFlush(any());
        verifyNoInteractions(auditoria);
    }

    @ParameterizedTest
    @EnumSource(value = StatusTermo.class, names = {"VIGENTE", "ARQUIVADO"})
    void termoForaDeRascunhoNaoPodeSerAlteradoExcluidoOuPublicado(
            StatusTermo status) {

        rascunho.setStatus(status);
        when(termos.findById(rascunho.getId())).thenReturn(Optional.of(rascunho));

        assertThrows(RegraNegocioException.class,
                () -> service.atualizar(
                        rascunho.getId(),
                        new AtualizarTermoVersaoRequest("Uso", "Política", 2L),
                        direcao));
        assertThrows(RegraNegocioException.class,
                () -> service.excluir(rascunho.getId(), direcao));
        assertThrows(RegraNegocioException.class,
                () -> service.publicar(rascunho.getId(), 2L, direcao));

        verify(termos, never()).saveAndFlush(any());
        verify(termos, never()).delete(any());
        verifyNoInteractions(auditoria);
    }

    @Test
    void excluirRascunhoRemoveERegistraAuditoria() {
        when(termos.findById(rascunho.getId())).thenReturn(Optional.of(rascunho));

        service.excluir(rascunho.getId(), direcao);

        verify(termos).delete(rascunho);
        verify(termos).flush();
        verify(auditoria).aposCommit(
                "TERMO_RASCUNHO_EXCLUIDO", direcao.id(), rascunho.getId());
    }

    @Test
    void publicarArquivaVigenteAnteriorEPreservaHistorico() {
        var anterior = termo(StatusTermo.VIGENTE, "v1");
        Instant criacao = anterior.getCriadoEm();

        when(termos.findById(rascunho.getId())).thenReturn(Optional.of(rascunho));
        when(termos.buscarPorStatusComBloqueio(StatusTermo.VIGENTE))
                .thenReturn(Optional.of(anterior));

        var resposta = service.publicar(rascunho.getId(), 2L, direcao);

        assertEquals(StatusTermo.ARQUIVADO, anterior.getStatus());
        assertEquals(criacao, anterior.getCriadoEm());
        assertEquals(agora, anterior.getAtualizadoEm());
        assertEquals(StatusTermo.VIGENTE, resposta.status());
        assertEquals(agora, resposta.publicadoEm());

        var ordem = inOrder(termos);
        ordem.verify(termos).saveAndFlush(anterior);
        ordem.verify(termos).saveAndFlush(rascunho);

        verify(auditoria).aposCommit(
                "TERMO_PUBLICADO", direcao.id(), rascunho.getId());
    }

    @Test
    void publicarSemTermoAnteriorTambemFunciona() {
        when(termos.findById(rascunho.getId())).thenReturn(Optional.of(rascunho));

        assertEquals(
                StatusTermo.VIGENTE,
                service.publicar(rascunho.getId(), 2L, direcao).status());

        verify(termos).saveAndFlush(rascunho);
    }

    private TermoVersao termo(StatusTermo status, String identificador) {
        var termo = new TermoVersao();
        termo.setId(UUID.randomUUID());
        termo.setIdentificador(identificador);
        termo.setStatus(status);
        termo.setTermoUso("Uso anterior");
        termo.setPoliticaPrivacidade("Política anterior");
        termo.setCriadoEm(agora.minusSeconds(3600));
        termo.setAtualizadoEm(termo.getCriadoEm());
        termo.setVersao(2L);
        return termo;
    }
}