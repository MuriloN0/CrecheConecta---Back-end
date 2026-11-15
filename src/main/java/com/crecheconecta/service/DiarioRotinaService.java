package com.crecheconecta.service;

import com.crecheconecta.dto.DiarioAlunoResponse;
import com.crecheconecta.dto.DiarioRegistroResponse;
import com.crecheconecta.dto.RegistrarDiarioRequest;
import com.crecheconecta.entity.DiarioRotina;
import com.crecheconecta.exception.AlunoNaoEncontradoException;
import com.crecheconecta.exception.ConflitoVersaoException;
import com.crecheconecta.exception.DiarioRotinaNaoEncontradoException;
import com.crecheconecta.exception.RegraNegocioException;
import com.crecheconecta.exception.TurmaNaoEncontradaException;
import com.crecheconecta.repository.AlunoRepository;
import com.crecheconecta.repository.DiarioRotinaRepository;
import com.crecheconecta.repository.MatriculaTurmaRepository;
import com.crecheconecta.repository.TurmaRepository;
import com.crecheconecta.security.UsuarioAtual;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@PreAuthorize("hasRole('PROFESSOR')")
public class DiarioRotinaService {

    private final VinculoProfessorService vinculoProfessor;
    private final TurmaRepository turmas;
    private final MatriculaTurmaRepository matriculas;
    private final AlunoRepository alunos;
    private final DiarioRotinaRepository diarios;
    private final MalaVirtualSuporte suporte;
    private final AuditoriaService auditoria;

    public DiarioRotinaService(
            VinculoProfessorService vinculoProfessor,
            TurmaRepository turmas,
            MatriculaTurmaRepository matriculas,
            AlunoRepository alunos,
            DiarioRotinaRepository diarios,
            MalaVirtualSuporte suporte,
            AuditoriaService auditoria
    ) {
        this.vinculoProfessor = vinculoProfessor;
        this.turmas = turmas;
        this.matriculas = matriculas;
        this.alunos = alunos;
        this.diarios = diarios;
        this.suporte = suporte;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public List<DiarioAlunoResponse> consultar(UUID turmaId, LocalDate data, UsuarioAtual solicitante) {
        validarConsulta(turmaId, data, solicitante);

        Map<UUID, DiarioRotina> diariosPorAluno = diarios.findByTurmaIdAndData(turmaId, data)
                .stream()
                .collect(Collectors.toMap(DiarioRotina::getAlunoId, Function.identity()));

        return matriculas.buscarAlunosAtuaisDaTurma(turmaId).stream()
                .map(aluno -> {
                    var diario = diariosPorAluno.get(aluno.getId());
                    var registro = diario == null ? null : DiarioRegistroResponse.de(diario, solicitante.id());
                    return new DiarioAlunoResponse(aluno.getId(), aluno.getNome(), registro);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<DiarioAlunoResponse> consultarPaginado(
            UUID turmaId, LocalDate data, int pagina, UsuarioAtual solicitante
    ) {
        validarConsulta(turmaId, data, solicitante);

        if (pagina < 0) {
            throw new RegraNegocioException("Informe uma página a partir de zero.");
        }

        var paginacao = PageRequest.of(pagina, 20, Sort.by("nome", "id"));
        var alunosDaPagina = matriculas.buscarAlunosAtuaisDaChamada(turmaId, paginacao);

        if (alunosDaPagina.isEmpty()) {
            return alunosDaPagina.map(aluno -> new DiarioAlunoResponse(aluno.getId(), aluno.getNome(), null));
        }

        var ids = alunosDaPagina.stream().map(aluno -> aluno.getId()).toList();
        var diariosPorAluno = diarios.findByTurmaIdAndDataAndAlunoIdIn(turmaId, data, ids)
                .stream()
                .collect(Collectors.toMap(DiarioRotina::getAlunoId, Function.identity()));

        return alunosDaPagina.map(aluno -> {
            var diario = diariosPorAluno.get(aluno.getId());
            var registro = diario == null ? null : DiarioRegistroResponse.de(diario, solicitante.id());
            return new DiarioAlunoResponse(aluno.getId(), aluno.getNome(), registro);
        });
    }

    @Transactional
    public DiarioRegistroResponse registrar(
            UUID turmaId, UUID alunoId, LocalDate data,
            RegistrarDiarioRequest request, UsuarioAtual solicitante
    ) {
        vinculoProfessor.exigirProfessorDaTurma(turmaId, solicitante);
        validarData(data);

        var turma = turmas.buscarPorIdComBloqueio(turmaId).orElseThrow(TurmaNaoEncontradaException::new);
        if (!turma.isAtivo()) {
            throw new RegraNegocioException("A turma está inativa.");
        }

        var aluno = alunos.findById(alunoId).orElseThrow(AlunoNaoEncontradoException::new);
        if (!aluno.isAtivo()) {
            throw new RegraNegocioException("O aluno está inativo.");
        }
        if (!matriculas.existsByTurmaIdAndAlunoIdAndEncerradoEmIsNull(turmaId, alunoId)) {
            throw new RegraNegocioException("O aluno não possui matrícula atual nesta turma.");
        }

        var diario = diarios.findByAlunoIdAndData(alunoId, data).orElse(null);
        boolean novo = diario == null;

        if (novo) {
            if (request.versao() != null) {
                throw new ConflitoVersaoException();
            }

            diario = new DiarioRotina();
            diario.setAlunoId(alunoId);
            diario.setTurmaId(turmaId);
            diario.setProfessorId(solicitante.id());
            diario.setData(data);
            diario.setCriadoEm(Instant.now());
        } else {
            exigirDiarioDaTurma(diario, turmaId);
            exigirAutor(diario, solicitante);

            if (request.versao() == null || !Objects.equals(diario.getVersao(), request.versao())) {
                throw new ConflitoVersaoException();
            }
        }

        diario.setAlimentacao(request.alimentacao());
        diario.setSono(request.sono());
        diario.setEvacuacao(request.evacuacao());
        diario.setHumor(request.humor());
        diario.setHigiene(request.higiene());
        diario.setObservacaoProfessor(MalaVirtualSuporte.textoOpcional(request.observacaoProfessor()));

        // O flush garante que a resposta tenha a versão atualizada do registro.
        diarios.saveAndFlush(diario);
        auditoria.aposCommit(
                novo ? "DIARIO_ROTINA_REGISTRADO" : "DIARIO_ROTINA_EDITADO",
                solicitante.id(),
                diario.getId()
        );

        return DiarioRegistroResponse.de(diario, solicitante.id());
    }

    @Transactional
    public void excluir(UUID turmaId, UUID alunoId, LocalDate data, UsuarioAtual solicitante) {
        vinculoProfessor.exigirProfessorDaTurma(turmaId, solicitante);
        validarData(data);

        var diario = diarios.findByAlunoIdAndData(alunoId, data)
                .orElseThrow(DiarioRotinaNaoEncontradoException::new);
        exigirDiarioDaTurma(diario, turmaId);
        exigirAutor(diario, solicitante);

        diarios.delete(diario);
        diarios.flush();
        auditoria.aposCommit("DIARIO_ROTINA_EXCLUIDO", solicitante.id(), diario.getId());
    }

    private void validarConsulta(UUID turmaId, LocalDate data, UsuarioAtual solicitante) {
        vinculoProfessor.exigirProfessorDaTurma(turmaId, solicitante);
        validarData(data);

        var turma = turmas.findById(turmaId).orElseThrow(TurmaNaoEncontradaException::new);
        if (!turma.isAtivo()) {
            throw new RegraNegocioException("A turma está inativa.");
        }
    }

    private void exigirDiarioDaTurma(DiarioRotina diario, UUID turmaId) {
        if (!diario.getTurmaId().equals(turmaId)) {
            throw new DiarioRotinaNaoEncontradoException();
        }
    }

    private void exigirAutor(DiarioRotina diario, UsuarioAtual solicitante) {
        if (!diario.getProfessorId().equals(solicitante.id())) {
            throw new AccessDeniedException("Somente o professor que registrou o diário pode alterá-lo.");
        }
    }

    private void validarData(LocalDate data) {
        if (data == null || data.isAfter(suporte.hoje())) {
            throw new RegraNegocioException("A data do diário não pode ser futura.");
        }
    }
}