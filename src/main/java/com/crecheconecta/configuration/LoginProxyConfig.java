package com.crecheconecta.configuration;

import com.crecheconecta.security.RespostaErroSeguranca;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Configuration
public class LoginProxyConfig implements WebMvcConfigurer {

    private static final String CABECALHO_SEGREDO = "X-CrecheConecta-Login-Proxy";

    private final boolean proxyObrigatorio;
    private final byte[] segredoEsperado;

    public LoginProxyConfig(@Value("${app.auth.login-proxy-required:false}") boolean proxyObrigatorio, @Value("${app.auth.login-proxy-secret:}") String segredo) {

        if (proxyObrigatorio && !segredo.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalStateException("APP_AUTH_LOGIN_PROXY_SECRET deve conter 64 caracteres hexadecimais.");
        }

        this.proxyObrigatorio = proxyObrigatorio;
        this.segredoEsperado = segredo.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        if (!proxyObrigatorio) {

            return;
        }

        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {

                if (!"POST".equals(request.getMethod())) {

                    return true;
                }

                var cabecalhos = request.getHeaders(CABECALHO_SEGREDO);
                String recebido = cabecalhos.hasMoreElements() ? cabecalhos.nextElement() : "";

                if (!cabecalhos.hasMoreElements() && MessageDigest.isEqual(segredoEsperado, recebido.getBytes(StandardCharsets.UTF_8))) {

                    return true;
                }

                RespostaErroSeguranca.acessoNegado(response);
                return false;
            }
        }).addPathPatterns("/api/auth/login");
    }
}