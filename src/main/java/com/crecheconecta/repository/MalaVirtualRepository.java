package com.crecheconecta.repository;

import com.crecheconecta.entity.MalaVirtual;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MalaVirtualRepository extends JpaRepository<MalaVirtual, UUID> {

    List<MalaVirtual> findByTurmaIdOrderByDataDescCriadoEmDesc(UUID turmaId);

    Page<MalaVirtual> findByTurmaId(UUID turmaId, Pageable pageable);

    List<MalaVirtual> findTop20ByTurmaIdOrderByDataDescCriadoEmDesc(UUID turmaId);

    @Query(
            value = """
                    SELECT m FROM MalaVirtual m
                    WHERE m.turmaId = :turmaId
                    ORDER BY CASE WHEN m.data = :hoje THEN 0 WHEN m.data > :hoje THEN 1 ELSE 2 END,
                             CASE WHEN m.data > :hoje THEN m.data ELSE NULL END ASC,
                             m.data DESC, m.criadoEm DESC, m.id DESC
                    """,
            countQuery = "SELECT COUNT(m) FROM MalaVirtual m WHERE m.turmaId = :turmaId"
    )
    Page<MalaVirtual> buscarParaFamilia(
            @Param("turmaId") UUID turmaId,
            @Param("hoje") LocalDate hoje,
            Pageable pageable
    );

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