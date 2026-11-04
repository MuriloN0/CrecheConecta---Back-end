package com.crecheconecta.service;

import com.crecheconecta.dto.AtividadeTurmaResponse;
import com.crecheconecta.dto.AtualizarAtividadeRequestDTO;
import com.crecheconecta.dto.NovaAtividadeRequestDTO;
import com.crecheconecta.entity.AtividadeEntity;
import com.crecheconecta.exception.AtividadeNaoEncontradaException;
import com.crecheconecta.exception.RegraNegocioException;
import com.crecheconecta.model.TipoAtividade;
import com.crecheconecta.repository.AtividadeRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
@PreAuthorize("hasAnyRole('DIRECAO', 'PROFESSOR', 'RESPONSAVEL')")
public class AtividadeService {

    private final AtividadeRepository atividades;
    private final AtividadeTituloValidation tituloValidation;
    private final AtividadePrazoValidation prazoValidation;
    private final AtividadeAcessoService acesso;
    private final AuditoriaService auditoria;
    private final Clock clock;

    public AtividadeService(AtividadeRepository atividades, AtividadeTituloValidation tituloValidation, AtividadePrazoValidation prazoValidation, AtividadeAcessoService acesso, AuditoriaService auditoria, Clock clock) {

        this.atividades = atividades;
        this.tituloValidation = tituloValidation;
        this.prazoValidation = prazoValidation;
        this.acesso = acesso;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AtividadeTurmaResponse> listarTurmas() {

        var usuario = acesso.usuarioAtual();

        return acesso.turmasPermitidas(usuario).stream().map(AtividadeTurmaResponse::de).toList();
    }

    @Transactional
    @PreAuthorize("hasRole('PROFESSOR')")
    public UUID criar(UUID turmaId, NovaAtividadeRequestDTO request) {

        var usuario = acesso.usuarioAtual();

        acesso.exigirProfessor(usuario);
        acesso.validarTurma(turmaId, usuario);

        acesso.exigirAutor(request.professorId(), usuario);

        if (request.tipo() == null) {

            throw new RegraNegocioException("O tipo da atividade é obrigatório.");
        }

        tituloValidation.validar(request.titulo());
        prazoValidation.validar(request.tipo(), request.prazoConclusao());

        var atividade = new AtividadeEntity();
        atividade.setId(UUID.randomUUID());
        atividade.setTurmaId(turmaId);
        atividade.setProfessorId(usuario.id());
        atividade.setTipo(request.tipo());
        atividade.setTitulo(request.titulo().trim());
        atividade.setDescricao(request.descricao());
        atividade.setDataCriacao(clock.instant());
        atividade.setPrazoConclusao(request.tipo() == TipoAtividade.CASA ? request.prazoConclusao() : null);

        atividades.save(atividade);

        auditoria.aposCommit("ATIVIDADE_CADASTRADA", usuario.id(), atividade.getId());

        return atividade.getId();
    }

    @Transactional
    @PreAuthorize("hasRole('PROFESSOR')")
    public void atualizar(UUID id, AtualizarAtividadeRequestDTO request) {

        var usuario = acesso.usuarioAtual();
        acesso.exigirProfessor(usuario);

        var atividade = buscarAtividadePorId(id);

        acesso.validarTurma(atividade.getTurmaId(), usuario);
        acesso.exigirAutor(atividade.getProfessorId(), usuario);

        var tipo = request.tipo() != null ? request.tipo() : atividade.getTipo();

        var titulo = request.titulo() != null ? request.titulo() : atividade.getTitulo();

        var prazo = request.prazoConclusao() != null ? request.prazoConclusao() : atividade.getPrazoConclusao();

        if (tipo == TipoAtividade.DIA) {

            prazo = null;
        }

        tituloValidation.validar(titulo);
        prazoValidation.validar(tipo, prazo);

        atividade.setTipo(tipo);
        atividade.setTitulo(titulo.trim());
        atividade.setPrazoConclusao(prazo);

        if (request.descricao() != null) {

            atividade.setDescricao(request.descricao());
        }

        atividades.save(atividade);

        auditoria.aposCommit("ATIVIDADE_ATUALIZADA", usuario.id(), atividade.getId());
    }

    @Transactional
    @PreAuthorize("hasRole('PROFESSOR')")
    public void deletar(UUID id) {

        var usuario = acesso.usuarioAtual();
        acesso.exigirProfessor(usuario);

        var atividade = buscarAtividadePorId(id);

        acesso.validarTurma(atividade.getTurmaId(), usuario);
        acesso.exigirAutor(atividade.getProfessorId(), usuario);

        atividades.delete(atividade);

        auditoria.aposCommit("ATIVIDADE_EXCLUIDA", usuario.id(), atividade.getId());
    }

    @Transactional(readOnly = true)
    public AtividadeEntity buscarPorId(UUID id) {

        var usuario = acesso.usuarioAtual();
        var atividade = buscarAtividadePorId(id);

        acesso.validarTurma(atividade.getTurmaId(), usuario);

        return atividade;
    }

    @Transactional(readOnly = true)
    public List<AtividadeEntity> listar(UUID turmaId, TipoAtividade tipo) {

        var usuario = acesso.usuarioAtual();

        List<UUID> turmaIds;

        if (turmaId != null) {

            acesso.validarTurma(turmaId, usuario);
            turmaIds = List.of(turmaId);
        } else {

            turmaIds = acesso.turmasPermitidas(usuario).stream()
                    .map(turma -> turma.getId()).toList();
        }

        if (turmaIds.isEmpty()) {

            return List.of();
        }

        if (tipo != null) {

            return atividades.findByTurmaIdInAndTipoOrderByDataCriacaoDescIdAsc(turmaIds, tipo);
        }

        return atividades.findByTurmaIdInOrderByDataCriacaoDescIdAsc(turmaIds);
    }

    private AtividadeEntity buscarAtividadePorId(UUID id) {

        return atividades.findById(id).orElseThrow(AtividadeNaoEncontradaException::new);
    }
}