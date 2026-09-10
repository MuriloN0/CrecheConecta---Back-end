package com.crecheconecta.atividades.adapter.out.persistence;

import com.crecheconecta.atividades.application.port.out.AtividadeRepository;
import com.crecheconecta.atividades.domain.model.Atividade;
import org.springframework.stereotype.Repository;

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
}