package com.crecheconecta.atividades.application.port.out;

import com.crecheconecta.atividades.domain.model.Atividade;

public interface AtividadeRepository {
    void salvar(Atividade atividade);
}