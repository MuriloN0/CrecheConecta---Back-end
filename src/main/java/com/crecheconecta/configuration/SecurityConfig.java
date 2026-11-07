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
                                "/api/alunos/*/inativar", "/api/alunos/*/responsaveis", "/api/alunos/*/responsaveis/*/encerrar", "/api/turmas", "/api/turmas/*", "/api/turmas/*/inativar", "/api/turmas/*/matriculas",
                                "/api/turmas/*/matriculas/*/encerrar", "/api/turmas/*/professores", "/api/turmas/*/professores/*/encerrar", "/api/direcao/me/inativar", "/api/direcao/convites", "/api/cadastro/direcao/consultar-convite",
                                "/api/cadastro/direcao/concluir", "/api/direcao/me", "/api/comunicados", "/api/comunicados/*", "/api/comunicados/*/inativar", "/api/turmas/*/chamadas/*/alunos/*",
                                "/api/alunos/*/observacoes", "/api/alunos/*/observacoes/*", "/api/turmas/*/malas", "/api/turmas/*/malas/*", "/api/meus-filhos/*/mala/*/itens/*"))

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
                        .requestMatchers(HttpMethod.GET, "/api/turmas/*/matriculas").hasRole("DIRECAO")
                        .requestMatchers(HttpMethod.POST, "/api/turmas/*/matriculas", "/api/turmas/*/matriculas/*/encerrar").hasRole("DIRECAO")
                        .requestMatchers(HttpMethod.GET, "/api/turmas/*/professores").hasRole("DIRECAO")
                        .requestMatchers(HttpMethod.POST, "/api/turmas/*/professores", "/api/turmas/*/professores/*/encerrar").hasRole("DIRECAO")
                        .requestMatchers(HttpMethod.GET, "/api/comunicados", "/api/comunicados/*").hasAnyRole("DIRECAO", "PROFESSOR", "RESPONSAVEL")
                        .requestMatchers(HttpMethod.POST, "/api/comunicados", "/api/comunicados/*/inativar").hasRole("DIRECAO")
                        .requestMatchers(HttpMethod.PUT, "/api/comunicados/*").hasRole("DIRECAO")
                        .requestMatchers(HttpMethod.GET, "/api/turmas/*/chamadas/*").hasRole("PROFESSOR")
                        .requestMatchers(HttpMethod.PUT, "/api/turmas/*/chamadas/*/alunos/*").hasRole("PROFESSOR")
                        .requestMatchers(HttpMethod.GET, "/api/turmas/*/malas", "/api/turmas/*/malas/*", "/api/turmas/*/malas/*/progresso").hasRole("PROFESSOR")
                        .requestMatchers(HttpMethod.POST, "/api/turmas/*/malas").hasRole("PROFESSOR")
                        .requestMatchers(HttpMethod.PUT, "/api/turmas/*/malas/*").hasRole("PROFESSOR")
                        .requestMatchers(HttpMethod.DELETE, "/api/turmas/*/malas/*").hasRole("PROFESSOR")
                        .requestMatchers(HttpMethod.GET, "/api/direcao", "/api/direcao/me").hasRole("DIRECAO")
                        .requestMatchers(HttpMethod.PUT, "/api/direcao/me").hasRole("DIRECAO")
                        .requestMatchers(HttpMethod.POST, "/api/direcao/convites", "/api/direcao/me/inativar").hasRole("DIRECAO")
                        .requestMatchers(HttpMethod.POST, "/api/cadastro/direcao/consultar-convite", "/api/cadastro/direcao/concluir").permitAll()

                        // Sessão e termos do usuário.
                        .requestMatchers(HttpMethod.GET, "/api/auth/me", "/api/termo/status")
                        .authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/auth/logout", "/api/termo/aceite")
                        .authenticated()

                        // Informações relevantes (fichas de saúde): o responsável escreve; responsável, direção e professor leem.
                        // A regra fina (filho do pai / professor da turma) fica no FichaSaudeService.
                        .requestMatchers(HttpMethod.GET, "/api/alunos/*/fichas-saude", "/api/alunos/*/fichas-saude/*").hasAnyRole("RESPONSAVEL", "DIRECAO", "PROFESSOR")
                        .requestMatchers(HttpMethod.POST, "/api/alunos/*/fichas-saude").hasRole("RESPONSAVEL")
                        .requestMatchers(HttpMethod.PUT, "/api/alunos/*/fichas-saude/*").hasRole("RESPONSAVEL")
                        .requestMatchers(HttpMethod.DELETE, "/api/alunos/*/fichas-saude/*").hasRole("RESPONSAVEL")

                        // Observacoes do filho: acesso dos pais (perfil RESPONSAVEL).
                        .requestMatchers(HttpMethod.GET, "/api/alunos/*/observacoes", "/api/alunos/*/observacoes/*").hasRole("RESPONSAVEL")
                        .requestMatchers(HttpMethod.POST, "/api/alunos/*/observacoes").hasRole("RESPONSAVEL")
                        .requestMatchers(HttpMethod.PUT, "/api/alunos/*/observacoes/*").hasRole("RESPONSAVEL")
                        .requestMatchers(HttpMethod.DELETE, "/api/alunos/*/observacoes/*").hasRole("RESPONSAVEL")
                        // Dados do filho (Perfil da Criança): só leitura, só pais (perfil RESPONSAVEL).
                        .requestMatchers(HttpMethod.GET, "/api/meus-filhos", "/api/meus-filhos/*").hasRole("RESPONSAVEL")
                        // Atividades para casa do filho: só leitura, só pais.
                        .requestMatchers(HttpMethod.GET, "/api/meus-filhos/*/atividades-casa").hasRole("RESPONSAVEL")
                        // Faltas e presenças (chamada) do filho: só leitura, só pais.
                        .requestMatchers(HttpMethod.GET, "/api/meus-filhos/*/chamada").hasRole("RESPONSAVEL")
                        // Diário de rotina do filho: só leitura, só pais.
                        .requestMatchers(HttpMethod.GET, "/api/meus-filhos/*/diario").hasRole("RESPONSAVEL")
                        // Mala virtual do filho: só pais.
                        .requestMatchers(HttpMethod.GET, "/api/meus-filhos/*/mala").hasRole("RESPONSAVEL")
                        .requestMatchers(HttpMethod.PUT, "/api/meus-filhos/*/mala/*/itens/*").hasRole("RESPONSAVEL")


                        .requestMatchers("/api/atividades/**").authenticated()
                        .anyRequest()
                        .denyAll()
                )
                .cors(org.springframework.security.config.Customizer.withDefaults())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> RespostaErroSeguranca.naoAutenticado(response))
                        .accessDeniedHandler((request, response, exception) -> RespostaErroSeguranca.acessoNegado(response))
                )
                .addFilterBefore(filtro, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
