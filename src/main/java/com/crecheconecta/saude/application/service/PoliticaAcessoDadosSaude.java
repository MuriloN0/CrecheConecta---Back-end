package com.crecheconecta.saude.application.service;

import com.crecheconecta.saude.application.exception.AcessoNegadoException;
import com.crecheconecta.saude.application.exception.FichaSaudeInvalidaException;
import com.crecheconecta.saude.application.exception.FichaSaudeNaoEncontradaException;
import com.crecheconecta.saude.application.port.out.VinculoResponsavelPort;
import com.crecheconecta.saude.domain.model.FichaSaude;

import java.util.UUID;

public final class PoliticaAcessoDadosSaude {

    private final VinculoResponsavelPort vinculos;

    public PoliticaAcessoDadosSaude(VinculoResponsavelPort vinculos) {
        this.vinculos = vinculos;
    }

    public void exigirAcesso(UUID usuarioId, UUID alunoId) {
        if (!vinculos.responsavelAtivoDoAluno(usuarioId, alunoId)) {
            throw new AcessoNegadoException();
        }
    }

    public void exigirPropriedade(FichaSaude ficha, UUID alunoId){
        if (!ficha.pertenceAo(alunoId)){
            throw new FichaSaudeNaoEncontradaException(ficha.id());
        }
    }
}
