package com.crecheconecta.repository;

import com.crecheconecta.entity.Turma;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TurmaRepository extends JpaRepository<Turma, UUID> {

    Page<Turma> findByAtivo(boolean ativo, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT t
            FROM Turma t
            WHERE t.id = :id
            """)
    Optional<Turma> buscarPorIdComBloqueio(@Param("id") UUID id);
}