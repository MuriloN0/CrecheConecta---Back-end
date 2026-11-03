package com.crecheconecta.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenSessaoServiceTest {

    private final TokenSessaoService service = new TokenSessaoService();

    @Test
    void deveGerarTokensComFormatoEsperado() {
        String primeiro = service.gerar();
        String segundo = service.gerar();

        assertTrue(primeiro.matches("[A-Za-z0-9_-]{43}"));
        assertTrue(segundo.matches("[A-Za-z0-9_-]{43}"));
        assertNotEquals(primeiro, segundo);
    }

    @Test
    void deveCalcularHashConhecido() {
        assertEquals(
                "ba7816bf8f01cfea414140de5dae2223"
                        + "b00361a396177a9cb410ff61f20015ad",
                service.calcularHash("abc")
        );
    }
}