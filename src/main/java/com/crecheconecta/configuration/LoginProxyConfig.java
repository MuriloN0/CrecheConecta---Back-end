package com.crecheconecta.configuration;

import com.crecheconecta.security.ClienteIpConfiavel;
import com.crecheconecta.security.RespostaErroSeguranca;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;

@Configuration
public class LoginProxyConfig implements WebMvcConfigurer, HandlerInterceptor {

    private final ClienteIpConfiavel clienteIp;

    public LoginProxyConfig(ClienteIpConfiavel clienteIp) {

        this.clienteIp = clienteIp;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        registry.addInterceptor(this)
                .addPathPatterns(ClienteIpConfiavel.ROTAS.toArray(String[]::new))
                .order(-100);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {

        if (!"POST".equals(request.getMethod())) {

            return true;
        }

        try {

            request.setAttribute(ClienteIpConfiavel.ATRIBUTO, clienteIp.resolver(request));

            return true;

        } catch (IllegalArgumentException exception) {

            RespostaErroSeguranca.acessoNegado(response);

            return false;
        }
    }
}