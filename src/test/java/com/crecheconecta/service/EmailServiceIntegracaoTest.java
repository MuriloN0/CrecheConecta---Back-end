package com.crecheconecta.service;


import com.crecheconecta.entity.FinalidadeAcao;
import com.crecheconecta.security.SegredoVerificacaoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.hibernate.validator.internal.util.Contracts.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
@EnabledIfEnvironmentVariable(
        named = "EXECUTAR_TESTE_RESEND",
        matches = "true"
)
public class EmailServiceIntegracaoTest {

    @Autowired
    private EmailService emailService;

    @Autowired
    private SegredoVerificacaoService segredoService;

    @Test
    void deveEnviarCodigoPeloResend() {
        String destinatario =
                System.getenv("RESEND_TEST_DESTINATARIO");

        assertNotNull(
                destinatario,
                "Configure RESEND_TEST_DESTINATARIO."
        );

        assertFalse(
                destinatario.isBlank(),
                "RESEND_TEST_DESTINATARIO não pode estar vazio."
        );

        String emailId = emailService.enviarCodigo(
                UUID.randomUUID(),
                destinatario,
                segredoService.gerarCodigo(),
                FinalidadeAcao.LOGIN
        );

        assertNotNull(emailId);
        assertFalse(emailId.isBlank());
    }

}
