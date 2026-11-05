package com.crecheconecta.repository;

import com.crecheconecta.entity.MalaVirtual;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MalaVirtualRepository extends JpaRepository<MalaVirtual, UUID> {

    // As 20 malas mais recentes do aluno (mais nova primeiro).
    List<MalaVirtual> findTop20ByAlunoIdOrderByDataDesc(UUID alunoId);
}
