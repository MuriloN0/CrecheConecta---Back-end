package com.crecheconecta.saude.application.service;

import com.crecheconecta.saude.application.exception.FichaSaudeNaoEncontradaException;
import com.crecheconecta.saude.application.port.in.VisualizarFichaSaudeUseCase;
import com.crecheconecta.saude.application.port.out.CriptografiaPort;
import com.crecheconecta.saude.application.port.out.FichaSaudeRepositoryPort;
import com.crecheconecta.saude.domain.model.FichaSaude;

public final class VisualizarFichaSaudeService implements VisualizarFichaSaudeUseCase {

    private final FichaSaudeRepositoryPort repository;
    private final CriptografiaPort cripto;
    private final PoliticaAcessoDadosSaude politica;

    public VisualizarFichaSaudeService(
            FichaSaudeRepositoryPort repository,
            CriptografiaPort cripto,
            PoliticaAcessoDadosSaude politica
    ) {
        this.repository = repository;
        this.cripto = cripto;
        this.politica = politica;
    }

    @Override
    public FichaSaude executar(Consulta consulta) {
        politica.exigirAcesso(consulta.usuarioId(), consulta.alunoId());

        FichaSaude ficha = repository.buscarPorId(consulta.fichaId())
                .orElseThrow(() -> new FichaSaudeNaoEncontradaException(consulta.fichaId()));

        politica.exigirPropriedade(ficha, consulta.alunoId());

        String obsClara = ficha.observacoes() == null
                ? null
                : cripto.decifrar(ficha.observacoes());

        return ficha.comAlteracoes(
                ficha.nome(), obsClara, ficha.anexos(), ficha.dataAtualizacao());
    }
}
