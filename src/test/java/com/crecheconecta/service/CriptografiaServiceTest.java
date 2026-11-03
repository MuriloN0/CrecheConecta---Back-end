package com.crecheconecta.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class CriptografiaServiceTest {

    private CriptografiaService service;

    @BeforeEach
    void preparar() {
        String chave = Base64.getEncoder()
                .encodeToString(new byte[32]);

        service = new CriptografiaService(chave);
    }

    @Test
    void deveCifrarEDecifrarTextoComAcentos() {
        String original = "Alergia a amendoim. Atenção à medicação.";

        String cifrado = service.cifrar(original);

        assertNotEquals(original, cifrado);
        assertEquals(original, service.decifrar(cifrado));
    }

    @Test
    void deveGerarCifradosDiferentesParaMesmoTexto() {
        String primeiro = service.cifrar("Informação sensível");
        String segundo = service.cifrar("Informação sensível");

        assertNotEquals(primeiro, segundo);
        assertEquals(
                service.decifrar(primeiro),
                service.decifrar(segundo)
        );
    }

    @Test
    void devePreservarValoresNulos() {
        assertNull(service.cifrar(null));
        assertNull(service.decifrar(null));
    }

    @Test
    void deveRejeitarConteudoAdulterado() {
        byte[] bytes = Base64.getDecoder()
                .decode(service.cifrar("Observação"));

        bytes[bytes.length - 1] ^= 1;

        String adulterado = Base64.getEncoder()
                .encodeToString(bytes);

        assertThrows(
                RuntimeException.class,
                () -> service.decifrar(adulterado)
        );
    }

    @Test
    void deveRejeitarDecifragemComOutraChave() {
        byte[] outraChave = new byte[32];
        Arrays.fill(outraChave, (byte) 1);

        var outroService = new CriptografiaService(
                Base64.getEncoder().encodeToString(outraChave)
        );

        String cifrado = service.cifrar("Observação");

        assertThrows(
                RuntimeException.class,
                () -> outroService.decifrar(cifrado)
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "!!!"})
    void deveRejeitarCifradoInvalido(String valor) {
        assertThrows(
                RuntimeException.class,
                () -> service.decifrar(valor)
        );
    }
}