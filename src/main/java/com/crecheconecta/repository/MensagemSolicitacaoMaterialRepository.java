package com.crecheconecta.repository;

import com.crecheconecta.entity.MensagemSolicitacaoMaterial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MensagemSolicitacaoMaterialRepository extends JpaRepository<MensagemSolicitacaoMaterial, UUID> {

    List<MensagemSolicitacaoMaterial> findBySolicitacaoIdOrderByCriadoEmAscIdAsc(UUID solicitacaoId);
}
