package com.crecheconecta.atividades.adapter.out.persistence;

import com.crecheconecta.atividades.application.port.out.AtividadeRepository;
import com.crecheconecta.atividades.domain.model.Atividade;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AtividadePersistenceAdapter implements AtividadeRepository {

    private final SpringDataRepository jpaRepository;

    public AtividadePersistenceAdapter(SpringDataRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void salvar(Atividade atividade) {
        AtividadeEntity entity = new AtividadeEntity(atividade);
        jpaRepository.save(entity);
    }

    @Override
    public Optional<Atividade> buscarPorId(UUID id) {
        return jpaRepository.findById(id)
                .map(AtividadeEntity::toDomain);
    }

    @Override
    public void deletarPorId(UUID id) {
        if (!jpaRepository.existsById(id)) {
            throw new IllegalArgumentException("Atividade não encontrada para deleção.");
        }
        jpaRepository.deleteById(id);
    }

    @Override
    public List<Atividade> buscarTodas() {
        return jpaRepository.findAll().stream()
                .map(AtividadeEntity::toDomain)
                .toList();
    }

    @Override
    public List<Atividade> buscarPorTurma(UUID turmaId) {
        return jpaRepository.findByTurmaId(turmaId).stream()
                .map(AtividadeEntity::toDomain)
                .toList();
    }
}