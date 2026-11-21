package com.crecheconecta.service;

import com.crecheconecta.dto.RegistrarDiarioRequest;
import com.crecheconecta.entity.*;
import com.crecheconecta.exception.*;
import com.crecheconecta.repository.*;
import com.crecheconecta.security.UsuarioAtual;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DiarioRotinaServiceTest {
    private final VinculoProfessorService vinculo = mock(VinculoProfessorService.class);
    private final TurmaRepository turmas = mock(TurmaRepository.class);
    private final MatriculaTurmaRepository matriculas = mock(MatriculaTurmaRepository.class);
    private final AlunoRepository alunos = mock(AlunoRepository.class);
    private final DiarioRotinaRepository diarios = mock(DiarioRotinaRepository.class);
    private final MalaVirtualSuporte suporte = mock(MalaVirtualSuporte.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final DiarioRotinaService service = new DiarioRotinaService(
            vinculo, turmas, matriculas, alunos, diarios, suporte, auditoria);

    private final UUID turmaId = UUID.randomUUID();
    private final UUID alunoId = UUID.randomUUID();
    private final UsuarioAtual professor =
            new UsuarioAtual(UUID.randomUUID(), Perfil.PROFESSOR);
    private final LocalDate hoje = LocalDate.of(2026, 11, 20);

    @Test
    void consultarIncluiAlunosComESemDiario() {
        permitirConsulta();
        var primeiro = aluno(alunoId, "Ana");
        var segundo = aluno(UUID.randomUUID(), "Bruno");

        when(matriculas.buscarAlunosAtuaisDaTurma(turmaId))
                .thenReturn(List.of(primeiro, segundo));
        when(diarios.findByTurmaIdAndData(turmaId, hoje))
                .thenReturn(List.of(existente()));

        var resposta = service.consultar(turmaId, hoje, professor);

        assertEquals(2, resposta.size());
        assertEquals(alunoId, resposta.getFirst().alunoId());
        assertTrue(resposta.getFirst().diario().editavel());
        assertNull(resposta.getLast().diario());

        verify(vinculo).exigirProfessorDaTurma(turmaId, professor);
    }

    @Test
    void paginacaoBuscaDiariosSomenteDosAlunosDaPagina() {
        permitirConsulta();
        var pagina = PageRequest.of(1, 20, Sort.by("nome", "id"));
        var primeiro = aluno(alunoId, "Ana");
        var segundo = aluno(UUID.randomUUID(), "Bruno");

        when(matriculas.buscarAlunosAtuaisDaChamada(turmaId, pagina))
                .thenReturn(new PageImpl<>(List.of(primeiro, segundo), pagina, 22));
        when(diarios.findByTurmaIdAndDataAndAlunoIdIn(
                turmaId, hoje, List.of(alunoId, segundo.getId())))
                .thenReturn(List.of(existente()));

        var resposta = service.consultarPaginado(turmaId, hoje, 1, professor);

        assertEquals(22, resposta.getTotalElements());
        assertNotNull(resposta.getContent().getFirst().diario());
        assertNull(resposta.getContent().getLast().diario());

        verify(diarios).findByTurmaIdAndDataAndAlunoIdIn(
                turmaId, hoje, List.of(alunoId, segundo.getId()));
    }

    @Test
    void paginaVaziaNaoBuscaDiarios() {
        permitirConsulta();
        when(matriculas.buscarAlunosAtuaisDaChamada(any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        assertTrue(service.consultarPaginado(turmaId, hoje, 0, professor).isEmpty());

        verifyNoInteractions(diarios);
    }

    @Test
    void paginaNegativaNaoConsultaMatriculas() {
        permitirConsulta();

        assertThrows(RegraNegocioException.class,
                () -> service.consultarPaginado(turmaId, hoje, -1, professor));

        verifyNoInteractions(matriculas, diarios);
    }

    @Test
    void professorSemVinculoNaoConsultaNemRegistra() {
        doThrow(new AccessDeniedException("Sem vínculo"))
                .when(vinculo).exigirProfessorDaTurma(turmaId, professor);

        assertThrows(AccessDeniedException.class,
                () -> service.consultar(turmaId, hoje, professor));
        assertThrows(AccessDeniedException.class,
                () -> service.registrar(
                        turmaId, alunoId, hoje, pedido(null), professor));

        verifyNoInteractions(turmas, alunos, matriculas, diarios, auditoria);
    }

    @Test
    void dataNulaOuFuturaNaoPodeSerRegistrada() {
        when(suporte.hoje()).thenReturn(hoje);

        assertThrows(RegraNegocioException.class,
                () -> service.registrar(
                        turmaId, alunoId, null, pedido(null), professor));
        assertThrows(RegraNegocioException.class,
                () -> service.registrar(
                        turmaId, alunoId, hoje.plusDays(1), pedido(null), professor));

        verifyNoInteractions(turmas, diarios, auditoria);
    }

    @Test
    void turmaInativaOuInexistenteImpedeConsulta() {
        when(suporte.hoje()).thenReturn(hoje);

        assertThrows(TurmaNaoEncontradaException.class,
                () -> service.consultar(turmaId, hoje, professor));

        var turma = new Turma();
        turma.setAtivo(false);
        when(turmas.findById(turmaId)).thenReturn(Optional.of(turma));

        assertThrows(RegraNegocioException.class,
                () -> service.consultar(turmaId, hoje, professor));

        verifyNoInteractions(matriculas, diarios);
    }

    @Test
    void registrarNovoDiarioPersisteConteudoEAutoria() {
        permitirRegistro();
        var id = UUID.randomUUID();

        when(diarios.saveAndFlush(any())).thenAnswer(chamada -> {
            DiarioRotina diario = chamada.getArgument(0);
            diario.setId(id);
            return diario;
        });

        var resposta = service.registrar(
                turmaId, alunoId, hoje, pedido(null), professor);

        var captor = ArgumentCaptor.forClass(DiarioRotina.class);
        verify(diarios).saveAndFlush(captor.capture());
        var salvo = captor.getValue();

        assertEquals(alunoId, salvo.getAlunoId());
        assertEquals(turmaId, salvo.getTurmaId());
        assertEquals(professor.id(), salvo.getProfessorId());
        assertEquals(hoje, salvo.getData());
        assertNotNull(salvo.getCriadoEm());
        assertEquals(Alimentacao.COMEU_BEM, resposta.alimentacao());
        assertEquals(Sono.DORMIU_BEM, resposta.sono());
        assertTrue(resposta.evacuacao());
        assertEquals(Humor.CALMO, resposta.humor());
        assertEquals(Higiene.HIGIENIZADO, resposta.higiene());
        assertEquals("Participou bem.", resposta.observacaoProfessor());
        assertTrue(resposta.editavel());

        verify(auditoria).aposCommit(
                "DIARIO_ROTINA_REGISTRADO", professor.id(), id);
    }

    @Test
    void turmaInexistenteOuInativaImpedeRegistro() {
        when(suporte.hoje()).thenReturn(hoje);

        assertThrows(TurmaNaoEncontradaException.class,
                () -> service.registrar(
                        turmaId, alunoId, hoje, pedido(null), professor));

        var turma = new Turma();
        turma.setAtivo(false);
        when(turmas.buscarPorIdComBloqueio(turmaId)).thenReturn(Optional.of(turma));

        assertThrows(RegraNegocioException.class,
                () -> service.registrar(
                        turmaId, alunoId, hoje, pedido(null), professor));

        verifyNoInteractions(diarios, auditoria);
    }

    @Test
    void alunoInexistenteInativoOuSemMatriculaNaoRecebeDiario() {
        permitirRegistro();
        when(alunos.findById(alunoId)).thenReturn(Optional.empty());

        assertThrows(AlunoNaoEncontradoException.class,
                () -> service.registrar(
                        turmaId, alunoId, hoje, pedido(null), professor));

        var aluno = aluno(alunoId, "Ana");
        aluno.setAtivo(false);
        when(alunos.findById(alunoId)).thenReturn(Optional.of(aluno));

        assertThrows(RegraNegocioException.class,
                () -> service.registrar(
                        turmaId, alunoId, hoje, pedido(null), professor));

        aluno.setAtivo(true);
        when(matriculas.existsByTurmaIdAndAlunoIdAndEncerradoEmIsNull(
                turmaId, alunoId)).thenReturn(false);

        assertThrows(RegraNegocioException.class,
                () -> service.registrar(
                        turmaId, alunoId, hoje, pedido(null), professor));

        verifyNoInteractions(diarios, auditoria);
    }

    @Test
    void diarioNovoComVersaoInformadaGeraConflito() {
        permitirRegistro();

        assertThrows(ConflitoVersaoException.class,
                () -> service.registrar(
                        turmaId, alunoId, hoje, pedido(0L), professor));

        verify(diarios, never()).saveAndFlush(any());
        verifyNoInteractions(auditoria);
    }

    @Test
    void autorPodeEditarDiarioExistente() {
        permitirRegistro();
        var diario = existente();

        when(diarios.findByAlunoIdAndData(alunoId, hoje))
                .thenReturn(Optional.of(diario));

        var resposta = service.registrar(
                turmaId, alunoId, hoje, pedido(2L), professor);

        assertEquals(diario.getId(), resposta.id());
        assertEquals("Participou bem.", resposta.observacaoProfessor());

        verify(diarios).saveAndFlush(diario);
        verify(auditoria).aposCommit(
                "DIARIO_ROTINA_EDITADO", professor.id(), diario.getId());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {1, 3})
    void versaoAusenteOuDiferenteImpedeEdicao(Long versao) {
        permitirRegistro();
        var diario = existente();

        when(diarios.findByAlunoIdAndData(alunoId, hoje))
                .thenReturn(Optional.of(diario));

        assertThrows(ConflitoVersaoException.class,
                () -> service.registrar(
                        turmaId, alunoId, hoje, pedido(versao), professor));

        verify(diarios, never()).saveAndFlush(any());
        verifyNoInteractions(auditoria);
    }

    @Test
    void diarioDeOutraTurmaNaoPodeSerEditadoNemExcluido() {
        permitirRegistro();
        var diario = existente();
        diario.setTurmaId(UUID.randomUUID());

        when(diarios.findByAlunoIdAndData(alunoId, hoje))
                .thenReturn(Optional.of(diario));

        assertThrows(DiarioRotinaNaoEncontradoException.class,
                () -> service.registrar(
                        turmaId, alunoId, hoje, pedido(2L), professor));
        assertThrows(DiarioRotinaNaoEncontradoException.class,
                () -> service.excluir(turmaId, alunoId, hoje, professor));

        verify(diarios, never()).saveAndFlush(any());
        verify(diarios, never()).delete(any());
        verifyNoInteractions(auditoria);
    }

    @Test
    void diarioDeOutroAutorNaoPodeSerEditadoNemExcluido() {
        permitirRegistro();
        var diario = existente();
        diario.setProfessorId(UUID.randomUUID());

        when(diarios.findByAlunoIdAndData(alunoId, hoje))
                .thenReturn(Optional.of(diario));

        assertThrows(AccessDeniedException.class,
                () -> service.registrar(
                        turmaId, alunoId, hoje, pedido(2L), professor));
        assertThrows(AccessDeniedException.class,
                () -> service.excluir(turmaId, alunoId, hoje, professor));

        verify(diarios, never()).saveAndFlush(any());
        verify(diarios, never()).delete(any());
        verifyNoInteractions(auditoria);
    }

    @Test
    void excluirInexistenteNaoGravaAuditoria() {
        when(suporte.hoje()).thenReturn(hoje);

        assertThrows(DiarioRotinaNaoEncontradoException.class,
                () -> service.excluir(turmaId, alunoId, hoje, professor));

        verify(diarios, never()).delete(any());
        verifyNoInteractions(auditoria);
    }

    @Test
    void autorPodeExcluirSeuDiario() {
        when(suporte.hoje()).thenReturn(hoje);
        var diario = existente();

        when(diarios.findByAlunoIdAndData(alunoId, hoje))
                .thenReturn(Optional.of(diario));

        service.excluir(turmaId, alunoId, hoje, professor);

        verify(diarios).delete(diario);
        verify(diarios).flush();
        verify(auditoria).aposCommit(
                "DIARIO_ROTINA_EXCLUIDO", professor.id(), diario.getId());
    }

    private void permitirConsulta() {
        when(suporte.hoje()).thenReturn(hoje);
        when(turmas.findById(turmaId)).thenReturn(Optional.of(new Turma()));
    }

    private void permitirRegistro() {
        when(suporte.hoje()).thenReturn(hoje);
        when(turmas.buscarPorIdComBloqueio(turmaId))
                .thenReturn(Optional.of(new Turma()));
        when(alunos.findById(alunoId))
                .thenReturn(Optional.of(aluno(alunoId, "Ana")));
        when(matriculas.existsByTurmaIdAndAlunoIdAndEncerradoEmIsNull(
                turmaId, alunoId)).thenReturn(true);
    }

    private Aluno aluno(UUID id, String nome) {
        var aluno = new Aluno();
        aluno.setId(id);
        aluno.setNome(nome);
        aluno.setAtivo(true);
        return aluno;
    }

    private DiarioRotina existente() {
        var diario = new DiarioRotina();
        diario.setId(UUID.randomUUID());
        diario.setAlunoId(alunoId);
        diario.setTurmaId(turmaId);
        diario.setProfessorId(professor.id());
        diario.setData(hoje);
        diario.setVersao(2L);
        return diario;
    }

    private RegistrarDiarioRequest pedido(Long versao) {
        return new RegistrarDiarioRequest(
                Alimentacao.COMEU_BEM,
                Sono.DORMIU_BEM,
                true,
                Humor.CALMO,
                Higiene.HIGIENIZADO,
                "  Participou bem.  ",
                versao);
    }
}