package com.crecheconecta.configuration;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import jakarta.servlet.DispatcherType;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {

        http
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/api/auth/login")
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR)
                        .permitAll()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/login"
                        )
                        .permitAll()
                        .anyRequest()
                        .denyAll()
                )
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, ex) -> {
                            response.setStatus(401);
                            response.setContentType(
                                    "application/problem+json"
                            );
                            response.setCharacterEncoding("UTF-8");

                            response.getWriter().write("""
                                    {
                                      "type": "about:blank",
                                      "title": "Unauthorized",
                                      "status": 401,
                                      "detail": "Autenticação necessária.",
                                      "codigo": "NAO_AUTENTICADO"
                                    }
                                    """);
                        })
                        .accessDeniedHandler((request, response, ex) -> {
                            response.setStatus(403);
                            response.setContentType(
                                    "application/problem+json"
                            );
                            response.setCharacterEncoding("UTF-8");

                            response.getWriter().write("""
                                    {
                                      "type": "about:blank",
                                      "title": "Forbidden",
                                      "status": 403,
                                      "detail": "Acesso negado.",
                                      "codigo": "ACESSO_NEGADO"
                                    }
                                    """);
                        })
                );

        return http.build();
    }

}
