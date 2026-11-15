package com.crecheconecta.repository;

import com.crecheconecta.entity.DiarioRotina;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DiarioRotinaRepository extends JpaRepository<DiarioRotina, UUID> {

    Optional<DiarioRotina> findByAlunoIdAndData(UUID alunoId, LocalDate data);

    List<DiarioRotina> findByTurmaIdAndData(UUID turmaId, LocalDate data);

    List<DiarioRotina> findByTurmaIdAndDataAndAlunoIdIn(
            UUID turmaId, LocalDate data, List<UUID> alunos
    );

    Optional<DiarioRotina> findFirstByAlunoIdOrderByDataDesc(UUID alunoId);

    List<DiarioRotina> findByAlunoIdOrderByDataDesc(UUID alunoId);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE DiarioRotina d SET d.observacaoProfessor = NULL WHERE d.alunoId = :alunoId")
    int apagarObservacoesDoAluno(@Param("alunoId") UUID alunoId);
}