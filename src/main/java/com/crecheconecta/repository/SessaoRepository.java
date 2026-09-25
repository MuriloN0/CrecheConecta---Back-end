package com.crecheconecta.repository;

import com.crecheconecta.entity.Sessao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SessaoRepository extends JpaRepository<Sessao, UUID> {

    Optional<Sessao> findByTokenHash(String tokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE Sessao s
        SET s.revogadoEm = :agora
        WHERE s.id = :sessaoId
          AND s.usuario.id = :usuarioId
          AND s.revogadoEm IS NULL
        """)
    int revogar(
            @Param("sessaoId") UUID sessaoId,
            @Param("usuarioId") UUID usuarioId,
            @Param("agora") Instant agora
    );

}

