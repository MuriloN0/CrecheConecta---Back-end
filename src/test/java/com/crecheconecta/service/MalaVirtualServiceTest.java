package com.crecheconecta.service;

import com.crecheconecta.dto.AtualizarMalaVirtualRequest;
import com.crecheconecta.dto.CriarMalaVirtualRequest;
import com.crecheconecta.dto.ItemMalaRequest;
import com.crecheconecta.entity.*;
import com.crecheconecta.exception.ConflitoVersaoException;
import com.crecheconecta.exception.MalaVirtualNaoEncontradaException;
import com.crecheconecta.exception.RegraNegocioException;
import com.crecheconecta.repository.*;
import com.crecheconecta.security.UsuarioAtual;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.*;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MalaVirtualServiceTest {

    @Mock private UsuarioRepository usuarios;
    @Mock private TurmaRepository turmas;
    @Mock private TurmaProfessorRepository professores;
    @Mock private MatriculaTurmaRepository matriculas;
    @Mock private MalaVirtualRepository malas;
    @Mock private ItemMalaRepository itens;
    @Mock private MarcacaoItemMalaRepository marcacoes;
    @Mock private AuditoriaService auditoria;

    private MalaVirtualService service;
    private Usuario professor;
    private UsuarioAtual solicitante;
    private Turma turma;
    private LocalDate hoje;
    private Instant agora;

    @BeforeEach
    void preparar() {
        agora = Instant.parse("2026-11-04T15:00:00Z");
        hoje = LocalDate.of(2026, 11, 4);

        service = criarService(Clock.fixed(agora, ZoneOffset.UTC));

        professor = new Usuario();
        professor.setId(UUID.randomUUID());
        professor.setPerfil(Perfil.PROFESSOR);
        professor.setAtivo(true);
        professor.setSituacaoCadastro(SituacaoCadastro.APROVADO);

        solicitante = new UsuarioAtual(professor.getId(), Perfil.PROFESSOR);

        turma = new Turma();
        turma.setId(UUID.randomUUID());
        turma.setAtivo(true);
    }

    @Test
    void deveCriarMalaComItens() {
        autorizar();
        when(turmas.findById(turma.getId())).thenReturn(Optional.of(turma));
        when(malas.saveAndFlush(any())).thenAnswer(invocacao -> {
            MalaVirtual mala = invocacao.getArgument(0);
            mala.setId(UUID.randomUUID());
            return mala;
        });

        var resposta = service.criar(turma.getId(), criarRequest(hoje.plusDays(1), item(null, "  Tinta guache ", 2), item(null, "Avental", 1)), solicitante);

        assertEquals("Aula de pintura", resposta.titulo());
        assertEquals(professor.getId(), resposta.professorId());
        assertEquals(List.of("Avental", "Tinta guache"), resposta.itens().stream().map(i -> i.nome()).toList());

        verify(itens).saveAll(argThat(lista -> ((Collection<?>) lista).size() == 2));
        verify(auditoria).aposCommit(eq("MALA_VIRTUAL_CRIADA"), eq(professor.getId()), any());
    }

    @Test
    void deveRejeitarDataNoPassado() {
        autorizar();
        when(turmas.findById(turma.getId())).thenReturn(Optional.of(turma));

        assertThrows(RegraNegocioException.class, () -> service.criar(turma.getId(), criarRequest(hoje.minusDays(1), item(null, "Avental", 1)), solicitante));

        verify(malas, never()).saveAndFlush(any());
    }

    @Test
    void devePermitirMalaParaHojeNoFusoDeSaoPaulo() {
        service = criarService(Clock.fixed(Instant.parse("2026-11-05T01:00:00Z"), ZoneOffset.UTC));
        autorizar();
        when(turmas.findById(turma.getId())).thenReturn(Optional.of(turma));

        assertDoesNotThrow(() -> service.criar(turma.getId(), criarRequest(hoje, item(null, "Avental", 1)), solicitante));
    }

    @Test
    void deveRejeitarItensRepetidos() {
        autorizar();
        when(turmas.findById(turma.getId())).thenReturn(Optional.of(turma));

        assertThrows(RegraNegocioException.class, () -> service.criar(turma.getId(), criarRequest(hoje, item(null, "Avental", 1), item(null, " avental ", 2)), solicitante));

        verify(malas, never()).saveAndFlush(any());
    }

    @Test
    void deveRejeitarTurmaInativa() {
        autorizar();
        turma.setAtivo(false);
        when(turmas.findById(turma.getId())).thenReturn(Optional.of(turma));

        assertThrows(RegraNegocioException.class, () -> service.criar(turma.getId(), criarRequest(hoje, item(null, "Avental", 1)), solicitante));
    }

    @Test
    void deveNegarProfessorSemVinculoComATurma() {
        when(usuarios.findById(professor.getId())).thenReturn(Optional.of(professor));

        assertThrows(AccessDeniedException.class, () -> service.listar(turma.getId(), solicitante));

        verifyNoInteractions(malas);
    }

    @Test
    void deveNegarProfessorNaoAprovado() {
        professor.setSituacaoCadastro(SituacaoCadastro.PENDENTE_APROVACAO);
        when(usuarios.findById(professor.getId())).thenReturn(Optional.of(professor));

        assertThrows(AccessDeniedException.class, () -> service.listar(turma.getId(), solicitante));

        verifyNoInteractions(malas);
    }

    @ParameterizedTest
    @EnumSource(value = Perfil.class, names = {"DIRECAO", "RESPONSAVEL"})
    void deveNegarOutrosPerfis(Perfil perfil) {
        solicitante = new UsuarioAtual(UUID.randomUUID(), perfil);

        assertThrows(AccessDeniedException.class, () -> service.listar(turma.getId(), solicitante));

        verifyNoInteractions(usuarios, malas);
    }

    @Test
    void deveAtualizarSubstituindoItensMesmoComMarcacoes() {
        autorizar();
        var mala = mala(professor.getId());
        var mantido = itemExistente(mala.getId(), "Avental", 1);
        var removido = itemExistente(mala.getId(), "Pincel", 3);

        when(malas.buscarDaTurmaComBloqueio(mala.getId(), turma.getId())).thenReturn(Optional.of(mala));
        when(itens.findByMalaId(mala.getId())).thenReturn(List.of(mantido, removido));

        var request = new AtualizarMalaVirtualRequest("Pintura com guache", mala.getData(), null, List.of(item(mantido.getId(), "Avental grande", 1), item(null, "Tinta guache", 2)), 0L);

        var resposta = service.atualizar(turma.getId(), mala.getId(), request, solicitante);

        assertEquals("Pintura com guache", resposta.titulo());
        assertEquals("Avental grande", mantido.getNome());
        assertEquals(List.of("Avental grande", "Tinta guache"), resposta.itens().stream().map(i -> i.nome()).toList());

        verify(itens).deleteAll(List.of(removido));
        verify(auditoria).aposCommit("MALA_VIRTUAL_ATUALIZADA", professor.getId(), mala.getId());
    }

    @Test
    void deveRejeitarItemDeOutraMala() {
        autorizar();
        var mala = mala(professor.getId());

        when(malas.buscarDaTurmaComBloqueio(mala.getId(), turma.getId())).thenReturn(Optional.of(mala));
        when(itens.findByMalaId(mala.getId())).thenReturn(List.of());

        var request = new AtualizarMalaVirtualRequest("Aula", mala.getData(), null, List.of(item(UUID.randomUUID(), "Avental", 1)), 0L);

        assertThrows(RegraNegocioException.class, () -> service.atualizar(turma.getId(), mala.getId(), request, solicitante));

        verify(itens, never()).deleteAll(any());
    }

    @Test
    void deveRejeitarVersaoAntiga() {
        autorizar();
        var mala = mala(professor.getId());
        when(malas.buscarDaTurmaComBloqueio(mala.getId(), turma.getId())).thenReturn(Optional.of(mala));

        var request = new AtualizarMalaVirtualRequest("Aula", mala.getData(), null, List.of(item(null, "Avental", 1)), 3L);

        assertThrows(ConflitoVersaoException.class, () -> service.atualizar(turma.getId(), mala.getId(), request, solicitante));
    }

    @Test
    void devePermitirEditarMalaPassadaSemMudarAData() {
        autorizar();
        var mala = mala(professor.getId());
        mala.setData(hoje.minusDays(2));

        when(malas.buscarDaTurmaComBloqueio(mala.getId(), turma.getId())).thenReturn(Optional.of(mala));
        when(itens.findByMalaId(mala.getId())).thenReturn(List.of());

        var request = new AtualizarMalaVirtualRequest("Aula", mala.getData(), "Obrigado!", List.of(item(null, "Avental", 1)), 0L);

        assertDoesNotThrow(() -> service.atualizar(turma.getId(), mala.getId(), request, solicitante));
    }

    @Test
    void deveNegarEdicaoDeOutroProfessor() {
        autorizar();
        var mala = mala(UUID.randomUUID());
        when(malas.buscarDaTurmaComBloqueio(mala.getId(), turma.getId())).thenReturn(Optional.of(mala));

        assertThrows(AccessDeniedException.class, () -> service.excluir(turma.getId(), mala.getId(), solicitante));

        verify(malas, never()).delete(any());
    }

    @Test
    void deveExcluirMalaDoAutor() {
        autorizar();
        var mala = mala(professor.getId());
        when(malas.buscarDaTurmaComBloqueio(mala.getId(), turma.getId())).thenReturn(Optional.of(mala));

        service.excluir(turma.getId(), mala.getId(), solicitante);

        verify(malas).delete(mala);
        verify(auditoria).aposCommit("MALA_VIRTUAL_EXCLUIDA", professor.getId(), mala.getId());
    }

    @Test
    void deveInformarMalaInexistente() {
        autorizar();
        var malaId = UUID.randomUUID();
        when(malas.findByIdAndTurmaId(malaId, turma.getId())).thenReturn(Optional.empty());

        assertThrows(MalaVirtualNaoEncontradaException.class, () -> service.visualizar(turma.getId(), malaId, solicitante));
    }

    @Test
    void deveMostrarProgressoPorAlunoIncluindoQuemNaoMarcou() {
        autorizar();
        var mala = mala(professor.getId());
        var avental = itemExistente(mala.getId(), "Avental", 1);
        var tinta = itemExistente(mala.getId(), "Tinta", 2);

        var ana = aluno("Ana");
        var bruno = aluno("Bruno");

        var marcacao = new MarcacaoItemMala();
        marcacao.setItemId(avental.getId());
        marcacao.setAlunoId(ana.getId());
        marcacao.setEnviado(true);
        marcacao.setObservacao("Vai na mochila");

        when(malas.existsByIdAndTurmaId(mala.getId(), turma.getId())).thenReturn(true);
        when(itens.findByMalaId(mala.getId())).thenReturn(List.of(avental, tinta));
        when(matriculas.buscarAlunosAtuaisDaTurma(turma.getId())).thenReturn(List.of(ana, bruno));
        when(marcacoes.findByItemIdIn(anyCollection())).thenReturn(List.of(marcacao));

        var progresso = service.progresso(turma.getId(), mala.getId(), solicitante);

        assertEquals(2, progresso.size());
        assertEquals(1, progresso.get(0).itensEnviados());
        assertEquals("Vai na mochila", progresso.get(0).itens().getFirst().observacao());
        assertFalse(progresso.get(0).completo());
        assertEquals(0, progresso.get(1).itensEnviados());
        assertEquals(2, progresso.get(1).totalItens());
    }

    private MalaVirtualService criarService(Clock clock) {
        return new MalaVirtualService(new VinculoProfessorService(usuarios, professores), turmas, matriculas, malas, itens, new MalaVirtualSuporte(itens, marcacoes, clock), auditoria, clock);
    }

    private void autorizar() {
        when(usuarios.findById(professor.getId())).thenReturn(Optional.of(professor));
        when(professores.existsByTurmaIdAndProfessorIdAndEncerradoEmIsNull(turma.getId(), professor.getId())).thenReturn(true);
    }

    private CriarMalaVirtualRequest criarRequest(LocalDate data, ItemMalaRequest... itensPedidos) {
        return new CriarMalaVirtualRequest("Aula de pintura", data, null, List.of(itensPedidos));
    }

    private ItemMalaRequest item(UUID id, String nome, int quantidade) {
        return new ItemMalaRequest(id, nome, quantidade);
    }

    private MalaVirtual mala(UUID autorId) {
        var mala = new MalaVirtual();
        mala.setId(UUID.randomUUID());
        mala.setTurmaId(turma.getId());
        mala.setProfessorId(autorId);
        mala.setTitulo("Aula");
        mala.setData(hoje.plusDays(1));
        mala.setCriadoEm(agora);
        mala.setAtualizadoEm(agora);
        mala.setVersao(0L);
        return mala;
    }

    private ItemMala itemExistente(UUID malaId, String nome, int quantidade) {
        var item = new ItemMala();
        item.setId(UUID.randomUUID());
        item.setMalaId(malaId);
        item.setNome(nome);
        item.setQuantidade(quantidade);
        return item;
    }

    private Aluno aluno(String nome) {
        var aluno = new Aluno();
        aluno.setId(UUID.randomUUID());
        aluno.setNome(nome);
        aluno.setAtivo(true);
        return aluno;
    }
}
