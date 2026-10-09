package com.crecheconecta.service.atividades;

import com.crecheconecta.model.TipoAtividade;
import com.crecheconecta.service.AtividadeValidation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AtividadeValidationTest {

    private AtividadeValidation validation;

    @BeforeEach
    void setUp() {
        validation = new AtividadeValidation();
    }

    @Test
    void aceitaTituloValido() {
        // Arrange
        String titulo = "Atividade de leitura";

        // Act & Assert
        assertDoesNotThrow(() -> validation.validarTitulo(titulo));
    }

    @Test
    void rejeitaTituloEmBranco() {
        // Arrange
        String titulo = "   ";

        // Act
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> validation.validarTitulo(titulo)
        );

        // Assert
        assertEquals("Título é obrigatório", ex.getMessage());
    }

    @Test
    void aceitaTituloCom150Caracteres() {
        // Arrange
        String titulo = "a".repeat(150);

        // Act & Assert
        assertDoesNotThrow(() -> validation.validarTitulo(titulo));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void rejeitaTitulosInvalidos(String titulo) {
        // Act
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> validation.validarTitulo(titulo)
        );

        // Assert
        assertEquals("Título é obrigatório", ex.getMessage());
    }

    @Test
    void casaExigePrazo() {
        // Arrange
        TipoAtividade tipo = TipoAtividade.CASA;

        // Act
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> validation.validarPrazo(tipo, null)
        );

        // Assert
        assertEquals(
                "Prazo de conclusão é obrigatório para atividade de casa",
                ex.getMessage()
        );
    }

    @Test
    void diaPermiteSemPrazo() {
        // Arrange
        TipoAtividade tipo = TipoAtividade.DIA;

        // Act & Assert
        assertDoesNotThrow(() -> validation.validarPrazo(tipo, null));
    }
}
