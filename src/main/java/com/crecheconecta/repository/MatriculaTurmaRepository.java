package com.crecheconecta.repository;

import com.crecheconecta.entity.MatriculaTurma;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface MatriculaTurmaRepository extends JpaRepository<MatriculaTurma, UUID> {

    boolean existsByAlunoIdAndEncerradoEmIsNull(UUID alunoId);

    Optional<MatriculaTurma> findByIdAndTurmaId(UUID id, UUID turmaI);

    Page<MatriculaTurma> findByTurmaId(UUID turmaId, Pageable pageable);

    Page<MatriculaTurma> findByTurmaIdAndEncerradoEmIsNull(UUID turmaId, Pageable pageable);

    @Query("""
            SELECT m.alunoId
            FROM MatriculaTurma m
            WHERE m.id = :id
              AND m.turmaId = :turmaId
            """)
    Optional<UUID> buscarAlunoId(
            @Param("id") UUID id,
            @Param("turmaId") UUID turmaId
    );

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE MatriculaTurma m
            SET m.encerradoEm = :agora
            WHERE m.turmaId = :turmaId
              AND m.encerradoEm IS NULL
            """)
    int encerrarDaTurma(
            @Param("turmaId") UUID turmaId,
            @Param("agora") Instant agora
    );

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE MatriculaTurma m
            SET m.encerradoEm = :agora
            WHERE m.alunoId = :alunoId
              AND m.encerradoEm IS NULL
            """)
    int encerrarDoAluno(
            @Param("alunoId") UUID alunoId,
            @Param("agora") Instant agora
    );
}