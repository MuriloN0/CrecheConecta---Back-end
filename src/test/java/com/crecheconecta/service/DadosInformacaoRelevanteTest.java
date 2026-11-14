package com.crecheconecta.service;

import com.crecheconecta.exception.RegraNegocioException;
import com.crecheconecta.model.DadosInformacaoRelevante;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class DadosInformacaoRelevanteTest {

    @Test
    void deveNormalizarNomeEObservacoes() {
        var dados = new DadosInformacaoRelevante(
                " Ficha ",
                " Observação "
        );

        assertEquals("Ficha", dados.nome());
        assertEquals("Observação", dados.observacoes());
    }

    @Test
    void deveTransformarObservacoesEmBrancoEmNulo() {
        var dados = new DadosInformacaoRelevante("Ficha", "   ");

        assertNull(dados.observacoes());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void deveRejeitarNomeInvalido(String nome) {
        assertThrows(
                RegraNegocioException.class,
                () -> new DadosInformacaoRelevante(nome, null)
        );
    }

    @Test
    void deveRejeitarNomeAcimaDoLimite() {
        assertThrows(
                RegraNegocioException.class,
                () -> new DadosInformacaoRelevante(
                        "A".repeat(121),
                        null
                )
        );
    }

    @Test
    void deveRejeitarObservacoesAcimaDoLimite() {
        assertThrows(
                RegraNegocioException.class,
                () -> new DadosInformacaoRelevante(
                        "Ficha",
                        "A".repeat(5001)
                )
        );
    }
}