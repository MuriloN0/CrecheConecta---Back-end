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
                        .ignoringRequestMatchers("/api/auth/login", "/api/auth/confirmar-login", "/api/auth/logout", "/api/auth/esqueci-senha", "/api/auth/confirmar-recuperacao", "/api/auth/redefinir-senha",
                                "/api/termo/aceite", "/api/alunos/*/fichas-saude", "/api/alunos/*/fichas-saude/*", "/api/atividades", "/api/atividades/**", "/api/professores/convites", "/api/cadastro/professores/consultar-convite",
                                "/api/cadastro/professores/concluir", "/api/professores/*/aprovar", "/api/professores/*/rejeitar", "/api/professores/*", "/api/professores/*/inativar", "/api/alunos", "/api/alunos/*",
                                "/api/alunos/*/inativar", "/api/alunos/*/responsaveis", "/api/alunos/*/responsaveis/*/encerrar", "/api/turmas", "/api/turmas/*", "/api/turmas/*/inativar"))

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
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/confirmar-login", "/api/auth/esqueci-senha", "/api/auth/confirmar-recuperacao", "/api/auth/redefinir-senha")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/professores/convites").hasRole("DIRECAO")
                        .requestMatchers(HttpMethod.POST, "/api/cadastro/professores/consultar-convite", "/api/cadastro/professores/concluir").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/professores/pendentes").hasRole("DIRECAO")
                        .requestMatchers(HttpMethod.POST, "/api/professores/*/aprovar", "/api/professores/*/rejeitar").hasRole("DIRECAO")
                        .requestMatchers(HttpMethod.GET, "/api/professores", "/api/professores/*").hasRole("DIRECAO")
                        .requestMatchers(HttpMethod.PUT, "/api/professores/*").hasRole("DIRECAO")
                        .requestMatchers(HttpMethod.POST, "/api/professores/*/inativar").hasRole("DIRECAO")
                        .requestMatchers(HttpMethod.GET, "/api/alunos", "/api/alunos/*").hasRole("DIRECAO")
                        .requestMatchers(HttpMethod.POST, "/api/alunos", "/api/alunos/*/inativar").hasRole("DIRECAO")
                        .requestMatchers(HttpMethod.PUT, "/api/alunos/*").hasRole("DIRECAO")
                        .requestMatchers(HttpMethod.GET, "/api/alunos/*/responsaveis").hasRole("DIRECAO")
                        .requestMatchers(HttpMethod.POST, "/api/alunos/*/responsaveis", "/api/alunos/*/responsaveis/*/encerrar").hasRole("DIRECAO")
                        .requestMatchers(HttpMethod.GET, "/api/turmas", "/api/turmas/*").hasRole("DIRECAO")
                        .requestMatchers(HttpMethod.POST, "/api/turmas", "/api/turmas/*/inativar").hasRole("DIRECAO")
                        .requestMatchers(HttpMethod.PUT, "/api/turmas/*").hasRole("DIRECAO")

                        // Sessão e termos do usuário.
                        .requestMatchers(HttpMethod.GET, "/api/auth/me", "/api/termo/status")
                        .authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/auth/logout", "/api/termo/aceite")
                        .authenticated()

                        // Fichas de saúde: acesso restrito à direção.
                        .requestMatchers(HttpMethod.GET, "/api/alunos/*/fichas-saude", "/api/alunos/*/fichas-saude/*").hasRole("DIRECAO")
                        .requestMatchers(HttpMethod.POST, "/api/alunos/*/fichas-saude").hasRole("DIRECAO").requestMatchers(HttpMethod.PUT, "/api/alunos/*/fichas-saude/*").hasRole("DIRECAO")
                        .requestMatchers(HttpMethod.DELETE, "/api/alunos/*/fichas-saude/*").hasRole("DIRECAO")
                        .requestMatchers("/api/atividades/**").authenticated()
                        .anyRequest()
                        .denyAll()
                )
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> RespostaErroSeguranca.naoAutenticado(response))
                        .accessDeniedHandler((request, response, exception) -> RespostaErroSeguranca.acessoNegado(response))
                )
                .addFilterBefore(filtro, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}