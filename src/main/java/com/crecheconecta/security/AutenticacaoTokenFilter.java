package com.crecheconecta.security;

import com.crecheconecta.service.SessaoService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class AutenticacaoTokenFilter extends OncePerRequestFilter {

    private final SessaoService sessaoService;

    public AutenticacaoTokenFilter(SessaoService sessaoService) {
        this.sessaoService = sessaoService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String caminho = request.getServletPath();

        return "POST".equals(request.getMethod())
                && (
                "/api/auth/login".equals(caminho)
                        || "/api/auth/confirmar-login".equals(caminho)
        );
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        var cabecalhos = request.getHeaders(
                HttpHeaders.AUTHORIZATION
        );

        if (!cabecalhos.hasMoreElements()) {
            filterChain.doFilter(request, response);
            return;
        }

        String authorization = cabecalhos.nextElement();

        if (cabecalhos.hasMoreElements()
                || !authorization.regionMatches(
                true, 0, "Bearer ", 0, 7
        )) {
            rejeitar(response);
            return;
        }

        String token = authorization.substring(7);

        var usuario = sessaoService.autenticar(token).orElse(null);

        if (usuario == null) {
            rejeitar(response);
            return;
        }

        var permissoes = List.of(
                new SimpleGrantedAuthority(
                        "ROLE_" + usuario.perfil().name()
                )
        );

        var autenticacao =
                new UsernamePasswordAuthenticationToken(
                        usuario,
                        null,
                        permissoes
                );

        var contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(autenticacao);
        SecurityContextHolder.setContext(contexto);

        filterChain.doFilter(request, response);
    }

    private void rejeitar(
            HttpServletResponse response
    ) throws IOException {
        SecurityContextHolder.clearContext();
        RespostaErroSeguranca.naoAutenticado(response);
    }

}
