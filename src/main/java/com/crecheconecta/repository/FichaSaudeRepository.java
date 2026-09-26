package com.crecheconecta.repository;

import com.crecheconecta.entity.FichaSaude;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FichaSaudeRepository extends JpaRepository<FichaSaude, UUID> {
    
    List<FichaSaude> findByAlunoId(UUID alunoId);
}
