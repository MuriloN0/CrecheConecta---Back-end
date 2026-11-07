package com.crecheconecta.repository;

import com.crecheconecta.entity.ItemSolicitacaoMaterial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ItemSolicitacaoMaterialRepository extends JpaRepository<ItemSolicitacaoMaterial, UUID> {

    List<ItemSolicitacaoMaterial> findBySolicitacaoId(UUID solicitacaoId);

    List<ItemSolicitacaoMaterial> findBySolicitacaoIdIn(Collection<UUID> solicitacaoIds);
}
