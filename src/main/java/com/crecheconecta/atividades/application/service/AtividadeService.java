package com.crecheconecta.atividades.application.service;

import com.crecheconecta.atividades.application.port.in.AtividadeUseCase;
import com.crecheconecta.atividades.application.port.out.AtividadeRepository;
import com.crecheconecta.atividades.domain.model.Atividade;
import com.crecheconecta.atividades.domain.model.TipoAtividade;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AtividadeService implements AtividadeUseCase {

    private final AtividadeRepository repository;

    public AtividadeService(AtividadeRepository repository) {
        this.repository = repository;
    }

    @Override
    public UUID criar(ComandoCriar comando) {
        UUID novoId = UUID.randomUUID();
        Atividade atividade = new Atividade(
                novoId,
                comando.turmaId(),
                comando.professorId(),
                comando.tipo(),
                comando.titulo(),
                comando.descricao(),
                Instant.now(),
                comando.prazoConclusao()
        );
        repository.salvar(atividade);
        return novoId;
    }

    @Override
    public void atualizar(UUID id, ComandoAtualizar comando) {
        Atividade atividade = repository.buscarPorId(id)
                .orElseThrow(() -> new IllegalArgumentException("Atividade não encontrada."));

        Atividade atividadeAtualizada = atividade.atualizar(
                comando.tipo(),
                comando.titulo(),
                comando.descricao(),
                comando.prazoConclusao()
        );

        repository.salvar(atividadeAtualizada);
    }

    @Override
    public void deletar(UUID id) {
        repository.deletarPorId(id);
    }

    @Override
    public Atividade buscarPorId(UUID id) {
        return repository.buscarPorId(id)
                .orElseThrow(() -> new IllegalArgumentException("Atividade não encontrada."));
    }

    @Override
    public List<Atividade> listar(UUID turmaId, TipoAtividade tipo) {
        return repository.buscarFiltrado(turmaId, tipo);
    }
}