package com.crecheconecta.repository;

import com.crecheconecta.entity.Sessao;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessaoRepository extends JpaRepository<Sessao, UUID> {

    Optional<Sessao> findByTokenHash(String tokenHash);

    default int revogarSubstituidas(UUID usuarioId, String hashAnterior, Instant agora) {
        var substituidas = buscarSubstituidasComBloqueio(usuarioId, hashAnterior, agora);
        for (Sessao sessao : substituidas) {
            sessao.setRevogadoEm(agora);
        }
        return substituidas.size();
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT s FROM Sessao s
        WHERE s.revogadoEm IS NULL AND s.expiraEm > :agora
          AND (s.usuario.id = :usuarioId OR s.renovacaoHash = :hashAnterior)
        ORDER BY s.id
        """)
    List<Sessao> buscarSubstituidasComBloqueio(
            @Param("usuarioId") UUID usuarioId,
            @Param("hashAnterior") String hashAnterior,
            @Param("agora") Instant agora
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Sessao s WHERE s.renovacaoHash = :hash")
    Optional<Sessao> buscarParaRenovacao(@Param("hash") String hash);

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

    @Modifying(flushAutomatically = true)
    @Query("""
        UPDATE Sessao s
        SET s.revogadoEm = :agora
        WHERE s.usuario.id = :usuarioId
          AND s.revogadoEm IS NULL
        """)
    int revogarTodasDoUsuario(
            @Param("usuarioId") UUID usuarioId,
            @Param("agora") Instant agora
    );
}