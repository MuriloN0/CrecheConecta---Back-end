package com.crecheconecta.service;

import com.crecheconecta.dto.MatriculaTurmaResponse;
import com.crecheconecta.entity.MatriculaTurma;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.exception.AlunoNaoEncontradoException;
import com.crecheconecta.exception.MatriculaNaoEncontradaException;
import com.crecheconecta.exception.RegraNegocioException;
import com.crecheconecta.exception.TurmaNaoEncontradaException;
import com.crecheconecta.repository.AlunoRepository;
import com.crecheconecta.repository.MatriculaTurmaRepository;
import com.crecheconecta.repository.TurmaRepository;
import com.crecheconecta.repository.UsuarioRepository;
import com.crecheconecta.security.UsuarioAtual;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
@PreAuthorize("hasRole('DIRECAO')")
public class MatriculaTurmaService {

    private final MatriculaTurmaRepository matriculas;
    private final AlunoRepository alunos;
    private final TurmaRepository turmas;
    private final UsuarioRepository usuarios;
    private final AuditoriaService auditoria;
    private final Clock clock;

    public MatriculaTurmaService(MatriculaTurmaRepository matriculas, AlunoRepository alunos, TurmaRepository turmas, UsuarioRepository usuarios, AuditoriaService auditoria, Clock clock) {

        this.matriculas = matriculas;
        this.alunos = alunos;
        this.turmas = turmas;
        this.usuarios = usuarios;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    @Transactional
    public MatriculaTurmaResponse matricular(UUID turmaId, UUID alunoId, UsuarioAtual solicitante) {

        validarDirecao(solicitante);

        var aluno = alunos.buscarPorIdComBloqueio(alunoId).orElseThrow(AlunoNaoEncontradoException::new);

        var turma = turmas.buscarPorIdComBloqueio(turmaId).orElseThrow(TurmaNaoEncontradaException::new);

        if (!aluno.isAtivo()) {

            throw new RegraNegocioException("Não é possível matricular um aluno inativo.");
        }

        if (!turma.isAtivo()) {

            throw new RegraNegocioException("Não é possível matricular em uma turma inativa.");
        }

        if (matriculas.existsByAlunoIdAndEncerradoEmIsNull(alunoId)) {

            throw new RegraNegocioException("O aluno já possui uma matrícula atual. " + "Encerre a matrícula anterior antes de continuar.");
        }

        var matricula = new MatriculaTurma();
        matricula.setAlunoId(alunoId);
        matricula.setTurmaId(turmaId);
        matricula.setCriadoEm(clock.instant());

        matriculas.saveAndFlush(matricula);

        auditoria.aposCommit("ALUNO_MATRICULADO", solicitante.id(), matricula.getId());

        return MatriculaTurmaResponse.de(matricula);
    }

    @Transactional(readOnly = true)
    public Page<MatriculaTurmaResponse> listar(UUID turmaId, int pagina, int tamanho, boolean incluirEncerradas, UsuarioAtual solicitante) {

        validarDirecao(solicitante);

        if (pagina < 0 || tamanho < 1 || tamanho > 100) {

            throw new RegraNegocioException("Informe página a partir de zero " + "e tamanho entre 1 e 100.");
        }

        if (!turmas.existsById(turmaId)) {

            throw new TurmaNaoEncontradaException();
        }

        var paginacao = PageRequest.of(pagina, tamanho, Sort.by("criadoEm").ascending().and(Sort.by("id").ascending()));

        Page<MatriculaTurma> resultado;

        if (incluirEncerradas) {

            resultado = matriculas.findByTurmaId(turmaId, paginacao);
        } else {

            resultado = matriculas.findByTurmaIdAndEncerradoEmIsNull(turmaId, paginacao);
        }

        return resultado.map(MatriculaTurmaResponse::de);
    }

    @Transactional
    public MatriculaTurmaResponse encerrar(UUID turmaId, UUID matriculaId, UsuarioAtual solicitante) {

        validarDirecao(solicitante);

        UUID alunoId = matriculas.buscarAlunoId(matriculaId, turmaId).orElseThrow(MatriculaNaoEncontradaException::new);

        alunos.buscarPorIdComBloqueio(alunoId).orElseThrow(AlunoNaoEncontradoException::new);

        turmas.buscarPorIdComBloqueio(turmaId).orElseThrow(TurmaNaoEncontradaException::new);

        var matricula = matriculas.findByIdAndTurmaId(matriculaId, turmaId).orElseThrow(MatriculaNaoEncontradaException::new);

        if (matricula.getEncerradoEm() != null) {

            throw new RegraNegocioException("Esta matrícula já foi encerrada.");
        }

        matricula.setEncerradoEm(clock.instant());

        matriculas.flush();

        auditoria.aposCommit("MATRICULA_ENCERRADA", solicitante.id(), matricula.getId());

        return MatriculaTurmaResponse.de(matricula);
    }

    private void validarDirecao(UsuarioAtual solicitante) {

        if (solicitante == null || solicitante.perfil() != Perfil.DIRECAO) {

            throw acessoNegado();
        }

        var diretor = usuarios.findById(solicitante.id()).orElseThrow(this::acessoNegado);

        if (!diretor.isAtivo() || diretor.getPerfil() != Perfil.DIRECAO) {

            throw acessoNegado();
        }
    }

    private AccessDeniedException acessoNegado() {

        return new AccessDeniedException("Acesso restrito à direção.");
    }
}