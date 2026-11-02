package com.crecheconecta.repository;

import com.crecheconecta.entity.ItemMala;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ItemMalaRepository extends JpaRepository<ItemMala, UUID> {

    // Itens de várias malas numa consulta só (evita 1 consulta por mala).
    List<ItemMala> findByMalaIdIn(Collection<UUID> malaIds);
}
