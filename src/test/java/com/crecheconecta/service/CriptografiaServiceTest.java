package com.crecheconecta.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes unitarios do CriptografiaService (AES-256-GCM).
 *
 * E um teste unitario puro: nao sobe o Spring e nao usa banco.
 * O service e construido manualmente com uma chave de teste
 * (base64 de 32 bytes = AES-256). Essa chave e so para teste.
 */
class CriptografiaServiceTest {

    // Chave AES-256 (32 bytes) em base64, exclusiva para os testes.
    private static final String CHAVE_TESTE =
            "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    private CriptografiaService cripto;

    @BeforeEach
    void preparar() {
        cripto = new CriptografiaService(CHAVE_TESTE);
    }

    // ---------- CAMINHO FELIZ ----------

    @Test
    void deveCifrarEDecifrarRecuperandoOTextoOriginal() {
        // Arrange
        String original = "Alergia a lactose e amendoim";

        // Act
        String cifrado = cripto.cifrar(original);
        String decifrado = cripto.decifrar(cifrado);

        // Assert
        assertNotEquals(original, cifrado, "o texto cifrado nao pode ser igual ao claro");
        assertEquals(original, decifrado, "decifrar deve recuperar o texto original");
    }

    @Test
    void devePreservarAcentosESimbolosUtf8NoCicloCompleto() {
        // Arrange
        String original = "Observação: criança com restrição à glúten (coração ♥)";

        // Act
        String decifrado = cripto.decifrar(cripto.cifrar(original));

        // Assert
        assertEquals(original, decifrado);
    }

    @Test
    void deveGerarCifradosDiferentesParaOMesmoTextoDevidoAoIvAleatorio() {
        // Arrange
        String original = "mesmo texto";

        // Act
        String primeiro = cripto.cifrar(original);
        String segundo = cripto.cifrar(original);

        // Assert
        assertNotEquals(primeiro, segundo,
                "cada cifragem usa um IV aleatorio, entao o resultado deve diferir");
        // mas ambos decifram para o mesmo texto
        assertEquals(original, cripto.decifrar(primeiro));
        assertEquals(original, cripto.decifrar(segundo));
    }

    // ---------- CASO-LIMITE ----------

    @Test
    void deveRetornarNuloQuandoEntradaForNula() {
        // Act + Assert
        assertNull(cripto.cifrar(null), "cifrar(null) deve devolver null");
        assertNull(cripto.decifrar(null), "decifrar(null) deve devolver null");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "a"})
    void deveCifrarEDecifrarValoresDeFronteira(String valor) {
        // Act
        String decifrado = cripto.decifrar(cripto.cifrar(valor));

        // Assert
        assertEquals(valor, decifrado);
    }

    // ---------- VIOLACAO / ERRO ----------

    @Test
    void deveLancarExcecaoAoDecifrarTextoCorrompido() {
        // Arrange: texto que nem sequer e base64 valido
        String lixo = "isto-nao-e-um-conteudo-cifrado-valido";

        // Act
        RuntimeException ex = assertThrows(
                RuntimeException.class,
                () -> cripto.decifrar(lixo));

        // Assert
        assertTrue(ex.getMessage().contains("decifrar"),
                "a mensagem deve indicar falha ao decifrar");
    }

    @Test
    void deveLancarExcecaoAoDecifrarConteudoAdulterado() {
        // Arrange: cifra de verdade e depois adultera um caractere do meio
        String cifrado = cripto.cifrar("conteudo sensivel");
        char[] chars = cifrado.toCharArray();
        int meio = chars.length / 2;
        chars[meio] = (chars[meio] == 'A') ? 'B' : 'A';
        String adulterado = new String(chars);

        // Act + Assert: a tag de autenticacao do GCM deve rejeitar
        RuntimeException ex = assertThrows(
                RuntimeException.class,
                () -> cripto.decifrar(adulterado));
        assertTrue(ex.getMessage().contains("decifrar"));
    }
}
