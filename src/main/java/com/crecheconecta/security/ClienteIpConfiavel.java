package com.crecheconecta.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Set;

@Component
public class ClienteIpConfiavel {

    public static final String ATRIBUTO =
            ClienteIpConfiavel.class.getName() + ".rede";

    public static final Set<String> ROTAS = Set.of(
            "/api/auth/login",
            "/api/auth/confirmar-login",
            "/api/auth/esqueci-senha",
            "/api/auth/confirmar-recuperacao",
            "/api/auth/redefinir-senha"
    );

    private final boolean proxyObrigatorio;
    private final byte[] segredo;

    public ClienteIpConfiavel(@Value("${app.auth.login-proxy-required:false}") boolean proxyObrigatorio, @Value("${app.auth.login-proxy-secret:}") String segredo) {

        if (proxyObrigatorio && !segredo.matches("[0-9a-fA-F]{64}")) {

            throw new IllegalStateException("segredo deve conter 64 caracteres hexadecimais.");
        }

        this.proxyObrigatorio = proxyObrigatorio;
        this.segredo = segredo.getBytes(StandardCharsets.UTF_8);
    }

    public String resolver(HttpServletRequest request) {

        if (!proxyObrigatorio) {

            return normalizar(request.getRemoteAddr());
        }

        String recebido = unico(request, "X-CrecheConecta-Login-Proxy");

        if (!MessageDigest.isEqual(segredo, recebido.getBytes(StandardCharsets.UTF_8))) {

            throw new IllegalArgumentException("Proxy não autenticado.");
        }

        return normalizar(unico(request, "X-CrecheConecta-Client-IP"));
    }

    private String unico(HttpServletRequest request, String nome) {

        var valores = request.getHeaders(nome);

        if (valores == null || !valores.hasMoreElements()) {

            throw new IllegalArgumentException(
                    "Cabeçalho obrigatório ausente."
            );
        }

        String valor = valores.nextElement();

        if (valores.hasMoreElements()) {
            throw new IllegalArgumentException("Cabeçalho duplicado.");
        }

        return valor;
    }

    private String normalizar(String ip) {

        if (ip == null || ip.length() > 45) {

            throw new IllegalArgumentException("IP inválido.");
        }

        try {
            InetAddress endereco;

            if (ip.contains(":") && ip.matches("[0-9a-fA-F:.]+")) {

                endereco = InetAddress.getByName(ip);

            } else if (ip.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")) {

                String[] partes = ip.split("\\.");

                byte[] bytes = new byte[4];

                for (int i = 0; i < 4; i++) {

                    int numero = Integer.parseInt(partes[i]);

                    if (numero > 255 || (partes[i].length() > 1 && partes[i].startsWith("0"))) {

                        throw new IllegalArgumentException("IP inválido.");
                    }

                    bytes[i] = (byte) numero;
                }

                endereco = InetAddress.getByAddress(bytes);
            } else {
                throw new IllegalArgumentException("IP inválido.");
            }

            byte[] bytes = endereco.getAddress();

            if (bytes.length == 16) {
                return "ipv6:" + HexFormat.of().formatHex(Arrays.copyOf(bytes, 8)) + "/64";
            }

            return "ipv4:" + endereco.getHostAddress();
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException("IP inválido.");
        }
    }
}