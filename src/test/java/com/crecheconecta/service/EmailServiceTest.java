package com.crecheconecta.service;

import com.crecheconecta.entity.FinalidadeAcao;
import com.crecheconecta.exception.AutenticacaoException;
import com.crecheconecta.exception.ErroAutenticacao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static com.crecheconecta.service.DadosAutenticacaoTest.EMAIL;
import static com.crecheconecta.service.DadosAutenticacaoTest.TOKEN;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class EmailServiceTest {

    private MockRestServiceServer servidor;
    private EmailService service;
    private UUID referenciaId;

    @BeforeEach
    void preparar() {
        var builder = RestClient.builder()
                .baseUrl("https://email.example.test");

        servidor = MockRestServiceServer.bindTo(builder).build();

        service = new EmailService(
                builder.build(),
                "CrecheConecta <teste@example.test>"
        );

        referenciaId = UUID.randomUUID();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "login",
            "recuperacao",
            "senha",
            "professor",
            "direcao"
    })
    void deveEnviarCadaTipoDeEmailComIdempotencia(String tipo) {
        String prefixo = switch (tipo) {
            case "login", "recuperacao" -> "acao-verificacao/";
            case "senha" -> "senha-alterada/";
            case "professor" -> "convite-professor/";
            case "direcao" -> "convite-direcao/";
            default -> throw new IllegalArgumentException(tipo);
        };

        servidor.expect(requestTo("https://email.example.test/emails"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(
                        "Idempotency-Key",
                        prefixo + referenciaId
                ))
                .andRespond(withSuccess(
                        "{\"id\":\"email-teste\"}",
                        MediaType.APPLICATION_JSON
                ));

        assertEquals("email-teste", enviar(tipo));

        servidor.verify();
    }

    @Test
    void deveConverterFalhaDoProvedorEmErroControlado() {
        servidor.expect(requestTo("https://email.example.test/emails"))
                .andRespond(withServerError());

        var erro = assertThrows(
                AutenticacaoException.class,
                () -> enviar("login")
        );

        assertEquals(
                ErroAutenticacao.ENVIO_EMAIL_INDISPONIVEL,
                erro.getErro()
        );

        servidor.verify();
    }

    @Test
    void deveRejeitarRespostaSemIdentificador() {
        servidor.expect(requestTo("https://email.example.test/emails"))
                .andRespond(withSuccess(
                        "{}",
                        MediaType.APPLICATION_JSON
                ));

        assertThrows(
                AutenticacaoException.class,
                () -> enviar("login")
        );

        servidor.verify();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"abc", "12345", "1234567"})
    void deveRejeitarCodigoInvalidoAntesDoEnvio(String codigo) {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.enviarCodigo(
                        referenciaId,
                        EMAIL,
                        codigo,
                        FinalidadeAcao.LOGIN
                )
        );

        servidor.verify();
    }

    @Test
    void naoDeveEnviarTokenDeRedefinicaoComoCodigo() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.enviarCodigo(
                        referenciaId,
                        EMAIL,
                        "123456",
                        FinalidadeAcao.REDEFINICAO_SENHA
                )
        );

        servidor.verify();
    }

    @Test
    void deveRejeitarRemetenteNaoConfigurado() {
        assertThrows(
                IllegalStateException.class,
                () -> new EmailService(
                        mock(RestClient.class),
                        " "
                )
        );
    }

    private String enviar(String tipo) {
        return switch (tipo) {
            case "login" -> service.enviarCodigo(
                    referenciaId,
                    EMAIL,
                    "123456",
                    FinalidadeAcao.LOGIN
            );

            case "recuperacao" -> service.enviarCodigo(
                    referenciaId,
                    EMAIL,
                    "123456",
                    FinalidadeAcao.RECUPERACAO_SENHA
            );

            case "senha" -> service.enviarAvisoSenhaAlterada(
                    referenciaId,
                    EMAIL
            );

            case "professor" -> service.enviarConviteProfessor(
                    referenciaId,
                    EMAIL,
                    "https://example.test/cadastro#token=" + TOKEN
            );

            case "direcao" -> service.enviarConviteDirecao(
                    referenciaId,
                    EMAIL,
                    "https://example.test/cadastro/direcao#token=" + TOKEN
            );

            default -> throw new IllegalArgumentException(tipo);
        };
    }
}