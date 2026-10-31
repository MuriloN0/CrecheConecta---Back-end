package com.crecheconecta.repository;

import com.crecheconecta.entity.AlunoResponsavel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlunoResponsavelRepository extends JpaRepository<AlunoResponsavel, UUID> {

    List<AlunoResponsavel>
    findByAlunoIdAndEncerradoEmIsNullOrderByPosicaoAsc(UUID alunoId);

    boolean existsByResponsavelIdAndEncerradoEmIsNull(UUID responsavelId);

    Optional<AlunoResponsavel> findByIdAndAlunoId(UUID id, UUID alunoId);
}