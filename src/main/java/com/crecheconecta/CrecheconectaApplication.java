package com.crecheconecta;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class CrecheconectaApplication {

    public static void main(String[] args) {

        SpringApplication.run(CrecheconectaApplication.class, args);
    }

    @Bean
    public Clock clock() {

        return Clock.systemUTC();
    }
}