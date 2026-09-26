package com.crecheconecta.repository;

import com.crecheconecta.entity.AceiteTermo;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AceiteTermoRepository extends JpaRepository<AceiteTermo, UUID> {
    Optional<AceiteTermo> findFirstByUsuarioIdOrderByDataAceiteDesc(UUID usuarioId);
}