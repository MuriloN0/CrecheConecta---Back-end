package com.crecheconecta.security;

import com.crecheconecta.entity.FinalidadeAcao;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;

@Service
public class SegredoVerificacaoService {

    private static final String ALGORITMO = "HmacSHA256";

    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec chave;

    public SegredoVerificacaoService(
            @Value("${app.seguranca.hmac-chave-base64}")
            String chaveBase64
    ) {
        byte[] bytes;

        try {
            bytes = Base64.getDecoder().decode(chaveBase64);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "AUTH_HMAC_KEY_BASE64 deve conter Base64 válido."
            );
        }

        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "AUTH_HMAC_KEY_BASE64 deve representar pelo menos 32 bytes."
            );
        }

        this.chave = new SecretKeySpec(bytes, ALGORITMO);
    }

    public String gerarCodigo() {
        return String.format(
                Locale.ROOT,
                "%06d",
                random.nextInt(1_000_000)
        );
    }

    public String gerarToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    public String proteger(
            UUID usuarioId,
            UUID acaoId,
            FinalidadeAcao finalidade,
            String segredo
    ) {
        Objects.requireNonNull(usuarioId);
        Objects.requireNonNull(acaoId);
        Objects.requireNonNull(finalidade);
        Objects.requireNonNull(segredo);

        String conteudo = usuarioId
                + "|" + acaoId
                + "|" + finalidade.name()
                + "|" + segredo;

        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(chave);

            byte[] resultado = mac.doFinal(
                    conteudo.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(resultado);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "Não foi possível executar a proteção criptográfica.",
                    exception
            );
        }
    }

    public boolean conferir(
            UUID usuarioId,
            UUID acaoId,
            FinalidadeAcao finalidade,
            String segredoInformado,
            String hashArmazenado
    ) {
        if (segredoInformado == null || hashArmazenado == null) {
            return false;
        }

        String hashCalculado = proteger(
                usuarioId,
                acaoId,
                finalidade,
                segredoInformado
        );

        return MessageDigest.isEqual(
                hashArmazenado.getBytes(StandardCharsets.US_ASCII),
                hashCalculado.getBytes(StandardCharsets.US_ASCII)
        );
    }

}
