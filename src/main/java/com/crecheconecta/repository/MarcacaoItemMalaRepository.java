package com.crecheconecta.repository;

import com.crecheconecta.entity.MarcacaoItemMala;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MarcacaoItemMalaRepository extends JpaRepository<MarcacaoItemMala, UUID> {

    List<MarcacaoItemMala> findByItemIdIn(Collection<UUID> itemIds);

    List<MarcacaoItemMala> findByAlunoIdAndItemIdIn(UUID alunoId, Collection<UUID> itemIds);

    Optional<MarcacaoItemMala> findByItemIdAndAlunoId(UUID itemId, UUID alunoId);
}
