package com.crecheconecta.repository;

import com.crecheconecta.entity.AlunoResponsavel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlunoResponsavelRepository extends JpaRepository<AlunoResponsavel, UUID> {

    List<AlunoResponsavel> findByAlunoIdAndEncerradoEmIsNullOrderByCriadoEmAsc(UUID alunoId);

    boolean existsByResponsavelIdAndEncerradoEmIsNull(UUID responsavelId);

    Optional<AlunoResponsavel> findByIdAndAlunoId(UUID id, UUID alunoId);

    boolean existsByAlunoIdAndEncerradoEmIsNull(UUID alunoId);

    // Features dos pais: lista os vínculos ATUAIS (não encerrados) de um responsável.
    List<AlunoResponsavel> findByResponsavelIdAndEncerradoEmIsNull(UUID responsavelId);

    // Features dos pais: "este responsável tem vínculo atual com este aluno?" (anti-IDOR).
    boolean existsByAlunoIdAndResponsavelIdAndEncerradoEmIsNull(UUID alunoId, UUID responsavelId);
}
