package com.crecheconecta.repository;

import com.crecheconecta.entity.MarcacaoItemMala;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MarcacaoItemMalaRepository extends JpaRepository<MarcacaoItemMala, UUID> {

    List<MarcacaoItemMala> findByItemIdIn(Collection<UUID> itemIds);

    List<MarcacaoItemMala> findByAlunoIdAndItemIdIn(UUID alunoId, Collection<UUID> itemIds);

    Optional<MarcacaoItemMala> findByItemIdAndAlunoId(UUID itemId, UUID alunoId);

    // Anonimização (LGPD): apaga o recado livre da família nas marcações; o "enviado" fica.
    @Modifying(flushAutomatically = true)
    @Query("UPDATE MarcacaoItemMala m SET m.observacao = NULL WHERE m.alunoId = :alunoId")
    int apagarObservacoesDoAluno(@Param("alunoId") UUID alunoId);
}
