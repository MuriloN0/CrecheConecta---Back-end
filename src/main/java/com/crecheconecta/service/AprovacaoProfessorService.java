package com.crecheconecta.service;

import com.crecheconecta.dto.ProfessorResponse;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.SituacaoCadastro;
import com.crecheconecta.entity.Usuario;
import com.crecheconecta.exception.ConflitoVersaoException;
import com.crecheconecta.exception.RegraNegocioException;
import com.crecheconecta.repository.UsuarioRepository;
import com.crecheconecta.security.UsuarioAtual;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
public class AprovacaoProfessorService {

    private final UsuarioRepository usuarios;
    private final AuditoriaService auditoria;

    public AprovacaoProfessorService(UsuarioRepository usuarios, AuditoriaService auditoria) {

        this.usuarios = usuarios;
        this.auditoria = auditoria;
    }

    @PreAuthorize("hasRole('DIRECAO')")
    @Transactional(readOnly = true)
    public Page<ProfessorResponse> listarPendentes(int pagina, int tamanho, UsuarioAtual solicitante) {

        validarDirecao(solicitante);

        if (pagina < 0 || tamanho < 1 || tamanho > 100) {

            throw new RegraNegocioException("Informe página a partir de zero " + "e tamanho entre 1 e 100.");
        }

        var paginacao = PageRequest.of(pagina, tamanho, Sort.by("nome").ascending().and(Sort.by("id").ascending()));

        return usuarios.findByPerfilAndSituacaoCadastro(Perfil.PROFESSOR, SituacaoCadastro.PENDENTE_APROVACAO, paginacao).map(ProfessorResponse::de);
    }

    @PreAuthorize("hasRole('DIRECAO')")
    @Transactional
    public ProfessorResponse aprovar(UUID professorId, Long versao, UsuarioAtual solicitante) {

        validarDirecao(solicitante);

        var professor = buscarPendente(professorId, versao);

        professor.setSituacaoCadastro(SituacaoCadastro.APROVADO);
        professor.setAtivo(true);

        usuarios.flush();

        auditoria.aposCommit("PROFESSOR_CADASTRO_APROVADO", solicitante.id(), professor.getId());

        return ProfessorResponse.de(professor);
    }

    @PreAuthorize("hasRole('DIRECAO')")
    @Transactional
    public ProfessorResponse rejeitar(UUID professorId, Long versao, UsuarioAtual solicitante) {

        validarDirecao(solicitante);

        var professor = buscarPendente(professorId, versao);

        professor.setSituacaoCadastro(SituacaoCadastro.REJEITADO);
        professor.setAtivo(false);

        usuarios.flush();

        auditoria.aposCommit("PROFESSOR_CADASTRO_REJEITADO", solicitante.id(), professor.getId());

        return ProfessorResponse.de(professor);
    }

    private Usuario buscarPendente(UUID professorId, Long versao) {

        var professor = usuarios.buscarPorIdComBloqueio(professorId)
                .filter(usuario -> usuario.getPerfil() == Perfil.PROFESSOR)
                .orElseThrow(() -> new RegraNegocioException("Professor não encontrado."));

        if (versao == null || !Objects.equals(professor.getVersao(), versao)) {
            throw new ConflitoVersaoException();
        }

        if (professor.getSituacaoCadastro() != SituacaoCadastro.PENDENTE_APROVACAO) {

            throw new RegraNegocioException("Somente cadastros pendentes " + "podem ser aprovados ou rejeitados.");
        }

        return professor;
    }

    private void validarDirecao(UsuarioAtual solicitante) {

        if (solicitante == null || solicitante.perfil() != Perfil.DIRECAO) {
            throw new AccessDeniedException("Acesso restrito à direção.");
        }

        var diretor = usuarios.findById(solicitante.id()).orElseThrow(() -> new AccessDeniedException("Acesso restrito à direção."));

        if (!diretor.isAtivo() || diretor.getPerfil() != Perfil.DIRECAO) {

            throw new AccessDeniedException("Acesso restrito à direção.");
        }
    }
}