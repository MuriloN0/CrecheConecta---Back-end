package com.crecheconecta.saude.application.service;

import com.crecheconecta.saude.application.exception.FichaSaudeNaoEncontradaException;
import com.crecheconecta.saude.application.port.in.ExcluirFichaSaudeUseCase;
import com.crecheconecta.saude.application.port.out.ArmazenamentoArquivoPort;
import com.crecheconecta.saude.application.port.out.FichaSaudeRepositoryPort;
import com.crecheconecta.saude.domain.model.Anexo;
import com.crecheconecta.saude.domain.model.FichaSaude;

public final class ExcluirFichaSaudeService implements ExcluirFichaSaudeUseCase {

    private final FichaSaudeRepositoryPort repository;
    private final ArmazenamentoArquivoPort armazenamento;
    private final PoliticaAcessoDadosSaude politica;

    public ExcluirFichaSaudeService(
            FichaSaudeRepositoryPort repository,
            ArmazenamentoArquivoPort armazenamento,
            PoliticaAcessoDadosSaude politica
    ) {
        this.repository = repository;
        this.armazenamento = armazenamento;
        this.politica = politica;
    }

    @Override
    public void executar(Comando comando) {
        politica.exigirAcesso(comando.usuarioId(), comando.alunoId());

        FichaSaude ficha = repository.buscarPorId(comando.fichaId())
                .orElseThrow(() -> new FichaSaudeNaoEncontradaException(comando.fichaId()));
        politica.exigirPropriedade(ficha, comando.alunoId());

        for (Anexo anexo : ficha.anexos()) {
            armazenamento.remover(anexo.chaveArmazenamento());
        }

        repository.excluir(ficha.id());
    }
}
