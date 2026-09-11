package com.crecheconecta.atividades.application.port.out;

import com.crecheconecta.atividades.domain.model.Atividade;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AtividadeRepository {
    void salvar(Atividade atividade); // Já existia
    Optional<Atividade> buscarPorId(UUID id);
    void deletarPorId(UUID id);
    List<Atividade> buscarTodas();
    List<Atividade> buscarPorTurma(UUID turmaId);
}