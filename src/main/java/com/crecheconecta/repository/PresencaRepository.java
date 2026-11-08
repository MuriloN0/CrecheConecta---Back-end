package com.crecheconecta.repository;

import com.crecheconecta.entity.Presenca;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PresencaRepository extends JpaRepository<Presenca, UUID> {

    Optional<Presenca> findByTurmaIdAndAlunoIdAndData(UUID turmaId, UUID alunoId, LocalDate data);

    List<Presenca> findByTurmaIdAndData(UUID turmaId, LocalDate data);

    // Registros de um aluno num intervalo de datas (ex.: um mês), do dia mais antigo para o mais novo.
    // Usado pelo portal dos pais.
    List<Presenca> findByAlunoIdAndDataBetweenOrderByDataAsc(UUID alunoId, LocalDate inicio, LocalDate fim);
}