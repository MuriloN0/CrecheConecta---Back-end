package com.crecheconecta.service;

import com.crecheconecta.entity.Aluno;
import com.crecheconecta.entity.FichaSaude;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.Usuario;
import com.crecheconecta.exception.AlunoNaoEncontradoException;
import com.crecheconecta.exception.ConflitoVersaoException;
import com.crecheconecta.exception.FichaSaudeNaoEncontradaException;
import com.crecheconecta.model.DadosFichaSaude;
import com.crecheconecta.repository.AlunoRepository;
import com.crecheconecta.repository.FichaSaudeRepository;
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
 * Testes unitários do FichaSaudeService (regra do portal dos pais):
 * escrita só do RESPONSAVEL do aluno; leitura de RESPONSAVEL, DIRECAO e PROFESSOR da turma.
 * O "porteiro" VinculoResponsavelService é mockado.
 */
class FichaSaudeServiceTest {

    private final FichaSaudeRepository repository = mock(FichaSaudeRepository.class);
    private final VinculoResponsavelService acessoResponsavel = mock(VinculoResponsavelService.class);
    private final AlunoRepository alunos = mock(AlunoRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final TurmaProfessorRepository turmasProfessores = mock(TurmaProfessorRepository.class);
    private final CriptografiaService cripto = mock(CriptografiaService.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);

    private final Instant agora = Instant.parse("2026-11-02T12:00:00Z");

    private final FichaSaudeService service = new FichaSaudeService(
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
    private final FichaSaude ficha = new FichaSaude();

    @BeforeEach
    void preparar() {
        ficha.setAlunoId(alunoId);
        ficha.setNome("Ficha");
        ficha.setObservacoes("conteudo-cifrado");
        ficha.setVersao(0L);
        ficha.setDataCriacao(agora.minusSeconds(3600));
        ficha.setDataAtualizacao(ficha.getDataCriacao());
    }

    // ---------- escrita (RESPONSAVEL) ----------

    @Test
    void deveCifrarObservacoesAntesDePersistir() {
        when(acessoResponsavel.exigirResponsavelDoAluno(pai, alunoId)).thenReturn(new Aluno());
        when(cripto.cifrar("Observação")).thenReturn("cifrado");

        var id = service.cadastrar(alunoId, new DadosFichaSaude("Ficha", "Observação"), pai);

        var captor = ArgumentCaptor.forClass(FichaSaude.class);
        verify(repository).saveAndFlush(captor.capture());
        assertEquals("cifrado", captor.getValue().getObservacoes());
        assertEquals(alunoId, captor.getValue().getAlunoId());
        assertEquals(agora, captor.getValue().getDataCriacao());
        verify(auditoria).aposCommit("FICHA_CADASTRADA", responsavel.getId(), id);
    }

    @Test
    void deveNegarCadastroDeQuemNaoEhResponsavelDoAluno() {
        when(acessoResponsavel.exigirResponsavelDoAluno(any(), any())).thenThrow(new AlunoNaoEncontradoException());

        assertThrows(AlunoNaoEncontradoException.class,
                () -> service.cadastrar(alunoId, new DadosFichaSaude("Ficha", "Obs"), pai));

        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(cripto);
    }

    @Test
    void deveAtualizarCifrandoNovasObservacoes() {
        when(acessoResponsavel.exigirResponsavelDoAluno(pai, alunoId)).thenReturn(new Aluno());
        when(repository.findById(ficha.getId())).thenReturn(Optional.of(ficha));
        when(cripto.cifrar("Nova observação")).thenReturn("novo-cifrado");
        when(cripto.decifrar("novo-cifrado")).thenReturn("Nova observação");

        var criadoEm = ficha.getDataCriacao();

        var resposta = service.atualizar(alunoId, ficha.getId(), new DadosFichaSaude("Novo nome", "Nova observação"), 0L, pai);

        assertEquals("Nova observação", resposta.observacoes());
        assertEquals("novo-cifrado", ficha.getObservacoes());
        assertEquals(criadoEm, ficha.getDataCriacao());
        assertEquals(agora, ficha.getDataAtualizacao());
        verify(repository).saveAndFlush(ficha);
    }

    @Test
    void deveRejeitarVersaoAntigaSemAlterarFicha() {
        when(acessoResponsavel.exigirResponsavelDoAluno(pai, alunoId)).thenReturn(new Aluno());
        when(repository.findById(ficha.getId())).thenReturn(Optional.of(ficha));

        assertThrows(ConflitoVersaoException.class,
                () -> service.atualizar(alunoId, ficha.getId(), new DadosFichaSaude("Novo", "Observação"), 1L, pai));

        assertEquals("Ficha", ficha.getNome());
        verifyNoInteractions(cripto, auditoria);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void deveImpedirAcessoAFichaDeOutroAluno() {
        when(acessoResponsavel.exigirResponsavelDoAluno(pai, alunoId)).thenReturn(new Aluno());
        ficha.setAlunoId(UUID.randomUUID());
        when(repository.findById(ficha.getId())).thenReturn(Optional.of(ficha));

        assertThrows(FichaSaudeNaoEncontradaException.class,
                () -> service.visualizar(alunoId, ficha.getId(), pai));

        verifyNoInteractions(cripto);
    }

    @Test
    void deveExcluirSomenteFichaLocalizadaDoAluno() {
        when(acessoResponsavel.exigirResponsavelDoAluno(pai, alunoId)).thenReturn(new Aluno());
        when(repository.findById(ficha.getId())).thenReturn(Optional.of(ficha));

        service.excluir(alunoId, ficha.getId(), pai);

        verify(repository).delete(ficha);
        verify(auditoria).aposCommit("FICHA_EXCLUIDA", responsavel.getId(), ficha.getId());
    }

    // ---------- leitura (RESPONSAVEL, DIRECAO, PROFESSOR) ----------

    @Test
    void responsavelDeveDecifrarSomenteNaVisualizacao() {
        when(acessoResponsavel.exigirResponsavelDoAluno(pai, alunoId)).thenReturn(new Aluno());
        when(repository.findById(ficha.getId())).thenReturn(Optional.of(ficha));
        when(cripto.decifrar("conteudo-cifrado")).thenReturn("Observação");

        var resposta = service.visualizar(alunoId, ficha.getId(), pai);

        assertEquals("Observação", resposta.observacoes());
        verify(auditoria).registrar("FICHA_CONSULTADA", responsavel.getId(), ficha.getId());
    }

    @Test
    void direcaoAtivaDeveListarFichasDeQualquerAluno() {
        when(usuarios.findById(diretorUsuario.getId())).thenReturn(Optional.of(diretorUsuario));
        when(alunos.existsById(alunoId)).thenReturn(true);
        when(repository.findByAlunoId(alunoId)).thenReturn(List.of(ficha));

        assertEquals(1, service.listar(alunoId, direcao).size());

        verifyNoInteractions(cripto);
        verify(auditoria).registrar("FICHAS_LISTADAS", diretorUsuario.getId(), alunoId);
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
        when(repository.findByAlunoId(alunoId)).thenReturn(List.of(ficha));

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
