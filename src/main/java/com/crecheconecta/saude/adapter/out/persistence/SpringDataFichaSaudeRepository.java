package com.crecheconecta.saude.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SpringDataFichaSaudeRepository
        extends JpaRepository<FichaSaudeJpaEntity, UUID> {

    List<FichaSaudeJpaEntity> findByAlunoIdOrderByDataCriacaoDesc(UUID alunoId);
}
