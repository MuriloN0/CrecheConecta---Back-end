package com.crecheconecta.saude.adapter.out.seguranca;

import com.crecheconecta.saude.application.port.out.CriptografiaPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class CriptografiaAdapter implements CriptografiaPort {

    private static final String ALGORITMO = "AES/GCM/NoPadding";
    private static final int TAMANHO_IV = 12;
    private static final int TAMANHO_TAG = 128;
    private final SecretKeySpec chave;
    private final SecureRandom random = new SecureRandom();

    public CriptografiaAdapter(@Value("${saude.criptografia.chave}") String chaveBase64) {
        byte[] bytesChave = Base64.getDecoder().decode(chaveBase64);
        this.chave = new SecretKeySpec(bytesChave, "AES");
    }

    @Override
    public String cifrar(String textoClaro) {
        if (textoClaro == null) {
            return null;
        }
        try {
            byte[] iv = new byte[TAMANHO_IV];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITMO);
            cipher.init(Cipher.ENCRYPT_MODE, chave, new GCMParameterSpec(TAMANHO_TAG, iv));
            byte[] cifrado = cipher.doFinal(textoClaro.getBytes(StandardCharsets.UTF_8));

            // Junta IV + dados cifrados num unico bloco
            byte[] resultado = new byte[iv.length + cifrado.length];
            System.arraycopy(iv, 0, resultado, 0, iv.length);
            System.arraycopy(cifrado, 0, resultado, iv.length, cifrado.length);

            return Base64.getEncoder().encodeToString(resultado);
        } catch (Exception e) {
            throw new RuntimeException("Falha ao cifrar o conteudo.", e);
        }
    }

    @Override
    public String decifrar(String textoCifrado) {
        if (textoCifrado == null) {
            return null;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(textoCifrado);

            byte[] iv = new byte[TAMANHO_IV];
            System.arraycopy(bytes, 0, iv, 0, TAMANHO_IV);
            byte[] cifrado = new byte[bytes.length - TAMANHO_IV];
            System.arraycopy(bytes, TAMANHO_IV, cifrado, 0, cifrado.length);

            Cipher cipher = Cipher.getInstance(ALGORITMO);
            cipher.init(Cipher.DECRYPT_MODE, chave, new GCMParameterSpec(TAMANHO_TAG, iv));
            byte[] claro = cipher.doFinal(cifrado);

            return new String(claro, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Falha ao decifrar o conteudo.", e);
        }
    }
}