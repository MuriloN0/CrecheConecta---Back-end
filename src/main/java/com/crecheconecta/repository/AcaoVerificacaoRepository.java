package com.crecheconecta.repository;

import com.crecheconecta.entity.AcaoVerificacao;
import com.crecheconecta.entity.FinalidadeAcao;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


public interface AcaoVerificacaoRepository extends JpaRepository<AcaoVerificacao, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT a
        FROM AcaoVerificacao a
        WHERE a.id = :id
        """)
    Optional<AcaoVerificacao> buscarPorIdComBloqueio(
            @Param("id") UUID id
    );

    @Query("""
        SELECT a
        FROM AcaoVerificacao a
        WHERE a.usuario.id = :usuarioId
          AND a.finalidade = :finalidade
          AND a.consumidoEm IS NULL
          AND a.invalidadoEm IS NULL
        """)
    List<AcaoVerificacao> buscarPendentes(
            @Param("usuarioId") UUID usuarioId,
            @Param("finalidade") FinalidadeAcao finalidade
    );

    Optional<AcaoVerificacao> findFirstByUsuario_IdAndFinalidadeOrderByCriadoEmDesc(
            UUID usuarioId,
            FinalidadeAcao finalidade
    );
}
