package com.crecheconecta.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class RefreshSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain refreshSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/api/auth/refresh")
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/auth/refresh"))
                .sessionManagement(session -> session.sessionCreationPolicy(
                        SessionCreationPolicy.STATELESS
                ))
                .requestCache(cache -> cache.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/refresh").permitAll()
                        .anyRequest().denyAll()
                )
                .build();
    }
}