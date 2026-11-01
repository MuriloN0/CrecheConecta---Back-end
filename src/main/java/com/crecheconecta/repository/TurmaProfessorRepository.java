package com.crecheconecta.repository;

import com.crecheconecta.entity.TurmaProfessor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface TurmaProfessorRepository extends JpaRepository<TurmaProfessor, UUID> {

    boolean existsByTurmaIdAndProfessorIdAndEncerradoEmIsNull(UUID turmaId, UUID professorId);

    Optional<TurmaProfessor> findByIdAndTurmaId(UUID id, UUID turmaId);

    Page<TurmaProfessor> findByTurmaId(UUID turmaId, Pageable pageable);

    Page<TurmaProfessor> findByTurmaIdAndEncerradoEmIsNull(UUID turmaId, Pageable pageable);

    @Query("""
            SELECT v.professorId
            FROM TurmaProfessor v
            WHERE v.id = :id
              AND v.turmaId = :turmaId
            """)
    Optional<UUID> buscarProfessorId(@Param("id") UUID id, @Param("turmaId") UUID turmaId);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE TurmaProfessor v
            SET v.encerradoEm = :agora
            WHERE v.turmaId = :turmaId
              AND v.encerradoEm IS NULL
            """)
    int encerrarDaTurma(@Param("turmaId") UUID turmaId, @Param("agora") Instant agora);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE TurmaProfessor v
            SET v.encerradoEm = :agora
            WHERE v.professorId = :professorId
              AND v.encerradoEm IS NULL
            """)
    int encerrarDoProfessor(@Param("professorId") UUID professorId, @Param("agora") Instant agora);
}