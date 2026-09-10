package com.crecheconecta.escolar.adapter.out.persistence;

import com.crecheconecta.escolar.domain.model.Aluno;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SpringDataAlunoRepository  extends JpaRepository<AlunoJpaEntity, UUID> {
}
