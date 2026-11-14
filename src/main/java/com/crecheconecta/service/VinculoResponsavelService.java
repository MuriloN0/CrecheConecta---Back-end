package com.crecheconecta.service;

import com.crecheconecta.entity.Aluno;
import com.crecheconecta.entity.MatriculaTurma;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.exception.AlunoNaoEncontradoException;
import com.crecheconecta.exception.TermoPendenteException;
import com.crecheconecta.repository.AlunoRepository;
import com.crecheconecta.repository.AlunoResponsavelRepository;
import com.crecheconecta.repository.MatriculaTurmaRepository;
import com.crecheconecta.repository.UsuarioRepository;
import com.crecheconecta.security.UsuarioAtual;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * "Porteiro" das features dos pais.
 * Confere se quem está logado é um RESPONSAVEL ativo, que aceitou a versão vigente dos termos (LGPD),
 * e se o aluno pedido é filho dele.
 * Propositalmente SEM @PreAuthorize("hasRole('DIRECAO')"): é usado pelos services dos pais.
 */
@Service
public class VinculoResponsavelService {

    private final AlunoResponsavelRepository vinculos;
    private final AlunoRepository alunos;
    private final MatriculaTurmaRepository matriculas;
    private final UsuarioRepository usuarios;
    private final TermoService termos;

    public VinculoResponsavelService(AlunoResponsavelRepository vinculos, AlunoRepository alunos, MatriculaTurmaRepository matriculas, UsuarioRepository usuarios, TermoService termos) {
        this.vinculos = vinculos;
        this.alunos = alunos;
        this.matriculas = matriculas;
        this.usuarios = usuarios;
        this.termos = termos;
    }

    /**
     * Garante que o solicitante é um responsável ativo (perfil no token + re-checagem no banco)
     * e que já aceitou a versão vigente dos termos.
     * Falha -> 403 (ACESSO_NEGADO ou TERMO_PENDENTE).
     * As rotas do próprio termo (/api/termo/status e /api/termo/aceite) não passam por aqui.
     */
    @Transactional(readOnly = true)
    public void exigirResponsavelAtivo(UsuarioAtual solicitante) {
        if (solicitante == null || solicitante.perfil() != Perfil.RESPONSAVEL) {
            throw acessoNegado();
        }

        var responsavel = usuarios.findById(solicitante.id()).orElseThrow(this::acessoNegado);

        if (!responsavel.isAtivo() || responsavel.getPerfil() != Perfil.RESPONSAVEL) {
            throw acessoNegado();
        }

        if (termos.possuiAceitePendente(responsavel.getId())) {
            throw new TermoPendenteException();
        }
    }

    /**
     * Garante que o solicitante é responsável ATUAL por este aluno, e que o aluno está ativo.
     * Se não for, responde 404 (e não 403) para não revelar que o aluno existe (anti-enumeração).
     */
    @Transactional(readOnly = true)
    public Aluno exigirResponsavelDoAluno(UsuarioAtual solicitante, UUID alunoId) {
        exigirResponsavelAtivo(solicitante);

        if (alunoId == null || !vinculos.existsByAlunoIdAndResponsavelIdAndEncerradoEmIsNull(alunoId, solicitante.id())) {
            throw new AlunoNaoEncontradoException();
        }

        return alunos.findById(alunoId).filter(Aluno::isAtivo).orElseThrow(AlunoNaoEncontradoException::new);
    }

    /**
     * Turma atual do aluno (vazio se ele não estiver matriculado).
     */
    @Transactional(readOnly = true)
    public Optional<UUID> turmaAtualDoAluno(UUID alunoId) {
        return matriculas.findByAlunoIdAndEncerradoEmIsNull(alunoId).map(MatriculaTurma::getTurmaId);
    }

    private AccessDeniedException acessoNegado() {
        return new AccessDeniedException("Acesso restrito aos responsáveis.");
    }
}
