package com.crecheconecta.repository;

import com.crecheconecta.entity.AtividadeEntity;
import com.crecheconecta.model.TipoAtividade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AtividadeRepository extends JpaRepository<AtividadeEntity, UUID> {
    List<AtividadeEntity> findByTurmaId(UUID turmaId);
    List<AtividadeEntity> findByTipo(TipoAtividade tipo);
    List<AtividadeEntity> findByTurmaIdAndTipo(UUID turmaId, TipoAtividade tipo);
}