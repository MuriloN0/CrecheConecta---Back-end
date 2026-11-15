package com.crecheconecta.configuration;

import com.crecheconecta.entity.Perfil;
import com.crecheconecta.exception.TermoPendenteException;
import com.crecheconecta.security.UsuarioAutenticado;
import com.crecheconecta.service.TermoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AceiteObrigatorioConfig implements WebMvcConfigurer {

    private final TermoService termos;

    public AceiteObrigatorioConfig(TermoService termos) {
        this.termos = termos;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(
                    HttpServletRequest request, HttpServletResponse response, Object handler
            ) {
                var autenticacao = SecurityContextHolder.getContext().getAuthentication();
                if (autenticacao != null
                        && autenticacao.getPrincipal() instanceof UsuarioAutenticado usuario
                        && usuario.perfil() == Perfil.RESPONSAVEL
                        && termos.precisaAceitar(usuario.usuarioId())) {
                    throw new TermoPendenteException();
                }

                return true;
            }
        }).addPathPatterns("/api/**").excludePathPatterns(
                "/api/auth/me",
                "/api/auth/logout",
                "/api/termo/status",
                "/api/termo/aceite",
                "/api/cadastro/**",
                "/api/meus-dados"
        );
    }
}
