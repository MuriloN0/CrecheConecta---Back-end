package com.crecheconecta.service;

import com.crecheconecta.dto.AtualizarProfessorRequest;
import com.crecheconecta.dto.ProfessorResponse;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.SituacaoCadastro;
import com.crecheconecta.entity.Usuario;
import com.crecheconecta.exception.ConflitoVersaoException;
import com.crecheconecta.exception.ProfessorNaoEncontradoException;
import com.crecheconecta.exception.RegraNegocioException;
import com.crecheconecta.repository.SessaoRepository;
import com.crecheconecta.repository.UsuarioRepository;
import com.crecheconecta.security.UsuarioAtual;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.crecheconecta.repository.TurmaProfessorRepository;
import java.time.Clock;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
@PreAuthorize("hasRole('DIRECAO')")
public class ProfessorService {

    private final UsuarioRepository usuarios;
    private final SessaoRepository sessoes;
    private final AuditoriaService auditoria;
    private final TurmaProfessorRepository professoresTurma;
    private final Clock clock;

    public ProfessorService(UsuarioRepository usuarios, SessaoRepository sessoes, AuditoriaService auditoria, TurmaProfessorRepository professoresTurma, Clock clock) {

        this.usuarios = usuarios;
        this.sessoes = sessoes;
        this.auditoria = auditoria;
        this.professoresTurma = professoresTurma;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<ProfessorResponse> listar(int pagina, int tamanho, SituacaoCadastro situacao, UsuarioAtual solicitante) {

        validarDirecao(solicitante);

        if (pagina < 0 || tamanho < 1 || tamanho > 100) {

            throw new RegraNegocioException("Informe página a partir de zero " + "e tamanho entre 1 e 100.");
        }

        var paginacao = PageRequest.of(pagina, tamanho, Sort.by("nome").ascending().and(Sort.by("id").ascending()));

        Page<Usuario> resultado;

        if (situacao == null) {

            resultado = usuarios.findByPerfil(Perfil.PROFESSOR, paginacao);
        } else {

            resultado = usuarios.findByPerfilAndSituacaoCadastro(Perfil.PROFESSOR, situacao, paginacao);
        }

        return resultado.map(ProfessorResponse::de);
    }

    @Transactional(readOnly = true)
    public ProfessorResponse visualizar(UUID professorId, UsuarioAtual solicitante) {

        validarDirecao(solicitante);

        var professor = usuarios.findById(professorId)
                .filter(usuario -> usuario.getPerfil() == Perfil.PROFESSOR)
                .orElseThrow(ProfessorNaoEncontradoException::new);

        return ProfessorResponse.de(professor);
    }

    @Transactional
    public ProfessorResponse atualizar(UUID professorId, AtualizarProfessorRequest request, UsuarioAtual solicitante) {

        validarDirecao(solicitante);

        var professor = buscarComBloqueio(professorId, request.versao());

        String nome = request.nome().strip();

        if (nome.isBlank()) {

            throw new RegraNegocioException("Informe o nome do professor.");
        }

        professor.setNome(nome);
        professor.setTelefone(request.telefone());

        usuarios.flush();

        auditoria.aposCommit("PROFESSOR_ATUALIZADO", solicitante.id(), professor.getId());

        return ProfessorResponse.de(professor);
    }

    @Transactional
    public ProfessorResponse inativar(UUID professorId, Long versao, UsuarioAtual solicitante) {

        validarDirecao(solicitante);

        var professor = buscarComBloqueio(professorId, versao);

        if (professor.getSituacaoCadastro() != SituacaoCadastro.APROVADO) {

            throw new RegraNegocioException("Somente professores aprovados " + "podem ser inativados.");
        }

        var agora = clock.instant();

        professor.setSituacaoCadastro(SituacaoCadastro.INATIVO);
        professor.setAtivo(false);

        usuarios.flush();

        professoresTurma.encerrarDoProfessor(professorId, agora);

        auditoria.aposCommit("PROFESSOR_INATIVADO", solicitante.id(), professor.getId());

        return ProfessorResponse.de(professor);
    }

    private Usuario buscarComBloqueio(UUID professorId, Long versao) {

        var professor = usuarios.buscarPorIdComBloqueio(professorId)
                .filter(usuario -> usuario.getPerfil() == Perfil.PROFESSOR)
                .orElseThrow(ProfessorNaoEncontradoException::new);

        if (versao == null || !Objects.equals(professor.getVersao(), versao)) {

            throw new ConflitoVersaoException();
        }

        return professor;
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