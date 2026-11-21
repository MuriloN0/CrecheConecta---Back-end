package com.crecheconecta.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;

class ClienteIpConfiavelTest {

    private static final String SEGREDO = "a".repeat(64);

    private final ClienteIpConfiavel cliente =
            new ClienteIpConfiavel(true, SEGREDO);

    private MockHttpServletRequest requisicao(String ip) {
        var request = new MockHttpServletRequest();

        request.addHeader(
                "X-CrecheConecta-Login-Proxy",
                SEGREDO
        );

        request.addHeader(
                "X-CrecheConecta-Client-IP",
                ip
        );

        return request;
    }

    @Test
    void validaIpv4EIgnoraCabecalhosNaoAutenticadosDeEncaminhamento() {
        var request = requisicao("203.0.113.10");

        request.addHeader("X-Forwarded-For", "198.51.100.20");
        request.addHeader("X-Real-IP", "198.51.100.21");

        assertEquals(
                "ipv4:203.0.113.10",
                cliente.resolver(request)
        );
    }

    @Test
    void rejeitaAcessoDiretoSemSegredo() {
        var request = new MockHttpServletRequest();

        request.addHeader(
                "X-CrecheConecta-Client-IP",
                "203.0.113.10"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> cliente.resolver(request)
        );
    }

    @Test
    void rejeitaSegredoErradoEIpAusente() {
        var errado = requisicao("203.0.113.10");

        errado.removeHeader("X-CrecheConecta-Login-Proxy");

        errado.addHeader(
                "X-CrecheConecta-Login-Proxy",
                "b".repeat(64)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> cliente.resolver(errado)
        );

        var semIp = new MockHttpServletRequest();

        semIp.addHeader(
                "X-CrecheConecta-Login-Proxy",
                SEGREDO
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> cliente.resolver(semIp)
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "X-CrecheConecta-Client-IP",
            "X-CrecheConecta-Login-Proxy"
    })
    void rejeitaCabecalhoDuplicado(String nome) {
        var request = requisicao("203.0.113.10");

        request.addHeader(
                nome,
                nome.endsWith("IP") ? "198.51.100.20" : SEGREDO
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> cliente.resolver(request)
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "localhost",
            "example.com",
            "256.1.1.1",
            "127.1",
            "2130706433",
            "203.0.113.10,198.51.100.20",
            "203.0.113.10:8080",
            "203.000.113.10",
            "[2001:db8::1]",
            "fe80::1%eth0",
            "2001:db8::zz",
            "dead.beef"
    })
    void rejeitaValoresQueNaoSaoUmUnicoIpLiteral(String ip) {
        assertThrows(
                IllegalArgumentException.class,
                () -> cliente.resolver(requisicao(ip))
        );
    }

    @Test
    void agrupaIpv6DaMesmaRedeESeparaOutraRede() {
        String rede = cliente.resolver(
                requisicao("2001:db8:abcd:1234::1")
        );

        assertEquals(
                rede,
                cliente.resolver(requisicao(
                        "2001:0db8:abcd:1234:ffff:aaaa:bbbb:cccc"
                ))
        );

        assertNotEquals(
                rede,
                cliente.resolver(requisicao(
                        "2001:db8:abcd:1235::1"
                ))
        );
    }

    @Test
    void ipv4MapeadoEmIpv6UsaOMesmoContador() {
        assertEquals(
                cliente.resolver(requisicao("203.0.113.10")),
                cliente.resolver(requisicao("::ffff:203.0.113.10"))
        );
    }

    @Test
    void localUsaEnderecoDaConexaoEIgnoraCabecalhos() {
        var request = requisicao("203.0.113.10");

        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "198.51.100.20");

        assertEquals(
                "ipv4:127.0.0.1",
                new ClienteIpConfiavel(false, "").resolver(request)
        );
    }

    @Test
    void exigeSegredoValidoQuandoProxyObrigatorio() {
        assertThrows(
                IllegalStateException.class,
                () -> new ClienteIpConfiavel(true, "invalido")
        );
    }
}