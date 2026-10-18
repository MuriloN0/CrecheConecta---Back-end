package com.crecheconecta.configuration;

import com.crecheconecta.security.AutenticacaoTokenFilter;
import com.crecheconecta.security.RespostaErroSeguranca;
import com.crecheconecta.service.SessaoService;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SessaoService sessaoService
    ) throws Exception {

        var filtro = new AutenticacaoTokenFilter(sessaoService);

        http
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers(
                                "/api/auth/login",
                                "/api/auth/confirmar-login",
                                "/api/auth/logout",
                                "/api/auth/esqueci-senha",
                                "/api/auth/confirmar-recuperacao",
                                "/api/auth/redefinir-senha",
                                "/api/termo/aceite",
                                "/api/alunos/*/fichas-saude",
                                "/api/alunos/*/fichas-saude/*",
                                "/api/alunos/*/observacoes",
                                "/api/alunos/*/observacoes/*",
                                "/api/atividades",
                                "/api/atividades/**"
                        )
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

                        // Início e confirmação dos fluxos de autenticação.
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/login",
                                "/api/auth/confirmar-login",
                                "/api/auth/esqueci-senha",
                                "/api/auth/confirmar-recuperacao",
                                "/api/auth/redefinir-senha"
                        )
                        .permitAll()

                        // Sessão e termos do usuário autenticado.
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/auth/me",
                                "/api/termo/status"
                        )
                        .authenticated()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/logout",
                                "/api/termo/aceite"
                        )
                        .authenticated()

                        // Informacoes relevantes (ficha de saude): gerenciadas pelos pais.
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/alunos/*/fichas-saude",
                                "/api/alunos/*/fichas-saude/*"
                        )
                        .hasRole("PAIS")
                        // Observacoes do filho: acesso dos pais (perfil PAIS).
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/alunos/*/observacoes",
                                "/api/alunos/*/observacoes/*"
                        )
                        .hasRole("PAIS")
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/alunos/*/observacoes"
                        )
                        .hasRole("PAIS")
                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/alunos/*/observacoes/*"
                        )
                        .hasRole("PAIS")
                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/alunos/*/observacoes/*"
                        )
                        .hasRole("PAIS")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/alunos/*/fichas-saude"
                        )
                        .hasRole("PAIS")
                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/alunos/*/fichas-saude/*"
                        )
                        .hasRole("PAIS")
                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/alunos/*/fichas-saude/*"
                        )
                        .hasRole("PAIS")

                        .requestMatchers("/api/atividades/**")
                        .authenticated()

                        .anyRequest()
                        .denyAll()
                )
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(
                                (request, response, exception) ->
                                        RespostaErroSeguranca
                                                .naoAutenticado(response)
                        )
                        .accessDeniedHandler(
                                (request, response, exception) ->
                                        RespostaErroSeguranca
                                                .acessoNegado(response)
                        )
                )
                .addFilterBefore(
                        filtro,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}