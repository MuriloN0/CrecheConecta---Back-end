package com.crecheconecta.model;

import com.crecheconecta.exception.RegraNegocioException;

public record DadosFichaSaude(String nome, String observacoes) {

    private static final int NOME_MAX = 120;
    private static final int OBS_MAX = 5000;

    public DadosFichaSaude {
        if (nome == null || nome.isBlank()) {
            throw new RegraNegocioException("A ficha precisa de um nome.");
        }
        nome = nome.trim();
        if (nome.length() > NOME_MAX) {
            throw new RegraNegocioException("O nome da ficha deve ter até " + NOME_MAX + " caracteres.");
        }

        if (observacoes != null) {
            observacoes = observacoes.trim();
            if (observacoes.isBlank()) {
                observacoes = null;
            } else if (observacoes.length() > OBS_MAX) {
                throw new RegraNegocioException(
                        "As observações devem ter até " + OBS_MAX + " caracteres.");
            }
        }
    }
}
