package com.crecheconecta.service;


import com.crecheconecta.entity.FinalidadeAcao;
import com.crecheconecta.exception.AutenticacaoException;
import com.crecheconecta.exception.ErroAutenticacao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class EmailService {


    private static final Logger log =
            LoggerFactory.getLogger(EmailService.class);

    private final RestClient resend;
    private final String remetente;

    public EmailService(
            @Qualifier("resendRestClient") RestClient resend,
            @Value("${app.email.resend.remetente}") String remetente
    ) {
        if (remetente.isBlank()) {
            throw new IllegalStateException(
                    "RESEND_FROM deve estar configurado."
            );
        }

        this.resend = resend;
        this.remetente = remetente;
    }

    public String enviarCodigo(
            UUID acaoId,
            String destinatario,
            String codigo,
            FinalidadeAcao finalidade
    ) {
        Objects.requireNonNull(acaoId, "A ação deve ser informada.");
        Objects.requireNonNull(finalidade, "A finalidade deve ser informada.");

        if (destinatario == null || destinatario.isBlank()) {
            throw new IllegalArgumentException(
                    "O destinatário deve ser informado."
            );
        }

        if (codigo == null || !codigo.matches("[0-9]{6}")) {
            throw new IllegalArgumentException(
                    "O código deve conter seis dígitos."
            );
        }

        String assunto = switch (finalidade) {
            case LOGIN ->
                    "CrecheConecta - Código de acesso";

            case RECUPERACAO_SENHA ->
                    "CrecheConecta - Recuperação de senha";

            case REDEFINICAO_SENHA ->
                    throw new IllegalArgumentException(
                            "A autorização de redefinição não é enviada por e-mail."
                    );
        };

        String finalidadeTexto = switch (finalidade) {
            case LOGIN -> "confirmar seu acesso";
            case RECUPERACAO_SENHA -> "recuperar sua senha";
            case REDEFINICAO_SENHA ->
                    throw new IllegalArgumentException(
                            "Finalidade incompatível com envio de código."
                    );
        };

        String texto = """
        Olá!

        Use o código abaixo para %s no CrecheConecta:

        %s

        O código possui validade limitada.
        Confira o prazo informado na tela e utilize o código mais recente.

        Não compartilhe este código.
        Se você não fez esta solicitação, ignore esta mensagem.

        Equipe CrecheConecta
        """.formatted(finalidadeTexto, codigo);

        var requisicao = new EnviarEmailRequest(
                remetente,
                List.of(destinatario),
                assunto,
                texto
        );

        try {
            var resposta = resend.post()
                    .uri("/emails")
                    .header(
                            "Idempotency-Key",
                            "acao-verificacao/" + acaoId
                    )
                    .body(requisicao)
                    .retrieve()
                    .body(EnviarEmailResponse.class);

            if (resposta == null
                    || resposta.id() == null
                    || resposta.id().isBlank()) {

                log.warn(
                        "Resend retornou resposta sem identificador. acaoId={}",
                        acaoId
                );

                throw envioIndisponivel();
            }

            log.info(
                    "Resend aceitou o envio. acaoId={}, emailId={}",
                    acaoId,
                    resposta.id()
            );

            return resposta.id();

        } catch (RestClientResponseException exception) {
            log.warn(
                    "Resend rejeitou o envio. acaoId={}, status={}",
                    acaoId,
                    exception.getStatusCode().value()
            );

            throw envioIndisponivel();

        } catch (RestClientException exception) {
            log.warn(
                    "Falha na comunicação com Resend. acaoId={}, tipo={}",
                    acaoId,
                    exception.getClass().getSimpleName()
            );

            throw envioIndisponivel();
        }
    }

    private AutenticacaoException envioIndisponivel() {
        return new AutenticacaoException(
                ErroAutenticacao.ENVIO_EMAIL_INDISPONIVEL
        );
    }

    public record EnviarEmailRequest(
            String from,
            List<String> to,
            String subject,
            String text
    ) {
    }

    public record EnviarEmailResponse(String id) {
    }
}
