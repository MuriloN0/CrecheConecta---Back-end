package com.crecheconecta.saude.application.service;

import com.crecheconecta.saude.application.port.in.ListarFichasSaudeUseCase;
import com.crecheconecta.saude.application.port.out.FichaSaudeRepositoryPort;
import com.crecheconecta.saude.domain.model.FichaSaude;

import java.util.List;

public final class ListarFichasSaudeService implements ListarFichasSaudeUseCase {
    private final FichaSaudeRepositoryPort repository;
    private final PoliticaAcessoDadosSaude politica;

    public ListarFichasSaudeService(
            FichaSaudeRepositoryPort repository,
            PoliticaAcessoDadosSaude politica
    ) {
        this.repository = repository;
        this.politica = politica;
    }

    @Override
    public List<FichaSaude> executar(Consulta consulta) {
        politica.exigirAcesso(consulta.usuarioId(), consulta.alunoId());
        return repository.listarPorAluno(consulta.alunoId());
    }
}
