package com.crecheconecta.configuration;

import com.crecheconecta.security.AutenticacaoTokenFilter;
import com.crecheconecta.security.RespostaErroSeguranca;
import com.crecheconecta.service.SessaoService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class ChatModeracaoSecurityConfig {

    private static final String ROTA = "/api/chat/moderacao/*";

    @Bean
    @Order(2)
    public SecurityFilterChain chatModeracaoSecurityFilterChain(
            HttpSecurity http,
            SessaoService sessoes
    ) throws Exception {
        return http
                .securityMatcher(ROTA)
                .csrf(csrf -> csrf.ignoringRequestMatchers(ROTA))
                .sessionManagement(sessoesHttp -> sessoesHttp.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, ROTA).hasRole("DIRECAO")
                        .requestMatchers(HttpMethod.PUT, ROTA).hasRole("DIRECAO")
                        .requestMatchers(HttpMethod.DELETE, ROTA).hasRole("DIRECAO")
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) ->
                                RespostaErroSeguranca.naoAutenticado(response))
                        .accessDeniedHandler((request, response, exception) ->
                                RespostaErroSeguranca.acessoNegado(response)))
                .addFilterBefore(new AutenticacaoTokenFilter(sessoes), UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}