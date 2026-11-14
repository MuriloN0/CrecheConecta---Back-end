package com.crecheconecta.repository;

import com.crecheconecta.entity.InformacaoRelevante;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InformacaoRelevanteRepository extends JpaRepository<InformacaoRelevante, UUID> {
    
    List<InformacaoRelevante> findByAlunoId(UUID alunoId);
}
