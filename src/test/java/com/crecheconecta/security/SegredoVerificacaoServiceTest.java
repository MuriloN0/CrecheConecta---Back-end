package com.crecheconecta.security;

import com.crecheconecta.entity.FinalidadeAcao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SegredoVerificacaoServiceTest {

    private SegredoVerificacaoService service;

    private UUID usuarioId;

    private UUID acaoId;

    @BeforeEach
    void preparar() {
        String chaveBase64 = Base64.getEncoder()
                .encodeToString(new byte[32]);

        service = new SegredoVerificacaoService(chaveBase64);

        usuarioId = UUID.fromString(
                "00000000-0000-0000-0000-000000000001"
        );

        acaoId = UUID.fromString(
                "00000000-0000-0000-0000-000000000002"
        );
    }

    @Test
    void deveAceitarCodigoProtegidoComMesmoUsuarioEAcao() {
        // Arrange
        String codigo = "012345";

        String hash = service.proteger(
                usuarioId,
                acaoId,
                FinalidadeAcao.LOGIN,
                codigo
        );

        // Act
        boolean valido = service.conferir(
                usuarioId,
                acaoId,
                FinalidadeAcao.LOGIN,
                codigo,
                hash
        );

        // Assert
        assertTrue(valido);

        // HMAC-SHA256 representado por 64 caracteres hexadecimais.
        assertTrue(hash.matches("[0-9a-f]{64}"));
    }

    @Test
    void deveRejeitarCodigoProtegidoParaOutroUsuario() {
        // Arrange
        String codigo = "012345";

        String hash = service.proteger(
                usuarioId,
                acaoId,
                FinalidadeAcao.LOGIN,
                codigo
        );

        UUID outroUsuarioId = UUID.fromString(
                "00000000-0000-0000-0000-000000000003"
        );

        // Act
        boolean valido = service.conferir(
                outroUsuarioId,
                acaoId,
                FinalidadeAcao.LOGIN,
                codigo,
                hash
        );

        // Assert
        assertFalse(valido);
    }

    @Test
    void deveRejeitarCodigoProtegidoParaOutraAcao() {
        // Arrange
        String codigo = "012345";

        String hash = service.proteger(
                usuarioId,
                acaoId,
                FinalidadeAcao.LOGIN,
                codigo
        );

        UUID outraAcaoId = UUID.fromString(
                "00000000-0000-0000-0000-000000000004"
        );

        // Act
        boolean valido = service.conferir(
                usuarioId,
                outraAcaoId,
                FinalidadeAcao.LOGIN,
                codigo,
                hash
        );

        // Assert
        assertFalse(valido);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "999999"})
    void deveRejeitarCodigoNuloVazioOuIncorreto(
            String codigoInformado
    ) {
        // Arrange
        String hash = service.proteger(
                usuarioId,
                acaoId,
                FinalidadeAcao.LOGIN,
                "012345"
        );

        // Act
        boolean valido = service.conferir(
                usuarioId,
                acaoId,
                FinalidadeAcao.LOGIN,
                codigoInformado,
                hash
        );

        // Assert
        assertFalse(valido);
    }

    @Test
    void deveRejeitarConferenciaQuandoHashArmazenadoForNulo() {
        // Arrange
        String codigo = "012345";

        // Act
        boolean valido = service.conferir(
                usuarioId,
                acaoId,
                FinalidadeAcao.LOGIN,
                codigo,
                null
        );

        // Assert
        assertFalse(valido);
    }

    @Test
    void deveAceitarChaveComExatamenteTrintaEDoisBytes() {
        // Arrange
        String chaveBase64 = Base64.getEncoder()
                .encodeToString(new byte[32]);

        // Act
        var serviceComChaveMinima = assertDoesNotThrow(
                () -> new SegredoVerificacaoService(chaveBase64)
        );

        String hash = serviceComChaveMinima.proteger(
                usuarioId,
                acaoId,
                FinalidadeAcao.LOGIN,
                "012345"
        );

        // Assert
        assertTrue(
                serviceComChaveMinima.conferir(
                        usuarioId,
                        acaoId,
                        FinalidadeAcao.LOGIN,
                        "012345",
                        hash
                )
        );
    }

    @Test
    void deveRejeitarChaveComTrintaEUmBytes() {
        // Arrange
        String chaveBase64 = Base64.getEncoder()
                .encodeToString(new byte[31]);

        // Act
        var exception = assertThrows(
                IllegalStateException.class,
                () -> new SegredoVerificacaoService(chaveBase64)
        );

        // Assert
        assertEquals(
                "AUTH_HMAC_KEY_BASE64 deve representar pelo menos 32 bytes.",
                exception.getMessage()
        );
    }

    @Test
    void deveRejeitarChaveQueNaoSejaBase64Valido() {
        // Arrange
        String chaveInvalida = "%%%";

        // Act
        var exception = assertThrows(
                IllegalStateException.class,
                () -> new SegredoVerificacaoService(chaveInvalida)
        );

        // Assert
        assertEquals(
                "AUTH_HMAC_KEY_BASE64 deve conter Base64 válido.",
                exception.getMessage()
        );
    }
}