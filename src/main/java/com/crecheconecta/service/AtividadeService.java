package com.crecheconecta.service;

import com.crecheconecta.dto.AtualizarAtividadeRequestDTO;
import com.crecheconecta.dto.NovaAtividadeRequestDTO;
import com.crecheconecta.entity.AtividadeEntity;
import com.crecheconecta.exception.AtividadeNaoEncontradaException;
import com.crecheconecta.model.TipoAtividade;
import com.crecheconecta.repository.AtividadeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class AtividadeService {

    private final AtividadeRepository atividadeRepository;
    private final AtividadeTituloValidation tituloValidation;
    private final AtividadePrazoValidation prazoValidation;

    public AtividadeService(
            AtividadeRepository atividadeRepository,
            AtividadeTituloValidation tituloValidation,
            AtividadePrazoValidation prazoValidation
    ) {
        this.atividadeRepository = atividadeRepository;
        this.tituloValidation = tituloValidation;
        this.prazoValidation = prazoValidation;
    }

    @Transactional
    public UUID criar(UUID turmaId, NovaAtividadeRequestDTO request) {
        tituloValidation.validar(request.titulo());
        prazoValidation.validar(request.tipo(), request.prazoConclusao());

        AtividadeEntity atividade = new AtividadeEntity();
        atividade.setId(UUID.randomUUID());
        atividade.setTurmaId(turmaId);
        atividade.setProfessorId(request.professorId());
        atividade.setTipo(request.tipo());
        atividade.setTitulo(request.titulo().trim());
        atividade.setDescricao(request.descricao());
        atividade.setDataCriacao(Instant.now());
        atividade.setPrazoConclusao(request.prazoConclusao());

        atividadeRepository.save(atividade);

        return atividade.getId();
    }

    @Transactional
    public void atualizar(UUID id, AtualizarAtividadeRequestDTO request) {
        AtividadeEntity atividade = buscarAtividadePorId(id);

        TipoAtividade tipo = request.tipo() != null ? request.tipo() : atividade.getTipo();
        String titulo = request.titulo() != null ? request.titulo() : atividade.getTitulo();
        LocalDate prazo = request.prazoConclusao() != null ? request.prazoConclusao() : atividade.getPrazoConclusao();

        tituloValidation.validar(titulo);
        prazoValidation.validar(tipo, prazo);

        atividade.setTipo(tipo);
        atividade.setTitulo(titulo.trim());
        if (request.descricao() != null) {
            atividade.setDescricao(request.descricao());
        }
        atividade.setPrazoConclusao(prazo);

        atividadeRepository.save(atividade);
    }

    @Transactional
    public void deletar(UUID id) {
        if (!atividadeRepository.existsById(id)) {
            throw new AtividadeNaoEncontradaException();
        }
        atividadeRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public AtividadeEntity buscarPorId(UUID id) {
        return buscarAtividadePorId(id);
    }

    @Transactional(readOnly = true)
    public List<AtividadeEntity> listar(UUID turmaId, TipoAtividade tipo) {
        if (turmaId != null && tipo != null) {
            return atividadeRepository.findByTurmaIdAndTipo(turmaId, tipo);
        }
        if (turmaId != null) {
            return atividadeRepository.findByTurmaId(turmaId);
        }
        if (tipo != null) {
            return atividadeRepository.findByTipo(tipo);
        }
        return atividadeRepository.findAll();
    }

    private AtividadeEntity buscarAtividadePorId(UUID id) {
        return atividadeRepository.findById(id)
                .orElseThrow(AtividadeNaoEncontradaException::new);
    }
}