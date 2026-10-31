package com.crecheconecta.repository;

import com.crecheconecta.entity.Aluno;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AlunoRepository extends JpaRepository<Aluno, UUID> {

    Page<Aluno> findByAtivo(boolean ativo, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT a
            FROM Aluno a
            WHERE a.id = :id
            """)
    Optional<Aluno> buscarPorIdComBloqueio(@Param("id") UUID id);
}