package com.crecheconecta.repository;

import com.crecheconecta.entity.DiarioRotina;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface DiarioRotinaRepository extends JpaRepository<DiarioRotina, UUID> {

    // Diário de um dia específico (no máximo 1, garantido por uk_diarios_aluno_data).
    Optional<DiarioRotina> findByAlunoIdAndData(UUID alunoId, LocalDate data);

    // Diário mais recente do aluno.
    Optional<DiarioRotina> findFirstByAlunoIdOrderByDataDesc(UUID alunoId);
}
