package com.crecheconecta.model;

import com.crecheconecta.exception.RegraNegocioException;

public record DadosObservacao(String titulo, String conteudo) {

    private static final int TITULO_MAX = 120;
    private static final int CONTEUDO_MAX = 5000;

    public DadosObservacao {
        if (titulo == null || titulo.isBlank()) {
            throw new RegraNegocioException("A observação precisa de um título.");
        }

        titulo = titulo.strip();

        if (titulo.length() > TITULO_MAX) {
            throw new RegraNegocioException("O título deve ter até " + TITULO_MAX + " caracteres.");
        }

        if (conteudo != null) {
            conteudo = conteudo.strip();

            if (conteudo.isBlank()) {
                conteudo = null;
            } else if (conteudo.length() > CONTEUDO_MAX) {
                throw new RegraNegocioException("O conteúdo deve ter até " + CONTEUDO_MAX + " caracteres.");
            }
        }
    }
}
