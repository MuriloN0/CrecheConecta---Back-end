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
                                "/api/auth/logout"
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
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/login",
                                "/api/auth/confirmar-login"
                        )
                        .permitAll()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/auth/me"
                        )
                        .authenticated()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/logout"
                        )
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