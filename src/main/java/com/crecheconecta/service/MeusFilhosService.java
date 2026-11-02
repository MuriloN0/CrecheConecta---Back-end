package com.crecheconecta.service;

import com.crecheconecta.dto.FilhoResponse;
import com.crecheconecta.entity.Aluno;
import com.crecheconecta.entity.AlunoResponsavel;
import com.crecheconecta.repository.AlunoRepository;
import com.crecheconecta.repository.AlunoResponsavelRepository;
import com.crecheconecta.repository.TurmaRepository;
import com.crecheconecta.security.UsuarioAtual;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Portal dos pais: "Dados do filho" (Perfil da Criança).
 * O pai nunca informa o próprio id: ele vem do token (UsuarioAtual).
 */
@Service
public class MeusFilhosService {

    private final VinculoResponsavelService acessoResponsavel;
    private final AlunoResponsavelRepository vinculos;
    private final AlunoRepository alunos;
    private final TurmaRepository turmas;
    private final AuditoriaService auditoria;

    public MeusFilhosService(VinculoResponsavelService acessoResponsavel, AlunoResponsavelRepository vinculos, AlunoRepository alunos, TurmaRepository turmas, AuditoriaService auditoria) {
        this.acessoResponsavel = acessoResponsavel;
        this.vinculos = vinculos;
        this.alunos = alunos;
        this.turmas = turmas;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public List<FilhoResponse> listar(UsuarioAtual solicitante) {
        acessoResponsavel.exigirResponsavelAtivo(solicitante);

        var alunoIds = vinculos.findByResponsavelIdAndEncerradoEmIsNull(solicitante.id()).stream().map(AlunoResponsavel::getAlunoId).toList();

        var filhos = alunos.findAllById(alunoIds).stream().filter(Aluno::isAtivo).sorted(Comparator.comparing(Aluno::getNome)).map(this::paraResponse).toList();

        auditoria.registrar("FILHOS_LISTADOS", solicitante.id(), null);
        return filhos;
    }

    @Transactional(readOnly = true)
    public FilhoResponse visualizar(UUID alunoId, UsuarioAtual solicitante) {
        var aluno = acessoResponsavel.exigirResponsavelDoAluno(solicitante, alunoId);

        auditoria.registrar("FILHO_CONSULTADO", solicitante.id(), alunoId);
        return paraResponse(aluno);
    }

    private FilhoResponse paraResponse(Aluno aluno) {
        var turma = acessoResponsavel.turmaAtualDoAluno(aluno.getId()).flatMap(turmas::findById).orElse(null);

        return FilhoResponse.de(aluno, turma);
    }
}
