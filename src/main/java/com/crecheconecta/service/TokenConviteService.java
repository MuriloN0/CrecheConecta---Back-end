package com.crecheconecta.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class TokenConviteService {

    private final SecureRandom random = new SecureRandom();

    public String gerarToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    public String calcularHash(String token) {
        if (token == null
                || !token.matches("[A-Za-z0-9_-]{43}")) {
            throw new IllegalArgumentException(
                    "Token de convite inválido."
            );
        }

        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] resultado = digest.digest(
                    token.getBytes(StandardCharsets.US_ASCII)
            );

            return HexFormat.of().formatHex(resultado);

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "Não foi possível proteger o token do convite.",
                    exception
            );
        }
    }
}
