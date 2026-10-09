package com.crecheconecta.service.atividades;

import com.crecheconecta.exception.RegraNegocioException;
import com.crecheconecta.service.AtividadeTituloValidation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AtividadeTituloValidationTest {

    private AtividadeTituloValidation validation;

    @BeforeEach
    void setUp() {
        validation = new AtividadeTituloValidation();
    }

    @Test
    void aceitaTituloValido() {
        // Arrange
        String titulo = "Atividade de leitura";

        // Act & Assert
        assertDoesNotThrow(() -> validation.validar(titulo));
    }

    @Test
    void rejeitaTituloEmBranco() {
        // Arrange
        String titulo = "   ";

        // Act
        RegraNegocioException ex = assertThrows(
                RegraNegocioException.class,
                () -> validation.validar(titulo)
        );

        // Assert
        assertEquals("Título é obrigatório", ex.getMessage());
    }

    @Test
    void aceitaTituloCom150Caracteres() {
        // Arrange
        String titulo = "a".repeat(150);

        // Act & Assert
        assertDoesNotThrow(() -> validation.validar(titulo));
    }

    @Test
    void rejeitaTituloCom151Caracteres() {
        // Arrange
        String titulo = "a".repeat(151);

        // Act
        RegraNegocioException ex = assertThrows(
                RegraNegocioException.class,
                () -> validation.validar(titulo)
        );

        // Assert
        assertEquals("Título excedeu 150 caracteres", ex.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void rejeitaTitulosInvalidos(String titulo) {
        // Act
        RegraNegocioException ex = assertThrows(
                RegraNegocioException.class,
                () -> validation.validar(titulo)
        );

        // Assert
        assertEquals("Título é obrigatório", ex.getMessage());
    }
}
