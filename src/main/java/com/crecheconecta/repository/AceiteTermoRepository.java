package com.crecheconecta.repository;

import com.crecheconecta.entity.AceiteTermo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AceiteTermoRepository extends JpaRepository<AceiteTermo, UUID> {

    Optional<AceiteTermo> findFirstByUsuarioIdOrderByDataAceiteDesc(UUID usuarioId);

    // Quantos usuários diferentes aceitaram cada versão (Big Number de LGPD da direção).
    @Query("SELECT a.versaoTermo AS identificador, COUNT(DISTINCT a.usuarioId) AS total FROM AceiteTermo a GROUP BY a.versaoTermo")
    List<AceitesPorVersao> contarPorVersao();

    interface AceitesPorVersao {

        String getIdentificador();

        Long getTotal();
    }
}
