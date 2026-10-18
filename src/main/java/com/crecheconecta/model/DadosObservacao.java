package com.crecheconecta.model;

import com.crecheconecta.exception.RegraNegocioException;

public record DadosObservacao(String titulo, String conteudo) {

    private static final int TITULO_MAX = 120;
    private static final int CONTEUDO_MAX = 5000;

    public DadosObservacao {
        if (titulo == null || titulo.isBlank()) {
            throw new RegraNegocioException("A observacao precisa de um titulo.");
        }
        titulo = titulo.trim();
        if (titulo.length() > TITULO_MAX) {
            throw new RegraNegocioException(
                    "O titulo deve ter ate " + TITULO_MAX + " caracteres.");
        }

        if (conteudo != null) {
            conteudo = conteudo.trim();
            if (conteudo.isBlank()) {
                conteudo = null;
            } else if (conteudo.length() > CONTEUDO_MAX) {
                throw new RegraNegocioException(
                        "O conteudo deve ter ate " + CONTEUDO_MAX + " caracteres.");
            }
        }
    }
}
