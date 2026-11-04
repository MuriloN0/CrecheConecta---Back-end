package com.crecheconecta.repository;

import com.crecheconecta.entity.Chamada;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ChamadaRepository extends JpaRepository<Chamada, UUID> {

    // Registros de um aluno num intervalo de datas (ex.: um mês), do dia mais antigo para o mais novo.
    List<Chamada> findByAlunoIdAndDataBetweenOrderByDataAsc(UUID alunoId, LocalDate inicio, LocalDate fim);
}
