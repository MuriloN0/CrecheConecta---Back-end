package com.crecheconecta;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class CrecheconectaCamadasApplication {

    public static void main(String[] args) {
        SpringApplication.run(CrecheconectaCamadasApplication.class, args);
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
