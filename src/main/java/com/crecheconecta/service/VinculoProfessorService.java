package com.crecheconecta.service;

import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.SituacaoCadastro;
import com.crecheconecta.repository.TurmaProfessorRepository;
import com.crecheconecta.repository.UsuarioRepository;
import com.crecheconecta.security.UsuarioAtual;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class VinculoProfessorService {

    private final UsuarioRepository usuarios;
    private final TurmaProfessorRepository professores;

    public VinculoProfessorService(UsuarioRepository usuarios, TurmaProfessorRepository professores) {

        this.usuarios = usuarios;
        this.professores = professores;
    }

    @Transactional(readOnly = true)
    public void exigirProfessorDaTurma(UUID turmaId, UsuarioAtual solicitante) {

        if (solicitante == null || solicitante.id() == null || solicitante.perfil() != Perfil.PROFESSOR) {

            throw acessoNegado();
        }

        var usuario = usuarios.findById(solicitante.id()).orElseThrow(this::acessoNegado);

        if (!usuario.isAtivo() || usuario.getPerfil() != Perfil.PROFESSOR || usuario.getSituacaoCadastro() != SituacaoCadastro.APROVADO) {

            throw acessoNegado();
        }

        if (turmaId == null || !professores.existsByTurmaIdAndProfessorIdAndEncerradoEmIsNull(turmaId, solicitante.id())) {

            throw acessoNegado();
        }
    }

    private AccessDeniedException acessoNegado() {

        return new AccessDeniedException("Acesso restrito ao professor vinculado à turma.");
    }
}
