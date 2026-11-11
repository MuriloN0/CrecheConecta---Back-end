package com.crecheconecta.repository;

import com.crecheconecta.entity.PrioridadeSolicitacaoMaterial;
import com.crecheconecta.entity.SolicitacaoMaterial;
import com.crecheconecta.entity.StatusSolicitacaoMaterial;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SolicitacaoMaterialRepository extends JpaRepository<SolicitacaoMaterial, UUID> {

    @Query("""
            SELECT s
            FROM SolicitacaoMaterial s
            WHERE (:status IS NULL OR s.status = :status)
              AND (:prioridade IS NULL OR s.prioridade = :prioridade)
            """)
    Page<SolicitacaoMaterial> listarTodas(
            @Param("status") StatusSolicitacaoMaterial status,
            @Param("prioridade") PrioridadeSolicitacaoMaterial prioridade,
            Pageable paginacao
    );

    @Query("""
            SELECT s
            FROM SolicitacaoMaterial s
            WHERE s.professorId = :professorId
              AND (:status IS NULL OR s.status = :status)
              AND (:prioridade IS NULL OR s.prioridade = :prioridade)
            """)
    Page<SolicitacaoMaterial> listarDoProfessor(
            @Param("professorId") UUID professorId,
            @Param("status") StatusSolicitacaoMaterial status,
            @Param("prioridade") PrioridadeSolicitacaoMaterial prioridade,
            Pageable paginacao
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT s
            FROM SolicitacaoMaterial s
            WHERE s.id = :id
            """)
    Optional<SolicitacaoMaterial> buscarComBloqueio(@Param("id") UUID id);
}
