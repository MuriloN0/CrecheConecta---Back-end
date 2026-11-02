package com.crecheconecta.service;

import com.crecheconecta.dto.AtualizarComunicadoRequest;
import com.crecheconecta.dto.ComunicadoResponse;
import com.crecheconecta.dto.CriarComunicadoRequest;
import com.crecheconecta.entity.Comunicado;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.SituacaoCadastro;
import com.crecheconecta.entity.Usuario;
import com.crecheconecta.exception.ComunicadoNaoEncontradoException;
import com.crecheconecta.exception.ConflitoVersaoException;
import com.crecheconecta.exception.RegraNegocioException;
import com.crecheconecta.repository.ComunicadoRepository;
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
import java.util.Objects;
import java.util.UUID;

@Service
@PreAuthorize("hasAnyRole('DIRECAO', 'PROFESSOR', 'RESPONSAVEL')")
public class ComunicadoService {

    private final ComunicadoRepository comunicados;
    private final UsuarioRepository usuarios;
    private final AuditoriaService auditoria;
    private final Clock clock;

    public ComunicadoService(ComunicadoRepository comunicados, UsuarioRepository usuarios, AuditoriaService auditoria, Clock clock) {

        this.comunicados = comunicados;
        this.usuarios = usuarios;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    @Transactional
    @PreAuthorize("hasRole('DIRECAO')")
    public ComunicadoResponse criar(CriarComunicadoRequest request, UsuarioAtual solicitante) {

        var diretor = validarUsuario(solicitante, true);

        validarConteudo(request.titulo(), request.conteudo());

        var agora = clock.instant();

        var comunicado = new Comunicado();
        comunicado.setTitulo(request.titulo().strip());
        comunicado.setConteudo(request.conteudo().strip());
        comunicado.setCriadoPor(diretor);
        comunicado.setCriadoEm(agora);
        comunicado.setAtualizadoEm(agora);
        comunicado.setAtivo(true);

        comunicados.saveAndFlush(comunicado);

        auditoria.aposCommit("COMUNICADO_CRIADO", diretor.getId(), comunicado.getId());

        return ComunicadoResponse.de(comunicado);
    }

    @Transactional(readOnly = true)
    public Page<ComunicadoResponse> listar(int pagina, int tamanho, UsuarioAtual solicitante) {

        validarUsuario(solicitante, false);

        if (pagina < 0 || tamanho < 1 || tamanho > 100) {

            throw new RegraNegocioException("Informe página a partir de zero " + "e tamanho entre 1 e 100.");
        }

        var ordenacao = Sort.by("criadoEm").descending().and(Sort.by("id").descending());

        var paginacao = PageRequest.of(pagina, tamanho, ordenacao);

        return comunicados.findByAtivoTrue(paginacao).map(ComunicadoResponse::de);
    }

    @Transactional(readOnly = true)
    public ComunicadoResponse visualizar(UUID comunicadoId, UsuarioAtual solicitante) {

        validarUsuario(solicitante, false);

        var comunicado = comunicados.findByIdAndAtivoTrue(comunicadoId).orElseThrow(ComunicadoNaoEncontradoException::new);

        return ComunicadoResponse.de(comunicado);
    }

    @Transactional
    @PreAuthorize("hasRole('DIRECAO')")
    public ComunicadoResponse atualizar(UUID comunicadoId, AtualizarComunicadoRequest request, UsuarioAtual solicitante) {

        validarUsuario(solicitante, true);

        var comunicado = buscarComVersao(comunicadoId, request.versao());

        validarConteudo(request.titulo(), request.conteudo());

        comunicado.setTitulo(request.titulo().strip());
        comunicado.setConteudo(request.conteudo().strip());
        comunicado.setAtualizadoEm(clock.instant());

        comunicados.flush();

        auditoria.aposCommit("COMUNICADO_ATUALIZADO", solicitante.id(), comunicado.getId());

        return ComunicadoResponse.de(comunicado);
    }

    @Transactional
    @PreAuthorize("hasRole('DIRECAO')")
    public ComunicadoResponse inativar(UUID comunicadoId, Long versao, UsuarioAtual solicitante) {

        validarUsuario(solicitante, true);

        var comunicado = buscarComVersao(comunicadoId, versao);

        comunicado.setAtivo(false);
        comunicado.setAtualizadoEm(clock.instant());

        comunicados.flush();

        auditoria.aposCommit("COMUNICADO_INATIVADO", solicitante.id(), comunicado.getId());

        return ComunicadoResponse.de(comunicado);
    }

    private Comunicado buscarComVersao(UUID id, Long versao) {

        var comunicado = comunicados.buscarAtivoComBloqueio(id).orElseThrow(ComunicadoNaoEncontradoException::new);

        if (versao == null || !Objects.equals(comunicado.getVersao(), versao)) {

            throw new ConflitoVersaoException();
        }

        return comunicado;
    }

    private Usuario validarUsuario(UsuarioAtual solicitante, boolean exigeDirecao) {

        if (solicitante == null || solicitante.id() == null || solicitante.perfil() == null) {

            throw acessoNegado();
        }

        var usuario = usuarios.findById(solicitante.id()).orElseThrow(this::acessoNegado);

        if (!usuario.isAtivo() || usuario.getSituacaoCadastro() != SituacaoCadastro.APROVADO || usuario.getPerfil() != solicitante.perfil()) {

            throw acessoNegado();
        }

        boolean perfilPermitido = usuario.getPerfil() == Perfil.DIRECAO || usuario.getPerfil() == Perfil.PROFESSOR || usuario.getPerfil() == Perfil.RESPONSAVEL;

        if (!perfilPermitido || (exigeDirecao && usuario.getPerfil() != Perfil.DIRECAO)) {

            throw acessoNegado();
        }

        return usuario;
    }

    private void validarConteudo(String titulo, String conteudo) {

        if (titulo == null || titulo.isBlank() || titulo.length() > 150) {
            throw new RegraNegocioException("Informe um título com até 150 caracteres.");
        }

        if (conteudo == null || conteudo.isBlank() || conteudo.length() > 5000) {

            throw new RegraNegocioException("Informe um conteúdo com até 5000 caracteres.");
        }
    }

    private AccessDeniedException acessoNegado() {

        return new AccessDeniedException("Você não possui permissão para esta operação.");
    }
}