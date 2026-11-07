package com.crecheconecta.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {

    @Bean
    UrlBasedCorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") String origensConfiguradas
    ) {
        List<String> origens = Arrays.stream(origensConfiguradas.split(","))
                .map(String::trim)
                .filter(origem -> !origem.isEmpty())
                .distinct()
                .toList();

        if (origens.isEmpty() || origens.stream().anyMatch(origem -> origem.contains("*"))) {
            throw new IllegalArgumentException(
                    "Configure pelo menos uma origem CORS explícita."
            );
        }

        CorsConfiguration configuracao = new CorsConfiguration();
        configuracao.setAllowedOrigins(origens);
        configuracao.setAllowedMethods(
                List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        );
        configuracao.setAllowedHeaders(
                List.of("Authorization", "Content-Type", "turmaUUID")
        );
        configuracao.setExposedHeaders(List.of("Location"));
        configuracao.setAllowCredentials(false);
        configuracao.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuracao);
        return source;
    }
}