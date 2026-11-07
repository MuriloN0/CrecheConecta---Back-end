package com.crecheconecta.service;

import com.crecheconecta.dto.MarcarItemMalaRequest;
import com.crecheconecta.entity.ItemMala;
import com.crecheconecta.entity.MalaVirtual;
import com.crecheconecta.entity.MarcacaoItemMala;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.exception.AlunoNaoEncontradoException;
import com.crecheconecta.exception.ItemMalaNaoEncontradoException;
import com.crecheconecta.exception.MalaVirtualNaoEncontradaException;
import com.crecheconecta.exception.RegraNegocioException;
import com.crecheconecta.repository.ItemMalaRepository;
import com.crecheconecta.repository.MalaVirtualRepository;
import com.crecheconecta.repository.MarcacaoItemMalaRepository;
import com.crecheconecta.security.UsuarioAtual;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MalaVirtualFilhoServiceTest {

    @Mock private VinculoResponsavelService acessoResponsavel;
    @Mock private MalaVirtualRepository malas;
    @Mock private ItemMalaRepository itens;
    @Mock private MarcacaoItemMalaRepository marcacoes;
    @Mock private AuditoriaService auditoria;

    private MalaVirtualFilhoService service;
    private UsuarioAtual responsavel;
    private UUID alunoId;
    private UUID turmaId;
    private LocalDate hoje;
    private MalaVirtual mala;
    private ItemMala avental;
    private ItemMala tinta;

    @BeforeEach
    void preparar() {
        Instant agora = Instant.parse("2026-11-04T15:00:00Z");
        hoje = LocalDate.of(2026, 11, 4);

        var clock = Clock.fixed(agora, ZoneOffset.UTC);
        service = new MalaVirtualFilhoService(acessoResponsavel, malas, itens, marcacoes, new MalaVirtualSuporte(itens, marcacoes, clock), auditoria, clock);

        responsavel = new UsuarioAtual(UUID.randomUUID(), Perfil.RESPONSAVEL);
        alunoId = UUID.randomUUID();
        turmaId = UUID.randomUUID();

        mala = new MalaVirtual();
        mala.setId(UUID.randomUUID());
        mala.setTurmaId(turmaId);
        mala.setTitulo("Aula de pintura");
        mala.setData(hoje);

        avental = item("Avental");
        tinta = item("Tinta guache");
    }

    @Test
    void deveListarMalasDaTurmaComMarcacoesDoFilho() {
        when(acessoResponsavel.turmaAtualDoAluno(alunoId)).thenReturn(Optional.of(turmaId));
        when(malas.findTop20ByTurmaIdOrderByDataDescCriadoEmDesc(turmaId)).thenReturn(List.of(mala));
        when(itens.findByMalaIdIn(List.of(mala.getId()))).thenReturn(List.of(tinta, avental));
        when(marcacoes.findByAlunoIdAndItemIdIn(eq(alunoId), anyCollection())).thenReturn(List.of(marcacao(avental, true, "Na mochila")));

        var resposta = service.listar(alunoId, responsavel);

        assertEquals(1, resposta.size());
        var malaResposta = resposta.getFirst();
        assertTrue(malaResposta.podeMarcar());
        assertEquals(1, malaResposta.itensPendentes());
        assertEquals("Avental", malaResposta.itens().getFirst().nome());
        assertTrue(malaResposta.itens().getFirst().enviado());
        assertEquals("Na mochila", malaResposta.itens().getFirst().observacao());
        assertFalse(malaResposta.itens().get(1).enviado());

        verify(acessoResponsavel).exigirResponsavelDoAluno(responsavel, alunoId);
    }

    @Test
    void deveRetornarVazioSeFilhoSemTurma() {
        when(acessoResponsavel.turmaAtualDoAluno(alunoId)).thenReturn(Optional.empty());

        assertTrue(service.listar(alunoId, responsavel).isEmpty());

        verifyNoInteractions(malas);
    }

    @Test
    void naoDeveListarSeNaoForResponsavelDoAluno() {
        when(acessoResponsavel.exigirResponsavelDoAluno(responsavel, alunoId)).thenThrow(new AlunoNaoEncontradoException());

        assertThrows(AlunoNaoEncontradoException.class, () -> service.listar(alunoId, responsavel));

        verifyNoInteractions(malas, itens, marcacoes);
    }

    @Test
    void deveMarcarItemComObservacao() {
        prepararMarcacao();
        when(marcacoes.findByItemIdAndAlunoId(avental.getId(), alunoId)).thenReturn(Optional.empty());

        service.marcarItem(alunoId, mala.getId(), avental.getId(), new MarcarItemMalaRequest(true, "  Não tenho avental, vai camiseta velha "), responsavel);

        verify(marcacoes).saveAndFlush(argThat(m -> m.isEnviado()
                && m.getAlunoId().equals(alunoId)
                && m.getItemId().equals(avental.getId())
                && m.getMarcadoPor().equals(responsavel.id())
                && m.getObservacao().equals("Não tenho avental, vai camiseta velha")));
        verify(auditoria).aposCommit(eq("ITEM_MALA_MARCADO"), eq(responsavel.id()), any());
    }

    @Test
    void deveDesmarcarItemJaMarcado() {
        prepararMarcacao();
        var existente = marcacao(avental, true, "obs");
        when(marcacoes.findByItemIdAndAlunoId(avental.getId(), alunoId)).thenReturn(Optional.of(existente));

        service.marcarItem(alunoId, mala.getId(), avental.getId(), new MarcarItemMalaRequest(false, " "), responsavel);

        assertFalse(existente.isEnviado());
        assertNull(existente.getObservacao());
        verify(marcacoes).saveAndFlush(existente);
    }

    @Test
    void naoDeveMarcarDepoisDaDataDaAula() {
        mala.setData(hoje.minusDays(1));
        when(acessoResponsavel.turmaAtualDoAluno(alunoId)).thenReturn(Optional.of(turmaId));
        when(malas.findByIdAndTurmaId(mala.getId(), turmaId)).thenReturn(Optional.of(mala));

        assertThrows(RegraNegocioException.class, () -> service.marcarItem(alunoId, mala.getId(), avental.getId(), new MarcarItemMalaRequest(true, null), responsavel));

        verify(marcacoes, never()).saveAndFlush(any());
    }

    @Test
    void naoDeveMarcarMalaDeOutraTurma() {
        when(acessoResponsavel.turmaAtualDoAluno(alunoId)).thenReturn(Optional.of(turmaId));
        when(malas.findByIdAndTurmaId(mala.getId(), turmaId)).thenReturn(Optional.empty());

        assertThrows(MalaVirtualNaoEncontradaException.class, () -> service.marcarItem(alunoId, mala.getId(), avental.getId(), new MarcarItemMalaRequest(true, null), responsavel));

        verify(marcacoes, never()).saveAndFlush(any());
    }

    @Test
    void naoDeveMarcarItemDeOutraMala() {
        when(acessoResponsavel.turmaAtualDoAluno(alunoId)).thenReturn(Optional.of(turmaId));
        when(malas.findByIdAndTurmaId(mala.getId(), turmaId)).thenReturn(Optional.of(mala));
        var outroItem = UUID.randomUUID();
        when(itens.findByMalaId(mala.getId())).thenReturn(List.of(avental, tinta));

        assertThrows(ItemMalaNaoEncontradoException.class, () -> service.marcarItem(alunoId, mala.getId(), outroItem, new MarcarItemMalaRequest(true, null), responsavel));

        verify(marcacoes, never()).saveAndFlush(any());
    }

    private void prepararMarcacao() {
        when(acessoResponsavel.turmaAtualDoAluno(alunoId)).thenReturn(Optional.of(turmaId));
        when(malas.findByIdAndTurmaId(mala.getId(), turmaId)).thenReturn(Optional.of(mala));
        when(itens.findByMalaId(mala.getId())).thenReturn(List.of(avental, tinta));
    }

    private ItemMala item(String nome) {
        var item = new ItemMala();
        item.setId(UUID.randomUUID());
        item.setMalaId(mala.getId());
        item.setNome(nome);
        item.setQuantidade(1);
        return item;
    }

    private MarcacaoItemMala marcacao(ItemMala item, boolean enviado, String observacao) {
        var marcacao = new MarcacaoItemMala();
        marcacao.setId(UUID.randomUUID());
        marcacao.setItemId(item.getId());
        marcacao.setAlunoId(alunoId);
        marcacao.setEnviado(enviado);
        marcacao.setObservacao(observacao);
        return marcacao;
    }
}
