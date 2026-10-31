package com.crecheconecta.repository;

import com.crecheconecta.entity.ConviteCadastro;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ConviteCadastroRepository extends JpaRepository<ConviteCadastro, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query(
            """
            SELECT c
            FROM ConviteCadastro c
            WHERE c.tokenHash = :tokenHash
            """)
    Optional<ConviteCadastro> buscarPorTokenHashComBloqueio(@Param("tokenHash") String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query(
            """
            SELECT c
            FROM ConviteCadastro c
            WHERE c.email = :email
              AND c.consumidoEm IS NULL
              AND c.canceladoEm IS NULL
            """)
    Optional<ConviteCadastro> buscarPendentePorEmailComBloqueio(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT c
        FROM ConviteCadastro c
        WHERE c.id = :id
        """)
    Optional<ConviteCadastro> buscarPorIdComBloqueio(@Param("id") UUID id);
}