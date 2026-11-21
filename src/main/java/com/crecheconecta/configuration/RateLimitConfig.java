package com.crecheconecta.configuration;

import com.crecheconecta.service.RateLimitService;
import com.crecheconecta.security.ClienteIpConfiavel;
import com.crecheconecta.security.UsuarioAutenticado;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Set;

import static com.crecheconecta.service.RateLimitService.Politica.*;

@Configuration
@ConditionalOnProperty(
        prefix = "app.rate-limit",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class RateLimitConfig implements WebMvcConfigurer, HandlerInterceptor {

    private static final Set<String> AUTENTICACAO = Set.of(
            "/api/auth/login",
            "/api/auth/confirmar-login",
            "/api/auth/esqueci-senha",
            "/api/auth/confirmar-recuperacao",
            "/api/auth/redefinir-senha"
    );

    private static final Set<String> SENHA = Set.of(
            "/api/perfil/email/solicitar",
            "/api/perfil/inativar"
    );

    private final RateLimitService limites;

    public RateLimitConfig(RateLimitService limites) {
        this.limites = limites;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this)
                .addPathPatterns("/api/**")
                .order(100);
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) {
        String caminho = String.valueOf(
                request.getAttribute(
                        HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE
                )
        );

        String metodo = request.getMethod();

        if ("POST".equals(metodo) && AUTENTICACAO.contains(caminho)) {
            Object rede = request.getAttribute(
                    ClienteIpConfiavel.ATRIBUTO
            );

            if (!(rede instanceof String identidadeIp)
                    || identidadeIp.isBlank()) {
                throw new IllegalStateException(
                        "IP da autenticação não validado."
                );
            }

            limites.verificar(
                    AUTENTICACAO_GLOBAL,
                    "autenticacao"
            );

            limites.verificar(
                    AUTENTICACAO_IP,
                    identidadeIp
            );

            limites.verificar(
                    AUTENTICACAO_IP_HORA,
                    identidadeIp
            );

            return true;
        }

        var autenticacao = SecurityContextHolder.getContext()
                .getAuthentication();

        if (!caminho.startsWith("/api/")
                || autenticacao == null
                || !autenticacao.isAuthenticated()
                || !(autenticacao.getPrincipal()
                instanceof UsuarioAutenticado usuario)) {
            return true;
        }

        String identidade = usuario.usuarioId().toString();

        limites.verificar(API_USUARIO, identidade);

        if ("GET".equals(metodo)) {
            String recurso = identidade + "\n" + caminho;
            var politica = politicaLeitura(caminho);

            if (politica != null) {
                limites.verificar(politica, recurso);
            }
        }

        if (Set.of("POST", "PUT", "PATCH", "DELETE").contains(metodo)) {
            limites.verificar(ESCRITA_USUARIO, identidade);
        }

        if ("POST".equals(metodo) && SENHA.contains(caminho)) {
            limites.verificar(SENHA_PERFIL, identidade);
        }

        if ("POST".equals(metodo)
                && "/api/perfil/email/confirmar".equals(caminho)) {
            limites.verificar(
                    CONFIRMACAO_EMAIL_PERFIL,
                    identidade
            );
        }

        if ("GET".equals(metodo)
                && "/api/meus-dados".equals(caminho)) {
            limites.verificar(EXPORTACAO_DADOS, identidade);
        }

        if ("POST".equals(metodo)
                && caminho.startsWith("/api/chat/contatos/")
                && caminho.endsWith("/mensagens")) {
            limites.verificar(CHAT_ENVIO, identidade);
        }

        return true;
    }

    private RateLimitService.Politica politicaLeitura(String caminho) {
        if ("/api/comunicados".equals(caminho)) {
            return LEITURA_MURAL;
        }

        if ("/api/turmas/frequencia".equals(caminho)) {
            return LEITURA_FREQUENCIA;
        }

        if (caminho.startsWith("/api/chat/")
                || Set.of(
                "/api/notificacoes/pendencias",
                "/api/termo/status",
                "/api/auth/me"
        ).contains(caminho)) {
            return null;
        }

        return LEITURA_RECURSO;
    }
}