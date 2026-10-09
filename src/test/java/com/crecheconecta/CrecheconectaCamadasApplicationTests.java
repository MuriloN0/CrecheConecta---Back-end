package com.crecheconecta;

import org.junit.jupiter.api.Test;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "saude.criptografia.chave=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "app.email.resend.api-key=test-api-key",
        "app.email.resend.remetente=test@example.com",
        "app.seguranca.hmac-chave-base64=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "spring.datasource.url=jdbc:postgresql://localhost:5433/CrecheConecta",
        "spring.datasource.username=postgres",
        "spring.datasource.password=admin",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.autoconfigure.exclude=org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"
})
class CrecheconectaCamadasApplicationTests {

    @Test
    void contextLoads() {
    }

}
