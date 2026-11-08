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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Diário de Rotina Digital, lado do PROFESSOR: consulta a turma no dia, registra, edita e exclui o diário de cada aluno.
 * (O lado do pai, só leitura, está em {@link DiarioRotinaFilhoService}.)
 *
 * Regras: só o professor vinculado à turma; o aluno precisa ter matrícula atual na turma; a data nunca é futura;
 * só quem criou o diário edita ou exclui; edição simultânea é barrada pela "versao".
 */
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

    public DiarioRotinaService(VinculoProfessorService vinculoProfessor, TurmaRepository turmas, MatriculaTurmaRepository matriculas, AlunoRepository alunos, DiarioRotinaRepository diarios, MalaVirtualSuporte suporte, AuditoriaService auditoria) {
        this.vinculoProfessor = vinculoProfessor;
        this.turmas = turmas;
        this.matriculas = matriculas;
        this.alunos = alunos;
        this.diarios = diarios;
        this.suporte = suporte;
        this.auditoria = auditoria;
    }

    /** Todos os alunos da turma com o diário do dia (ou null, se ainda não preenchido). */
    @Transactional(readOnly = true)
    public List<DiarioAlunoResponse> consultar(UUID turmaId, LocalDate data, UsuarioAtual solicitante) {

        vinculoProfessor.exigirProfessorDaTurma(turmaId, solicitante);
        validarData(data);

        var turma = turmas.findById(turmaId).orElseThrow(TurmaNaoEncontradaException::new);
        if (!turma.isAtivo()) {
            throw new RegraNegocioException("A turma está inativa.");
        }

        Map<UUID, DiarioRotina> diariosPorAluno = diarios.findByTurmaIdAndData(turmaId, data)
                .stream()
                .collect(Collectors.toMap(DiarioRotina::getAlunoId, Function.identity()));

        return matriculas.buscarAlunosAtuaisDaTurma(turmaId).stream().map(aluno -> {
            var diario = diariosPorAluno.get(aluno.getId());
            return new DiarioAlunoResponse(aluno.getId(), aluno.getNome(), diario == null ? null : DiarioRegistroResponse.de(diario, solicitante.id()));
        }).toList();
    }

    /** Cria o diário do aluno no dia (versao null) ou edita o que já existe (versao obrigatória). */
    @Transactional
    public DiarioRegistroResponse registrar(UUID turmaId, UUID alunoId, LocalDate data, RegistrarDiarioRequest request, UsuarioAtual solicitante) {

        vinculoProfessor.exigirProfessorDaTurma(turmaId, solicitante);
        validarData(data);

        // Trava a linha da turma até o fim da transação: dois professores salvando o mesmo aluno ao mesmo tempo não furam o UNIQUE.
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
            diario.setCriadoEm(java.time.Instant.now());
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

        // saveAndFlush: o @Version só incrementa no flush; sem ele a resposta devolveria a versão antiga.
        diarios.saveAndFlush(diario);

        auditoria.aposCommit(novo ? "DIARIO_ROTINA_REGISTRADO" : "DIARIO_ROTINA_EDITADO", solicitante.id(), diario.getId());
        return DiarioRegistroResponse.de(diario, solicitante.id());
    }

    @Transactional
    public void excluir(UUID turmaId, UUID alunoId, LocalDate data, UsuarioAtual solicitante) {

        vinculoProfessor.exigirProfessorDaTurma(turmaId, solicitante);
        validarData(data);

        var diario = diarios.findByAlunoIdAndData(alunoId, data).orElseThrow(DiarioRotinaNaoEncontradoException::new);
        exigirDiarioDaTurma(diario, turmaId);
        exigirAutor(diario, solicitante);

        diarios.delete(diario);
        diarios.flush();

        auditoria.aposCommit("DIARIO_ROTINA_EXCLUIDO", solicitante.id(), diario.getId());
    }

    // Um diário de outra turma não pode ser tocado pela URL desta turma (e não revelamos que ele existe).
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
