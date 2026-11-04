package com.crecheconecta.service;

import com.crecheconecta.dto.ChamadaAlunoResponse;
import com.crecheconecta.dto.PresencaResponse;
import com.crecheconecta.dto.RegistrarPresencaRequest;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.Presenca;
import com.crecheconecta.entity.SituacaoCadastro;
import com.crecheconecta.exception.AlunoNaoEncontradoException;
import com.crecheconecta.exception.ConflitoVersaoException;
import com.crecheconecta.exception.RegraNegocioException;
import com.crecheconecta.exception.TurmaNaoEncontradaException;
import com.crecheconecta.repository.*;
import com.crecheconecta.security.UsuarioAtual;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@PreAuthorize("hasRole('PROFESSOR')")
public class ChamadaService {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    private final UsuarioRepository usuarios;
    private final TurmaRepository turmas;
    private final TurmaProfessorRepository professores;
    private final MatriculaTurmaRepository matriculas;
    private final AlunoRepository alunos;
    private final PresencaRepository presencas;
    private final AuditoriaService auditoria;
    private final Clock clock;

    public ChamadaService(UsuarioRepository usuarios, TurmaRepository turmas, TurmaProfessorRepository professores, MatriculaTurmaRepository matriculas, AlunoRepository alunos, PresencaRepository presencas, AuditoriaService auditoria, Clock clock) {

        this.usuarios = usuarios;
        this.turmas = turmas;
        this.professores = professores;
        this.matriculas = matriculas;
        this.alunos = alunos;
        this.presencas = presencas;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<ChamadaAlunoResponse> consultar(UUID turmaId, LocalDate data, int pagina, int tamanho, UsuarioAtual solicitante) {

        validarProfessor(turmaId, solicitante);
        validarData(data);

        var turma = turmas.findById(turmaId).orElseThrow(TurmaNaoEncontradaException::new);

        if (!turma.isAtivo()) {

            throw new RegraNegocioException("A turma está inativa.");
        }

        if (pagina < 0 || tamanho < 1 || tamanho > 100) {

            throw new RegraNegocioException("Informe página a partir de zero " + "e tamanho entre 1 e 100.");
        }

        var paginacao = PageRequest.of(pagina, tamanho, Sort.by("nome").ascending().and(Sort.by("id").ascending()));

        var registros = presencas.findByTurmaIdAndData(turmaId, data)
                .stream()
                .collect(Collectors.toMap(
                        Presenca::getAlunoId,
                        Function.identity()
                ));

        return matriculas.buscarAlunosAtuaisDaChamada(turmaId, paginacao).map(aluno -> {

            var registro = registros.get(aluno.getId());

            return new ChamadaAlunoResponse(
                    aluno.getId(),
                    aluno.getNome(),
                    registro == null ? null : registro.isPresente(),
                    registro == null ? null : registro.getVersao()
            );
        });
    }

    @Transactional
    public PresencaResponse registrar(UUID turmaId, UUID alunoId, LocalDate data, RegistrarPresencaRequest request, UsuarioAtual solicitante) {

        validarProfessor(turmaId, solicitante);
        validarData(data);

        var turma = turmas.buscarPorIdComBloqueio(turmaId)
                .orElseThrow(TurmaNaoEncontradaException::new);

        if (!turma.isAtivo()) {
            throw new RegraNegocioException("A turma está inativa.");
        }

        var aluno = alunos.findById(alunoId)
                .orElseThrow(AlunoNaoEncontradoException::new);

        if (!aluno.isAtivo()) {
            throw new RegraNegocioException("O aluno está inativo.");
        }

        if (!matriculas.existsByTurmaIdAndAlunoIdAndEncerradoEmIsNull(turmaId, alunoId)) {

            throw new RegraNegocioException("O aluno não possui matrícula atual nesta turma.");
        }

        if (request.presente() == null) {

            throw new RegraNegocioException("Informe se o aluno está presente.");
        }

        var registro = presencas.findByTurmaIdAndAlunoIdAndData(turmaId, alunoId, data).orElse(null);

        boolean novo = registro == null;

        if (novo) {
            if (request.versao() != null) {

                throw new ConflitoVersaoException();
            }

            registro = new Presenca();
            registro.setTurmaId(turmaId);
            registro.setAlunoId(alunoId);
            registro.setData(data);

        } else if (request.versao() == null || !Objects.equals(registro.getVersao(), request.versao())) {

            throw new ConflitoVersaoException();
        }

        registro.setPresente(request.presente());
        registro.setRegistradoPor(solicitante.id());
        registro.setAtualizadoEm(clock.instant());

        presencas.saveAndFlush(registro);

        validarData(data);

        auditoria.aposCommit(novo ? "PRESENCA_REGISTRADA" : "PRESENCA_ATUALIZADA", solicitante.id(), registro.getId());

        return PresencaResponse.de(registro);
    }

    private void validarProfessor(UUID turmaId, UsuarioAtual solicitante) {

        if (solicitante == null || solicitante.id() == null || solicitante.perfil() != Perfil.PROFESSOR) {

            throw acessoNegado();
        }

        var usuario = usuarios.findById(solicitante.id()).orElseThrow(this::acessoNegado);

        if (!usuario.isAtivo() || usuario.getPerfil() != Perfil.PROFESSOR || usuario.getSituacaoCadastro() != SituacaoCadastro.APROVADO) {

            throw acessoNegado();
        }

        if (!professores.existsByTurmaIdAndProfessorIdAndEncerradoEmIsNull(turmaId, solicitante.id())) {

            throw acessoNegado();
        }
    }

    private void validarData(LocalDate data) {
        var hoje = LocalDate.ofInstant(clock.instant(), FUSO);

        if (data == null || !data.equals(hoje)) {

            throw new RegraNegocioException("A chamada só pode ser consultada e alterada " + "no dia vigente.");
        }
    }

    private AccessDeniedException acessoNegado() {

        return new AccessDeniedException("Acesso restrito ao professor vinculado à turma.");
    }
}