package com.crecheconecta;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class CrecheconectaCamadasApplicationTests {

    @Autowired
    private ApplicationContext contexto;

    @Test
    void contextLoads() {

        assertTrue(contexto.getBeansOfType(UserDetailsService.class).isEmpty());
        assertFalse(contexto.getBeansOfType(SecurityFilterChain.class).isEmpty());
        assertNotNull(contexto.getBean(PasswordEncoder.class));
    }

}