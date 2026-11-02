package com.crecheconecta.repository;

import com.crecheconecta.entity.Comunicado;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ComunicadoRepository extends JpaRepository<Comunicado, UUID> {

    Page<Comunicado> findByAtivoTrue(Pageable pageable);

    Optional<Comunicado> findByIdAndAtivoTrue(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT c
        FROM Comunicado c
        WHERE c.id = :id
          AND c.ativo = TRUE
        """)
    Optional<Comunicado> buscarAtivoComBloqueio(@Param("id") UUID id);
}