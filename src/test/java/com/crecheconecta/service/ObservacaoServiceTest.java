package com.crecheconecta.service;

import com.crecheconecta.entity.*;
import com.crecheconecta.exception.*;
import com.crecheconecta.model.DadosObservacao;
import com.crecheconecta.repository.*;
import com.crecheconecta.security.UsuarioAtual;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.crecheconecta.service.DadosAutenticacaoTest.usuario;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ObservacaoServiceTest {
    private final ObservacaoRepository observacoes = mock(ObservacaoRepository.class);
    private final VinculoResponsavelService vinculos = mock(VinculoResponsavelService.class);
    private final TurmaProfessorRepository professores = mock(TurmaProfessorRepository.class);
    private final AlunoRepository alunos = mock(AlunoRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final CriptografiaService cripto = mock(CriptografiaService.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final Instant agora = Instant.parse("2026-11-20T12:00:00Z");
    private final ObservacaoService service = new ObservacaoService(
            observacoes, vinculos, professores, alunos, usuarios, cripto, auditoria,
            Clock.fixed(agora, ZoneOffset.UTC));

    private final Usuario autor = usuario(Perfil.PROFESSOR);
    private final UsuarioAtual professor = new UsuarioAtual(autor.getId(), Perfil.PROFESSOR);
    private final UUID alunoId = UUID.randomUUID();
    private final UUID turmaId = UUID.randomUUID();
    private final DadosObservacao dados =
            new DadosObservacao("Participação", "Participou da atividade.");

    @Test
    void cadastrarCifraConteudoERegistraAutorEData() {
        permitirProfessor();
        when(cripto.cifrar(dados.conteudo())).thenReturn("cifrado");

        UUID id = service.cadastrar(alunoId, dados, professor);

        var captor = ArgumentCaptor.forClass(Observacao.class);
        verify(observacoes).saveAndFlush(captor.capture());
        Observacao salva = captor.getValue();

        assertEquals(id, salva.getId());
        assertEquals(alunoId, salva.getAlunoId());
        assertEquals(autor.getId(), salva.getAutorId());
        assertEquals(dados.titulo(), salva.getTitulo());
        assertEquals("cifrado", salva.getConteudo());
        assertEquals(agora, salva.getDataCriacao());
        assertEquals(agora, salva.getDataAtualizacao());

        verify(auditoria).aposCommit("OBSERVACAO_CADASTRADA", autor.getId(), id);
    }

    @ParameterizedTest
    @EnumSource(value = Perfil.class, names = {"DIRECAO", "RESPONSAVEL"})
    void outrosPerfisNaoPodemCadastrar(Perfil perfil) {
        assertThrows(AccessDeniedException.class, () -> service.cadastrar(
                alunoId, dados, new UsuarioAtual(UUID.randomUUID(), perfil)));

        verifyNoInteractions(observacoes, cripto, auditoria);
    }

    @Test
    void usuarioInativoNaoPodeEscrever() {
        autor.setAtivo(false);
        when(usuarios.findById(autor.getId())).thenReturn(Optional.of(autor));

        assertThrows(AccessDeniedException.class,
                () -> service.cadastrar(alunoId, dados, professor));

        verifyNoInteractions(observacoes, auditoria);
    }

    @Test
    void professorSemVinculoNaoDescobreAluno() {
        permitirProfessor();
        when(professores.existsByTurmaIdAndProfessorIdAndEncerradoEmIsNull(
                turmaId, autor.getId())).thenReturn(false);

        assertThrows(AlunoNaoEncontradoException.class,
                () -> service.cadastrar(alunoId, dados, professor));

        verifyNoInteractions(observacoes, auditoria);
    }

    @Test
    void listarComoResponsavelValidaVinculoEDecifraSemPermitirEdicao() {
        var pai = new UsuarioAtual(UUID.randomUUID(), Perfil.RESPONSAVEL);
        var observacao = existente();

        when(observacoes.findByAlunoIdOrderByDataCriacaoDesc(alunoId))
                .thenReturn(List.of(observacao));
        when(usuarios.findAllById(any())).thenReturn(List.of(autor));
        when(cripto.decifrar("cifrado")).thenReturn("Texto original");

        var resposta = service.listar(alunoId, pai);

        verify(vinculos).exigirResponsavelDoAluno(pai, alunoId);
        assertEquals(1, resposta.size());
        assertEquals("Texto original", resposta.getFirst().conteudo());
        assertEquals(autor.getNome(), resposta.getFirst().autorNome());
        assertFalse(resposta.getFirst().editavel());

        verify(auditoria).registrar("OBSERVACOES_LISTADAS", pai.id(), alunoId);
    }

    @Test
    void responsavelSemVinculoNaoConsultaObservacoes() {
        var pai = new UsuarioAtual(UUID.randomUUID(), Perfil.RESPONSAVEL);

        doThrow(new AlunoNaoEncontradoException())
                .when(vinculos).exigirResponsavelDoAluno(pai, alunoId);

        assertThrows(AlunoNaoEncontradoException.class,
                () -> service.listar(alunoId, pai));

        verifyNoInteractions(observacoes, cripto, auditoria);
    }

    @Test
    void direcaoPodeLerObservacaoSemEditar() {
        var usuarioDirecao = usuario(Perfil.DIRECAO);
        var direcao = new UsuarioAtual(usuarioDirecao.getId(), Perfil.DIRECAO);
        var observacao = existente();

        when(usuarios.findById(direcao.id())).thenReturn(Optional.of(usuarioDirecao));
        when(alunos.existsById(alunoId)).thenReturn(true);
        when(observacoes.findById(observacao.getId())).thenReturn(Optional.of(observacao));
        when(usuarios.findById(autor.getId())).thenReturn(Optional.of(autor));
        when(cripto.decifrar("cifrado")).thenReturn("Texto original");

        var resposta = service.visualizar(alunoId, observacao.getId(), direcao);

        assertEquals("Texto original", resposta.conteudo());
        assertFalse(resposta.editavel());

        verify(auditoria).registrar(
                "OBSERVACAO_CONSULTADA", direcao.id(), observacao.getId());
    }

    @Test
    void leituraSemUsuarioEAlunoInexistenteSaoRecusados() {
        assertThrows(AccessDeniedException.class,
                () -> service.listar(alunoId, null));

        var diretor = usuario(Perfil.DIRECAO);
        when(usuarios.findById(diretor.getId())).thenReturn(Optional.of(diretor));

        assertThrows(AlunoNaoEncontradoException.class, () -> service.listar(
                alunoId, new UsuarioAtual(diretor.getId(), Perfil.DIRECAO)));

        verifyNoInteractions(observacoes, cripto, auditoria);
    }

    @Test
    void atualizarComoAutorPreservaCriacaoECifraNovoConteudo() {
        permitirProfessor();
        var observacao = existente();
        Instant criacao = observacao.getDataCriacao();

        when(observacoes.findById(observacao.getId())).thenReturn(Optional.of(observacao));
        when(cripto.cifrar(dados.conteudo())).thenReturn("novo-cifrado");
        when(cripto.decifrar("novo-cifrado")).thenReturn(dados.conteudo());

        var resposta = service.atualizar(
                alunoId, observacao.getId(), dados, 2L, professor);

        assertEquals(dados.titulo(), resposta.titulo());
        assertEquals(dados.conteudo(), resposta.conteudo());
        assertTrue(resposta.editavel());
        assertEquals(criacao, observacao.getDataCriacao());
        assertEquals(agora, observacao.getDataAtualizacao());

        verify(observacoes).saveAndFlush(observacao);
        verify(auditoria).aposCommit(
                "OBSERVACAO_EDITADA", autor.getId(), observacao.getId());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {1, 3})
    void atualizarComVersaoAusenteOuDiferenteNaoPersiste(Long versao) {
        permitirProfessor();
        var observacao = existente();

        when(observacoes.findById(observacao.getId())).thenReturn(Optional.of(observacao));

        assertThrows(ConflitoVersaoException.class,
                () -> service.atualizar(
                        alunoId, observacao.getId(), dados, versao, professor));

        assertEquals("Título anterior", observacao.getTitulo());
        verify(observacoes, never()).saveAndFlush(any());
        verifyNoInteractions(cripto, auditoria);
    }

    @Test
    void outroProfessorNaoPodeEditarNemExcluir() {
        permitirProfessor();
        var observacao = existente();
        observacao.setAutorId(UUID.randomUUID());

        when(observacoes.findById(observacao.getId())).thenReturn(Optional.of(observacao));

        assertThrows(AccessDeniedException.class,
                () -> service.atualizar(
                        alunoId, observacao.getId(), dados, 2L, professor));
        assertThrows(AccessDeniedException.class,
                () -> service.excluir(alunoId, observacao.getId(), professor));

        verify(observacoes, never()).saveAndFlush(any());
        verify(observacoes, never()).delete(any());
        verifyNoInteractions(auditoria);
    }

    @Test
    void observacaoDeOutroAlunoNaoPodeSerLida() {
        permitirProfessor();
        var observacao = existente();
        observacao.setAlunoId(UUID.randomUUID());

        when(observacoes.findById(observacao.getId())).thenReturn(Optional.of(observacao));

        assertThrows(ObservacaoNaoEncontradaException.class,
                () -> service.visualizar(alunoId, observacao.getId(), professor));

        verifyNoInteractions(cripto, auditoria);
    }

    @Test
    void autorPodeExcluirComAuditoria() {
        permitirProfessor();
        var observacao = existente();

        when(observacoes.findById(observacao.getId())).thenReturn(Optional.of(observacao));

        service.excluir(alunoId, observacao.getId(), professor);

        verify(observacoes).delete(observacao);
        verify(observacoes).flush();
        verify(auditoria).aposCommit(
                "OBSERVACAO_EXCLUIDA", autor.getId(), observacao.getId());
    }

    private void permitirProfessor() {
        when(usuarios.findById(autor.getId())).thenReturn(Optional.of(autor));

        var aluno = new Aluno();
        aluno.setAtivo(true);

        when(alunos.findById(alunoId)).thenReturn(Optional.of(aluno));
        when(vinculos.turmaAtualDoAluno(alunoId)).thenReturn(Optional.of(turmaId));
        when(professores.existsByTurmaIdAndProfessorIdAndEncerradoEmIsNull(
                turmaId, autor.getId())).thenReturn(true);
    }

    private Observacao existente() {
        var observacao = new Observacao();
        observacao.setAlunoId(alunoId);
        observacao.setAutorId(autor.getId());
        observacao.setTitulo("Título anterior");
        observacao.setConteudo("cifrado");
        observacao.setVersao(2L);
        observacao.setDataCriacao(agora.minusSeconds(3600));
        observacao.setDataAtualizacao(observacao.getDataCriacao());
        return observacao;
    }
}