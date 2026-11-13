package com.crecheconecta;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// Sobe o contexto completo com o perfil "test" (H2 em memoria), sem depender de PostgreSQL local
@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
class CrecheconectaCamadasApplicationTests {

    @Test
    void contextLoads() {
    }

}
