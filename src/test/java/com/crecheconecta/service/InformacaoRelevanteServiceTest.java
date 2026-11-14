package com.crecheconecta.service;

import com.crecheconecta.entity.Aluno;
import com.crecheconecta.entity.InformacaoRelevante;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.Usuario;
import com.crecheconecta.exception.AlunoNaoEncontradoException;
import com.crecheconecta.exception.ConflitoVersaoException;
import com.crecheconecta.exception.InformacaoRelevanteNaoEncontradaException;
import com.crecheconecta.model.DadosInformacaoRelevante;
import com.crecheconecta.repository.AlunoRepository;
import com.crecheconecta.repository.InformacaoRelevanteRepository;
import com.crecheconecta.repository.TurmaProfessorRepository;
import com.crecheconecta.repository.UsuarioRepository;
import com.crecheconecta.security.UsuarioAtual;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

/**
 * Testes unitários do InformacaoRelevanteService (regra do portal dos pais):
 * escrita só do RESPONSAVEL do aluno; leitura de RESPONSAVEL, DIRECAO e PROFESSOR da turma.
 * O "porteiro" VinculoResponsavelService é mockado.
 */
class InformacaoRelevanteServiceTest {

    private final InformacaoRelevanteRepository repository = mock(InformacaoRelevanteRepository.class);
    private final VinculoResponsavelService acessoResponsavel = mock(VinculoResponsavelService.class);
    private final AlunoRepository alunos = mock(AlunoRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final TurmaProfessorRepository turmasProfessores = mock(TurmaProfessorRepository.class);
    private final CriptografiaService cripto = mock(CriptografiaService.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);

    private final Instant agora = Instant.parse("2026-11-02T12:00:00Z");

    private final InformacaoRelevanteService service = new InformacaoRelevanteService(
            repository,
            acessoResponsavel,
            alunos,
            usuarios,
            turmasProfessores,
            cripto,
            auditoria,
            Clock.fixed(agora, ZoneOffset.UTC)
    );

    private final Usuario responsavel = usuario(Perfil.RESPONSAVEL);
    private final UsuarioAtual pai = new UsuarioAtual(responsavel.getId(), Perfil.RESPONSAVEL);

    private final Usuario diretorUsuario = usuario(Perfil.DIRECAO);
    private final UsuarioAtual direcao = new UsuarioAtual(diretorUsuario.getId(), Perfil.DIRECAO);

    private final Usuario professorUsuario = usuario(Perfil.PROFESSOR);
    private final UsuarioAtual professor = new UsuarioAtual(professorUsuario.getId(), Perfil.PROFESSOR);

    private final UUID alunoId = UUID.randomUUID();
    private final InformacaoRelevante informacao = new InformacaoRelevante();

    @BeforeEach
    void preparar() {
        informacao.setAlunoId(alunoId);
        informacao.setNome("Ficha");
        informacao.setObservacoes("conteudo-cifrado");
        informacao.setVersao(0L);
        informacao.setDataCriacao(agora.minusSeconds(3600));
        informacao.setDataAtualizacao(informacao.getDataCriacao());
    }

    // ---------- escrita (RESPONSAVEL) ----------

    @Test
    void deveCifrarObservacoesAntesDePersistir() {
        when(acessoResponsavel.exigirResponsavelDoAluno(pai, alunoId)).thenReturn(new Aluno());
        when(cripto.cifrar("Observação")).thenReturn("cifrado");

        var id = service.cadastrar(alunoId, new DadosInformacaoRelevante("Ficha", "Observação"), pai);

        var captor = ArgumentCaptor.forClass(InformacaoRelevante.class);
        verify(repository).saveAndFlush(captor.capture());
        assertEquals("cifrado", captor.getValue().getObservacoes());
        assertEquals(alunoId, captor.getValue().getAlunoId());
        assertEquals(agora, captor.getValue().getDataCriacao());
        verify(auditoria).aposCommit("INFORMACAO_RELEVANTE_CADASTRADA", responsavel.getId(), id);
    }

    @Test
    void deveNegarCadastroDeQuemNaoEhResponsavelDoAluno() {
        when(acessoResponsavel.exigirResponsavelDoAluno(any(), any())).thenThrow(new AlunoNaoEncontradoException());

        assertThrows(AlunoNaoEncontradoException.class,
                () -> service.cadastrar(alunoId, new DadosInformacaoRelevante("Ficha", "Obs"), pai));

        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(cripto);
    }

    @Test
    void deveAtualizarCifrandoNovasObservacoes() {
        when(acessoResponsavel.exigirResponsavelDoAluno(pai, alunoId)).thenReturn(new Aluno());
        when(repository.findById(informacao.getId())).thenReturn(Optional.of(informacao));
        when(cripto.cifrar("Nova observação")).thenReturn("novo-cifrado");
        when(cripto.decifrar("novo-cifrado")).thenReturn("Nova observação");

        var criadoEm = informacao.getDataCriacao();

        var resposta = service.atualizar(alunoId, informacao.getId(), new DadosInformacaoRelevante("Novo nome", "Nova observação"), 0L, pai);

        assertEquals("Nova observação", resposta.observacoes());
        assertEquals("novo-cifrado", informacao.getObservacoes());
        assertEquals(criadoEm, informacao.getDataCriacao());
        assertEquals(agora, informacao.getDataAtualizacao());
        verify(repository).saveAndFlush(informacao);
    }

    @Test
    void deveRejeitarVersaoAntigaSemAlterarInformacao() {
        when(acessoResponsavel.exigirResponsavelDoAluno(pai, alunoId)).thenReturn(new Aluno());
        when(repository.findById(informacao.getId())).thenReturn(Optional.of(informacao));

        assertThrows(ConflitoVersaoException.class,
                () -> service.atualizar(alunoId, informacao.getId(), new DadosInformacaoRelevante("Novo", "Observação"), 1L, pai));

        assertEquals("Ficha", informacao.getNome());
        verifyNoInteractions(cripto, auditoria);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void deveImpedirAcessoAInformacaoDeOutroAluno() {
        when(acessoResponsavel.exigirResponsavelDoAluno(pai, alunoId)).thenReturn(new Aluno());
        informacao.setAlunoId(UUID.randomUUID());
        when(repository.findById(informacao.getId())).thenReturn(Optional.of(informacao));

        assertThrows(InformacaoRelevanteNaoEncontradaException.class,
                () -> service.visualizar(alunoId, informacao.getId(), pai));

        verifyNoInteractions(cripto);
    }

    @Test
    void deveExcluirSomenteInformacaoLocalizadaDoAluno() {
        when(acessoResponsavel.exigirResponsavelDoAluno(pai, alunoId)).thenReturn(new Aluno());
        when(repository.findById(informacao.getId())).thenReturn(Optional.of(informacao));

        service.excluir(alunoId, informacao.getId(), pai);

        verify(repository).delete(informacao);
        verify(auditoria).aposCommit("INFORMACAO_RELEVANTE_EXCLUIDA", responsavel.getId(), informacao.getId());
    }

    // ---------- leitura (RESPONSAVEL, DIRECAO, PROFESSOR) ----------

    @Test
    void responsavelDeveDecifrarSomenteNaVisualizacao() {
        when(acessoResponsavel.exigirResponsavelDoAluno(pai, alunoId)).thenReturn(new Aluno());
        when(repository.findById(informacao.getId())).thenReturn(Optional.of(informacao));
        when(cripto.decifrar("conteudo-cifrado")).thenReturn("Observação");

        var resposta = service.visualizar(alunoId, informacao.getId(), pai);

        assertEquals("Observação", resposta.observacoes());
        verify(auditoria).registrar("INFORMACAO_RELEVANTE_CONSULTADA", responsavel.getId(), informacao.getId());
    }

    @Test
    void direcaoAtivaDeveListarInformacoesDeQualquerAluno() {
        when(usuarios.findById(diretorUsuario.getId())).thenReturn(Optional.of(diretorUsuario));
        when(alunos.existsById(alunoId)).thenReturn(true);
        when(repository.findByAlunoId(alunoId)).thenReturn(List.of(informacao));

        assertEquals(1, service.listar(alunoId, direcao).size());

        verifyNoInteractions(cripto);
        verify(auditoria).registrar("INFORMACOES_RELEVANTES_LISTADAS", diretorUsuario.getId(), alunoId);
    }

    @Test
    void direcaoParaAlunoInexistenteDeveDar404() {
        when(usuarios.findById(diretorUsuario.getId())).thenReturn(Optional.of(diretorUsuario));
        when(alunos.existsById(alunoId)).thenReturn(false);

        assertThrows(AlunoNaoEncontradoException.class, () -> service.listar(alunoId, direcao));

        verify(repository, never()).findByAlunoId(any());
    }

    @Test
    void direcaoInativaDeveSerNegada() {
        diretorUsuario.setAtivo(false);
        when(usuarios.findById(diretorUsuario.getId())).thenReturn(Optional.of(diretorUsuario));

        assertThrows(AccessDeniedException.class, () -> service.listar(alunoId, direcao));

        verifyNoInteractions(repository, cripto);
    }

    @Test
    void professorDaTurmaDoAlunoDevePoderLer() {
        var turmaId = UUID.randomUUID();
        when(usuarios.findById(professorUsuario.getId())).thenReturn(Optional.of(professorUsuario));
        when(acessoResponsavel.turmaAtualDoAluno(alunoId)).thenReturn(Optional.of(turmaId));
        when(turmasProfessores.existsByTurmaIdAndProfessorIdAndEncerradoEmIsNull(turmaId, professorUsuario.getId())).thenReturn(true);
        when(repository.findByAlunoId(alunoId)).thenReturn(List.of(informacao));

        assertEquals(1, service.listar(alunoId, professor).size());
    }

    @Test
    void professorDeOutraTurmaDeveDar404() {
        var turmaId = UUID.randomUUID();
        when(usuarios.findById(professorUsuario.getId())).thenReturn(Optional.of(professorUsuario));
        when(acessoResponsavel.turmaAtualDoAluno(alunoId)).thenReturn(Optional.of(turmaId));
        when(turmasProfessores.existsByTurmaIdAndProfessorIdAndEncerradoEmIsNull(turmaId, professorUsuario.getId())).thenReturn(false);

        assertThrows(AlunoNaoEncontradoException.class, () -> service.listar(alunoId, professor));

        verify(repository, never()).findByAlunoId(any());
    }
}
