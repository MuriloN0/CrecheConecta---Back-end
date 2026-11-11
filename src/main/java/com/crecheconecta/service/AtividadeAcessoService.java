package com.crecheconecta.service;

import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.SituacaoCadastro;
import com.crecheconecta.entity.Turma;
import com.crecheconecta.repository.AtividadeTurmaRepository;
import com.crecheconecta.repository.UsuarioRepository;
import com.crecheconecta.security.UsuarioAtual;
import com.crecheconecta.security.UsuarioAutenticado;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AtividadeAcessoService {

    private final UsuarioRepository usuarios;
    private final AtividadeTurmaRepository turmas;

    public AtividadeAcessoService(UsuarioRepository usuarios, AtividadeTurmaRepository turmas) {

        this.usuarios = usuarios;
        this.turmas = turmas;
    }

    @Transactional(readOnly = true)
    public UsuarioAtual usuarioAtual() {

        var autenticacao = SecurityContextHolder.getContext().getAuthentication();

        if (autenticacao == null || !autenticacao.isAuthenticated() || !(autenticacao.getPrincipal() instanceof UsuarioAutenticado autenticado)) {

            throw new AuthenticationCredentialsNotFoundException("Autenticação necessária.");
        }

        var atual = UsuarioAtual.de(autenticado);

        var usuario = usuarios.findById(atual.id()).orElseThrow(this::acessoNegado);

        if (!usuario.isAtivo() || usuario.getSituacaoCadastro() != SituacaoCadastro.APROVADO || usuario.getPerfil() != atual.perfil()) {

            throw acessoNegado();
        }

        return atual;
    }

    @Transactional(readOnly = true)
    public List<Turma> turmasPermitidas(UsuarioAtual usuario) {

        return switch (usuario.perfil()) {

            case DIRECAO -> turmas.buscarDaDirecao();
            case PROFESSOR -> turmas.buscarDoProfessor(usuario.id());
            case RESPONSAVEL -> turmas.buscarDoResponsavel(usuario.id());
        };
    }

    @Transactional(readOnly = true)
    public void validarTurma(UUID turmaId, UsuarioAtual usuario) {

        boolean permitido = turmaId != null && turmasPermitidas(usuario).stream().anyMatch(turma -> turma.getId().equals(turmaId));

        if (!permitido) {

            throw acessoNegado();
        }
    }

    public void exigirProfessor(UsuarioAtual usuario) {

        if (usuario.perfil() != Perfil.PROFESSOR) {

            throw acessoNegado();
        }
    }

    public void exigirAutor(UUID professorId, UsuarioAtual usuario) {

        exigirProfessor(usuario);

        if (!usuario.id().equals(professorId)) {

            throw acessoNegado();
        }
    }

    private AccessDeniedException acessoNegado() {

        return new AccessDeniedException("Você não possui permissão para acessar ou alterar esta atividade.");
    }
}