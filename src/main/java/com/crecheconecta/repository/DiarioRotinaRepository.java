package com.crecheconecta.repository;

import com.crecheconecta.entity.DiarioRotina;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DiarioRotinaRepository extends JpaRepository<DiarioRotina, UUID> {

    // Diário de um dia específico (no máximo 1, garantido por uk_diarios_aluno_data).
    Optional<DiarioRotina> findByAlunoIdAndData(UUID alunoId, LocalDate data);

    // Diários de uma turma num dia (tela do professor).
    List<DiarioRotina> findByTurmaIdAndData(UUID turmaId, LocalDate data);

    // Diário mais recente do aluno.
    Optional<DiarioRotina> findFirstByAlunoIdOrderByDataDesc(UUID alunoId);

    // Exportação dos dados (LGPD): todos os diários do aluno, do mais recente para o mais antigo.
    List<DiarioRotina> findByAlunoIdOrderByDataDesc(UUID alunoId);

    // Anonimização (LGPD): o texto livre do professor pode identificar a criança; as opções do diário ficam.
    @Modifying(flushAutomatically = true)
    @Query("UPDATE DiarioRotina d SET d.observacaoProfessor = NULL WHERE d.alunoId = :alunoId")
    int apagarObservacoesDoAluno(@Param("alunoId") UUID alunoId);
}
