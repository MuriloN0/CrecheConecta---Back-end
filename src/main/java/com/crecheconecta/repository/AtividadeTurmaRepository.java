package com.crecheconecta.repository;

import com.crecheconecta.entity.Turma;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AtividadeTurmaRepository extends Repository<Turma, UUID> {

    @Query("""
            SELECT t
            FROM Turma t
            WHERE t.ativo = TRUE
            ORDER BY t.nome, t.id
            """)
    List<Turma> buscarDaDirecao();

    @Query("""
            SELECT t
            FROM Turma t
            WHERE t.ativo = TRUE
              AND EXISTS (
                  SELECT v.id
                  FROM TurmaProfessor v
                  WHERE v.turmaId = t.id
                    AND v.professorId = :professorId
                    AND v.encerradoEm IS NULL
              )
            ORDER BY t.nome, t.id
            """)
    List<Turma> buscarDoProfessor(@Param("professorId") UUID professorId);

    @Query("""
            SELECT t
            FROM Turma t
            WHERE t.ativo = TRUE
              AND EXISTS (
                  SELECT m.id
                  FROM MatriculaTurma m, AlunoResponsavel r, Aluno a
                  WHERE m.turmaId = t.id
                    AND m.encerradoEm IS NULL
                    AND r.alunoId = m.alunoId
                    AND r.responsavelId = :responsavelId
                    AND r.encerradoEm IS NULL
                    AND a.id = m.alunoId
                    AND a.ativo = TRUE
              )
            ORDER BY t.nome, t.id
            """)
    List<Turma> buscarDoResponsavel(
            @Param("responsavelId") UUID responsavelId
    );
}