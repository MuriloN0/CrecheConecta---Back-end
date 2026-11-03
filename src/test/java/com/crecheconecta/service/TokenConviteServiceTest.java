package com.crecheconecta.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class TokenConviteServiceTest {

    private final TokenConviteService service = new TokenConviteService();

    @Test
    void deveGerarEProtegerToken() {
        String token = service.gerarToken();
        String hash = service.calcularHash(token);

        assertTrue(token.matches("[A-Za-z0-9_-]{43}"));
        assertTrue(hash.matches("[0-9a-f]{64}"));
        assertEquals(hash, service.calcularHash(token));

        assertNotEquals(
                hash,
                service.calcularHash("B".repeat(43))
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"abc", "token-invalido!"})
    void deveRejeitarTokenMalformado(String token) {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.calcularHash(token)
        );
    }
}