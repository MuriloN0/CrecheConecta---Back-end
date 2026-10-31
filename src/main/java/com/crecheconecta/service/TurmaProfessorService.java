package com.crecheconecta.service;

import com.crecheconecta.dto.TurmaProfessorResponse;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.SituacaoCadastro;
import com.crecheconecta.entity.TurmaProfessor;
import com.crecheconecta.exception.ProfessorNaoEncontradoException;
import com.crecheconecta.exception.RegraNegocioException;
import com.crecheconecta.exception.TurmaNaoEncontradaException;
import com.crecheconecta.exception.VinculoProfessorNaoEncontradoException;
import com.crecheconecta.repository.TurmaProfessorRepository;
import com.crecheconecta.repository.TurmaRepository;
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
import java.util.UUID;

@Service
@PreAuthorize("hasRole('DIRECAO')")
public class TurmaProfessorService {

    private final TurmaProfessorRepository vinculos;
    private final TurmaRepository turmas;
    private final UsuarioRepository usuarios;
    private final AuditoriaService auditoria;
    private final Clock clock;

    public TurmaProfessorService(TurmaProfessorRepository vinculos, TurmaRepository turmas, UsuarioRepository usuarios, AuditoriaService auditoria, Clock clock) {

        this.vinculos = vinculos;
        this.turmas = turmas;
        this.usuarios = usuarios;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    @Transactional
    public TurmaProfessorResponse vincular(UUID turmaId, UUID professorId, UsuarioAtual solicitante) {

        validarDirecao(solicitante);

        var professor = usuarios.buscarPorIdComBloqueio(professorId).filter(usuario -> usuario.getPerfil() == Perfil.PROFESSOR).orElseThrow(ProfessorNaoEncontradoException::new);

        var turma = turmas.buscarPorIdComBloqueio(turmaId).orElseThrow(TurmaNaoEncontradaException::new);

        if (!professor.isAtivo() || professor.getSituacaoCadastro() != SituacaoCadastro.APROVADO) {

            throw new RegraNegocioException("Somente professores aprovados e ativos " + "podem ser vinculados.");
        }

        if (!turma.isAtivo()) {

            throw new RegraNegocioException("Não é possível vincular professores " + "a uma turma inativa.");
        }

        if (vinculos.existsByTurmaIdAndProfessorIdAndEncerradoEmIsNull(turmaId, professorId)) {

            throw new RegraNegocioException("O professor já está vinculado a esta turma.");
        }

        var vinculo = new TurmaProfessor();
        vinculo.setTurmaId(turmaId);
        vinculo.setProfessorId(professorId);
        vinculo.setCriadoEm(clock.instant());

        vinculos.saveAndFlush(vinculo);

        auditoria.aposCommit("TURMA_PROFESSOR_VINCULADO", solicitante.id(), vinculo.getId());

        return TurmaProfessorResponse.de(vinculo);
    }

    @Transactional(readOnly = true)
    public Page<TurmaProfessorResponse> listar(UUID turmaId, int pagina, int tamanho, boolean incluirEncerrados, UsuarioAtual solicitante) {

        validarDirecao(solicitante);

        if (pagina < 0 || tamanho < 1 || tamanho > 100) {

            throw new RegraNegocioException("Informe página a partir de zero " + "e tamanho entre 1 e 100.");
        }

        if (!turmas.existsById(turmaId)) {

            throw new TurmaNaoEncontradaException();
        }

        var paginacao = PageRequest.of(pagina, tamanho, Sort.by("criadoEm").ascending().and(Sort.by("id").ascending()));

        Page<TurmaProfessor> resultado;

        if (incluirEncerrados) {

            resultado = vinculos.findByTurmaId(turmaId, paginacao);
        } else {

            resultado = vinculos.findByTurmaIdAndEncerradoEmIsNull(turmaId, paginacao);
        }

        return resultado.map(TurmaProfessorResponse::de);
    }

    @Transactional
    public TurmaProfessorResponse encerrar(UUID turmaId, UUID vinculoId, UsuarioAtual solicitante) {

        validarDirecao(solicitante);

        UUID professorId = vinculos.buscarProfessorId(vinculoId, turmaId).orElseThrow(VinculoProfessorNaoEncontradoException::new);

        usuarios.buscarPorIdComBloqueio(professorId).orElseThrow(ProfessorNaoEncontradoException::new);

        turmas.buscarPorIdComBloqueio(turmaId).orElseThrow(TurmaNaoEncontradaException::new);

        var vinculo = vinculos.findByIdAndTurmaId(vinculoId, turmaId).orElseThrow(VinculoProfessorNaoEncontradoException::new);

        if (vinculo.getEncerradoEm() != null) {

            throw new RegraNegocioException("Este vínculo já foi encerrado.");
        }

        vinculo.setEncerradoEm(clock.instant());

        vinculos.flush();

        auditoria.aposCommit("TURMA_PROFESSOR_DESVINCULADO", solicitante.id(), vinculo.getId());

        return TurmaProfessorResponse.de(vinculo);
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