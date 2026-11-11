package com.crecheconecta.repository;

import com.crecheconecta.entity.MalaVirtual;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MalaVirtualRepository extends JpaRepository<MalaVirtual, UUID> {

    List<MalaVirtual> findByTurmaIdOrderByDataDescCriadoEmDesc(UUID turmaId);

    List<MalaVirtual> findTop20ByTurmaIdOrderByDataDescCriadoEmDesc(UUID turmaId);

    Optional<MalaVirtual> findByIdAndTurmaId(UUID id, UUID turmaId);

    boolean existsByIdAndTurmaId(UUID id, UUID turmaId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT m
            FROM MalaVirtual m
            WHERE m.id = :id
              AND m.turmaId = :turmaId
            """)
    Optional<MalaVirtual> buscarDaTurmaComBloqueio(@Param("id") UUID id, @Param("turmaId") UUID turmaId);
}
