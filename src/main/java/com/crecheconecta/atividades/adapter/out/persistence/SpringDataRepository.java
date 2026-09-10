package com.crecheconecta.atividades.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface SpringDataRepository extends JpaRepository<AtividadeEntity, UUID> {
}
