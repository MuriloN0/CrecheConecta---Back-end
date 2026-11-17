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
public class PerfilSecurityConfig {

    private static final String PERFIL = "/api/perfil";
    private static final String TELEFONE = "/api/perfil/telefone";
    private static final String SOLICITAR_EMAIL = "/api/perfil/email/solicitar";
    private static final String CONFIRMAR_EMAIL = "/api/perfil/email/confirmar";
    private static final String INATIVAR = "/api/perfil/inativar";

    @Bean
    @Order(3)
    public SecurityFilterChain perfilSecurityFilterChain(HttpSecurity http, SessaoService sessoes) throws Exception {
        return http
                .securityMatcher(PERFIL, "/api/perfil/**")
                .csrf(csrf -> csrf.ignoringRequestMatchers(TELEFONE, SOLICITAR_EMAIL, CONFIRMAR_EMAIL, INATIVAR))
                .sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, PERFIL).hasAnyRole("PROFESSOR", "RESPONSAVEL")
                        .requestMatchers(HttpMethod.PUT, TELEFONE).hasAnyRole("PROFESSOR", "RESPONSAVEL")
                        .requestMatchers(HttpMethod.POST, SOLICITAR_EMAIL, CONFIRMAR_EMAIL, INATIVAR)
                        .hasRole("RESPONSAVEL")
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