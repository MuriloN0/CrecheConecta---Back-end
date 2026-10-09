package com.crecheconecta.model;

import com.crecheconecta.exception.RegraNegocioException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes unitarios do record DadosFichaSaude.
 *
 * Todas as validacoes acontecem no compact constructor, entao basta
 * construir o record e verificar o resultado ou a excecao. Nao ha
 * dependencias: e o teste unitario mais simples possivel.
 *
 * Regras:
 *  - nome obrigatorio (nao nulo/nao em branco), no maximo 120 caracteres;
 *  - nome e aparado (trim);
 *  - observacoes no maximo 5000 caracteres; em branco vira null.
 */
class DadosFichaSaudeTest {

    // ---------- CAMINHO FELIZ ----------

    @Test
    void deveCriarFichaValidaEAparandoOsEspacosDoNome() {
        // Arrange + Act
        DadosFichaSaude dados = new DadosFichaSaude("  Maria Clara  ", "  usa inalador  ");

        // Assert: trim aplicado no nome e nas observacoes
        assertEquals("Maria Clara", dados.nome());
        assertEquals("usa inalador", dados.observacoes());
    }

    @Test
    void deveTransformarObservacoesEmBrancoEmNulo() {
        // Act: observacoes so com espacos
        DadosFichaSaude dados = new DadosFichaSaude("Joao", "    ");

        // Assert
        assertNull(dados.observacoes(), "observacoes em branco devem virar null");
    }

    // ---------- VIOLACAO DE REGRA ----------

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void deveRejeitarNomeNuloOuEmBranco(String nomeInvalido) {
        // Act + Assert
        RegraNegocioException ex = assertThrows(
                RegraNegocioException.class,
                () -> new DadosFichaSaude(nomeInvalido, "obs"));
        assertTrue(ex.getMessage().contains("nome"));
    }

    @Test
    void deveRejeitarNomeComMaisDe120Caracteres() {
        // Arrange: 121 caracteres
        String nomeLongo = "a".repeat(121);

        // Act + Assert
        RegraNegocioException ex = assertThrows(
                RegraNegocioException.class,
                () -> new DadosFichaSaude(nomeLongo, null));
        assertTrue(ex.getMessage().contains("120"));
    }

    @Test
    void deveRejeitarObservacoesComMaisDe5000Caracteres() {
        // Arrange: 5001 caracteres
        String obsLonga = "x".repeat(5001);

        // Act + Assert
        RegraNegocioException ex = assertThrows(
                RegraNegocioException.class,
                () -> new DadosFichaSaude("Pedro", obsLonga));
        assertTrue(ex.getMessage().contains("5000"));
    }

    // ---------- CASO-LIMITE (fronteira exata) ----------

    @Test
    void deveAceitarNomeComExatos120Caracteres() {
        // Arrange: exatamente no limite
        String nome120 = "a".repeat(120);

        // Act
        DadosFichaSaude dados = new DadosFichaSaude(nome120, null);

        // Assert: 120 e permitido (o limite e "ate 120")
        assertEquals(120, dados.nome().length());
    }

    @Test
    void deveAceitarObservacoesComExatos5000Caracteres() {
        // Arrange: exatamente no limite
        String obs5000 = "y".repeat(5000);

        // Act
        DadosFichaSaude dados = new DadosFichaSaude("Pedro", obs5000);

        // Assert
        assertEquals(5000, dados.observacoes().length());
    }
}
