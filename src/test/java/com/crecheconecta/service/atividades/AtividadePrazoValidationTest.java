package com.crecheconecta.service.atividades;

import com.crecheconecta.exception.RegraNegocioException;
import com.crecheconecta.model.TipoAtividade;
import com.crecheconecta.service.AtividadePrazoValidation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AtividadePrazoValidationTest {

    private AtividadePrazoValidation validation;

    @BeforeEach
    void setUp() {
        validation = new AtividadePrazoValidation();
    }

    @Test
    void casaExigePrazo() {
        // Arrange
        TipoAtividade tipo = TipoAtividade.CASA;

        // Act
        RegraNegocioException ex = assertThrows(
                RegraNegocioException.class,
                () -> validation.validar(tipo, null)
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
        assertDoesNotThrow(() -> validation.validar(tipo, null));
    }

    @Test
    void casaComPrazoValido() {
        // Arrange
        TipoAtividade tipo = TipoAtividade.CASA;
        LocalDate prazo = LocalDate.of(2026, 12, 31);

        // Act & Assert
        assertDoesNotThrow(() -> validation.validar(tipo, prazo));
    }
}
