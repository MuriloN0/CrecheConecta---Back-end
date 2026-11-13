package com.crecheconecta;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Confere que o contexto do Spring sobe. Usa o H2 do perfil "test", como o resto da suíte
 * (antes apontava para um Postgres local em localhost:5433, que só existia na máquina de um integrante).
 */
@SpringBootTest
@ActiveProfiles("test")
class CrecheconectaCamadasApplicationTests {

    @Test
    void contextLoads() {
    }

}
