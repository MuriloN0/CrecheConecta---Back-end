package com.crecheconecta.configuration;

import com.crecheconecta.entity.Perfil;
import com.crecheconecta.security.UsuarioAutenticado;
import com.crecheconecta.service.TermoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;

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
                    HttpServletRequest request,
                    HttpServletResponse response,
                    Object handler
            ) throws IOException {
                var autenticacao = SecurityContextHolder.getContext().getAuthentication();

                if (autenticacao == null
                        || !(autenticacao.getPrincipal() instanceof UsuarioAutenticado usuario)
                        || usuario.perfil() != Perfil.RESPONSAVEL
                        || !termos.precisaAceitar(usuario.usuarioId())) {
                    return true;
                }

                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/problem+json");
                response.setCharacterEncoding("UTF-8");
                response.setHeader("Cache-Control", "no-store");
                response.getWriter().write("""
                        {
                          "status": 403,
                          "title": "Aceite obrigatório",
                          "detail": "Aceite os Termos de Uso e a Política de Privacidade vigentes.",
                          "codigo": "ACEITE_OBRIGATORIO"
                        }
                        """);
                return false;
            }
        }).addPathPatterns("/api/**").excludePathPatterns(
                "/api/auth/me",
                "/api/auth/logout",
                "/api/termo/status",
                "/api/termo/aceite",
                "/api/cadastro/**"
        );
    }
}