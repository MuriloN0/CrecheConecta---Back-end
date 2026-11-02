package com.crecheconecta.service;

import com.crecheconecta.dto.AlunoResponsavelResponse;
import com.crecheconecta.entity.Aluno;
import com.crecheconecta.entity.AlunoResponsavel;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.exception.AlunoNaoEncontradoException;
import com.crecheconecta.exception.RegraNegocioException;
import com.crecheconecta.repository.AlunoRepository;
import com.crecheconecta.repository.AlunoResponsavelRepository;
import com.crecheconecta.repository.UsuarioRepository;
import com.crecheconecta.security.UsuarioAtual;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
@PreAuthorize("hasRole('DIRECAO')")
public class AlunoResponsavelService {

    private final AlunoResponsavelRepository vinculos;
    private final AlunoRepository alunos;
    private final UsuarioRepository usuarios;
    private final AuditoriaService auditoria;
    private final Clock clock;

    public AlunoResponsavelService(AlunoResponsavelRepository vinculos, AlunoRepository alunos, UsuarioRepository usuarios, AuditoriaService auditoria, Clock clock) {

        this.vinculos = vinculos;
        this.alunos = alunos;
        this.usuarios = usuarios;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    @Transactional
    public AlunoResponsavelResponse vincular(UUID alunoId, UUID responsavelId, UsuarioAtual solicitante) {

        validarDirecao(solicitante);

        var aluno = buscarAlunoComBloqueio(alunoId);

        if (!aluno.isAtivo()) {

            throw new RegraNegocioException("Não é possível vincular um responsável " + "a um aluno inativo.");
        }

        usuarios.buscarPorIdComBloqueio(responsavelId).filter(usuario -> usuario.getPerfil() == Perfil.RESPONSAVEL).orElseThrow(() -> new RegraNegocioException("Responsável não encontrado."));

        if (vinculos.existsByAlunoIdAndEncerradoEmIsNull(alunoId)) {

            throw new RegraNegocioException("O aluno já possui um responsável vinculado. " + "Encerre o vínculo anterior antes de continuar.");
        }

        if (vinculos.existsByResponsavelIdAndEncerradoEmIsNull(responsavelId)) {

            throw new RegraNegocioException("O responsável já possui um aluno vinculado.");
        }

        var vinculo = new AlunoResponsavel();
        vinculo.setAlunoId(alunoId);
        vinculo.setResponsavelId(responsavelId);
        vinculo.setCriadoEm(clock.instant());

        vinculos.saveAndFlush(vinculo);

        auditoria.aposCommit("ALUNO_RESPONSAVEL_VINCULADO", solicitante.id(), vinculo.getId());

        return AlunoResponsavelResponse.de(vinculo);
    }

    @Transactional(readOnly = true)
    public List<AlunoResponsavelResponse> listar(UUID alunoId, UsuarioAtual solicitante) {

        validarDirecao(solicitante);

        if (!alunos.existsById(alunoId)) {

            throw new AlunoNaoEncontradoException();
        }

        return vinculos.findByAlunoIdAndEncerradoEmIsNullOrderByCriadoEmAsc(alunoId).stream().map(AlunoResponsavelResponse::de).toList();
    }

    @Transactional
    public AlunoResponsavelResponse encerrar(UUID alunoId, UUID vinculoId, UsuarioAtual solicitante) {

        validarDirecao(solicitante);

        buscarAlunoComBloqueio(alunoId);

        var vinculo = vinculos.findByIdAndAlunoId(vinculoId, alunoId).orElseThrow(() -> new RegraNegocioException("Vínculo não encontrado para este aluno."));

        usuarios.buscarPorIdComBloqueio(vinculo.getResponsavelId()).orElseThrow(() -> new RegraNegocioException("Responsável não encontrado."));

        if (vinculo.getEncerradoEm() != null) {

            throw new RegraNegocioException("Este vínculo já foi encerrado.");
        }

        vinculo.setEncerradoEm(clock.instant());

        vinculos.flush();

        auditoria.aposCommit("ALUNO_RESPONSAVEL_DESVINCULADO", solicitante.id(), vinculo.getId());

        return AlunoResponsavelResponse.de(vinculo);
    }

    private Aluno buscarAlunoComBloqueio(UUID alunoId) {

        return alunos.buscarPorIdComBloqueio(alunoId).orElseThrow(AlunoNaoEncontradoException::new);
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