package com.crecheconecta.service;

import com.crecheconecta.dto.DirecaoResponse;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.SituacaoCadastro;
import com.crecheconecta.entity.Usuario;
import com.crecheconecta.exception.ConflitoVersaoException;
import com.crecheconecta.exception.RegraNegocioException;
import com.crecheconecta.repository.SessaoRepository;
import com.crecheconecta.repository.UsuarioRepository;
import com.crecheconecta.security.UsuarioAtual;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Objects;

@Service
@PreAuthorize("hasRole('DIRECAO')")
public class DirecaoService {

    private final UsuarioRepository usuarios;
    private final SessaoRepository sessoes;
    private final AuditoriaService auditoria;
    private final Clock clock;

    public DirecaoService(UsuarioRepository usuarios, SessaoRepository sessoes, AuditoriaService auditoria, Clock clock) {

        this.usuarios = usuarios;
        this.sessoes = sessoes;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DirecaoResponse consultarMinhaConta(UsuarioAtual solicitante) {

        validarSolicitante(solicitante);

        var usuario = usuarios.findById(solicitante.id())
                .orElseThrow(this::acessoNegado);

        validarDirecaoAtiva(usuario);

        return DirecaoResponse.de(usuario);
    }

    @Transactional
    public DirecaoResponse inativarMinhaConta(Long versao, UsuarioAtual solicitante) {

        validarSolicitante(solicitante);

        var diretores = usuarios.buscarPorPerfilComBloqueio(Perfil.DIRECAO);

        var usuario = diretores.stream()
                .filter(diretor ->
                        diretor.getId().equals(solicitante.id()))
                .findFirst()
                .orElseThrow(this::acessoNegado);

        validarDirecaoAtiva(usuario);

        if (versao == null || !Objects.equals(usuario.getVersao(), versao)) {

            throw new ConflitoVersaoException();
        }

        long quantidadeAtiva = diretores.stream()
                .filter(Usuario::isAtivo)
                .filter(diretor ->
                        diretor.getSituacaoCadastro() == SituacaoCadastro.APROVADO)
                .count();

        if (quantidadeAtiva <= 1) {

            throw new RegraNegocioException("Não é possível inativar a última direção ativa. " + "Cadastre outra direção antes de continuar.");
        }

        usuario.setAtivo(false);
        usuario.setSituacaoCadastro(SituacaoCadastro.INATIVO);

        usuarios.flush();

        sessoes.revogarTodasDoUsuario(usuario.getId(), clock.instant());

        auditoria.aposCommit("DIRECAO_INATIVADA", solicitante.id(), usuario.getId());

        return DirecaoResponse.de(usuario);
    }

    private void validarSolicitante(UsuarioAtual solicitante) {

        if (solicitante == null || solicitante.id() == null || solicitante.perfil() != Perfil.DIRECAO) {
            throw acessoNegado();
        }
    }

    private void validarDirecaoAtiva(Usuario usuario) {

        if (usuario.getPerfil() != Perfil.DIRECAO || !usuario.isAtivo() || usuario.getSituacaoCadastro() != SituacaoCadastro.APROVADO) {
            throw acessoNegado();
        }
    }

    private AccessDeniedException acessoNegado() {

        return new AccessDeniedException("Acesso restrito à direção ativa e aprovada.");
    }
}