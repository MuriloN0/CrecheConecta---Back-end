package com.crecheconecta.atividades.adapter.out.persistence;

import com.crecheconecta.atividades.domain.model.TipoAtividade;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SpringDataRepository extends JpaRepository<AtividadeEntity, UUID> {
    List<AtividadeEntity> findByTurmaId(UUID turmaId);
    List<AtividadeEntity> findByTipo(TipoAtividade tipo);
    List<AtividadeEntity> findByTurmaIdAndTipo(UUID turmaId, TipoAtividade tipo);
}