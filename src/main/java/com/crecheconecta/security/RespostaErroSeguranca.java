package com.crecheconecta.security;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public final class RespostaErroSeguranca {

    private RespostaErroSeguranca() {
    }

    public static void naoAutenticado(
            HttpServletResponse response
    ) throws IOException {
        response.setHeader("WWW-Authenticate", "Bearer");

        escrever(response, 401, """
                {
                  "type": "about:blank",
                  "title": "Unauthorized",
                  "status": 401,
                  "detail": "Autenticação necessária ou sessão inválida.",
                  "codigo": "NAO_AUTENTICADO"
                }
                """);
    }

    public static void acessoNegado(
            HttpServletResponse response
    ) throws IOException {
        escrever(response, 403, """
                {
                  "type": "about:blank",
                  "title": "Forbidden",
                  "status": 403,
                  "detail": "Você não possui permissão para esta operação.",
                  "codigo": "ACESSO_NEGADO"
                }
                """);
    }

    private static void escrever(
            HttpServletResponse response,
            int status,
            String corpo
    ) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write(corpo);
    }

}
