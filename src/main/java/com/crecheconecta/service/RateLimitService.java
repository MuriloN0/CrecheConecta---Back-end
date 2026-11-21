package com.crecheconecta.service;

import com.crecheconecta.security.RateLimitException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;

@Service
@ConditionalOnProperty(prefix = "app.rate-limit", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitService {

    public enum Politica {

        AUTENTICACAO_IP(30, 60),
        AUTENTICACAO_IP_HORA(300, 3600),
        AUTENTICACAO_GLOBAL(120, 60),
        LOGIN_EMAIL(10, 60),
        RECUPERACAO_EMAIL(3, 60),
        ACAO_VERIFICACAO(10, 60),
        API_USUARIO(240, 60),
        LEITURA_RECURSO(30, 60),
        LEITURA_MURAL(12, 60),
        LEITURA_FREQUENCIA(12, 60),
        ESCRITA_USUARIO(60, 60),
        SENHA_PERFIL(5, 300),
        CONFIRMACAO_EMAIL_PERFIL(10, 600),
        EXPORTACAO_DADOS(3, 300),
        CHAT_ENVIO(20, 60);

        final int quantidade;
        final int segundos;

        Politica(int quantidade, int segundos) {

            this.quantidade = quantidade;
            this.segundos = segundos;
        }
    }

    private static final String CONSUMIR = """
            INSERT INTO limites_requisicoes AS atual
                (politica, chave_hash, quantidade, expira_em)
            VALUES (?, ?, 1, statement_timestamp() + (? * interval '1 second'))
            ON CONFLICT (politica, chave_hash) DO UPDATE
            SET quantidade = CASE
                    WHEN atual.expira_em <= statement_timestamp() THEN 1
                    ELSE LEAST(atual.quantidade + 1, ?)
                END,
                expira_em = CASE
                    WHEN atual.expira_em <= statement_timestamp()
                        THEN EXCLUDED.expira_em
                    ELSE atual.expira_em
                END
            RETURNING quantidade <= ? AS permitido,
                GREATEST(1, CEIL(EXTRACT(
                    EPOCH FROM expira_em - statement_timestamp()
                )))::bigint AS segundos_restantes
            """;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transacao;
    private final SecretKeySpec chave;

    public RateLimitService(JdbcTemplate jdbc, PlatformTransactionManager gerenciador, @Value("${app.seguranca.hmac-chave-base64}") String segredoBase64) {

        this.jdbc = jdbc;
        this.transacao = new TransactionTemplate(gerenciador);

        this.transacao.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        this.transacao.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);

        this.transacao.setTimeout(5);

        byte[] segredo = Base64.getDecoder().decode(segredoBase64);

        if (segredo.length < 32) {

            throw new IllegalArgumentException("A chave HMAC deve possuir pelo menos 32 bytes.");
        }

        this.chave = new SecretKeySpec(segredo, "HmacSHA256");
    }

    public void verificar(Politica politica, String identidade) {

        Objects.requireNonNull(politica);
        Objects.requireNonNull(identidade);

        String hash = proteger(politica.name() + "\n" + identidade);

        var resultado = Objects.requireNonNull(transacao.execute(status ->
                jdbc.queryForObject(
                        CONSUMIR,
                        (rs, linha) -> new Resultado(rs.getBoolean("permitido"), rs.getLong("segundos_restantes")),
                        politica.name(),
                        hash,
                        politica.segundos,
                        politica.quantidade + 1,
                        politica.quantidade
                )
        ));

        if (!resultado.permitido()) {

            throw new RateLimitException(resultado.segundosRestantes());
        }
    }

    @Scheduled(fixedDelayString = "${app.rate-limit.limpeza-ms:300000}")
    public void limparExpirados() {
        jdbc.update("""
                DELETE FROM limites_requisicoes
                WHERE (politica, chave_hash) IN (
                    SELECT politica, chave_hash
                    FROM limites_requisicoes
                    WHERE expira_em < statement_timestamp() - interval '1 hour'
                    ORDER BY expira_em
                    LIMIT 5000
                )
                """);
    }

    private String proteger(String valor) {

        try {

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(chave);

            return HexFormat.of().formatHex(mac.doFinal(valor.getBytes(StandardCharsets.UTF_8)));

        } catch (java.security.GeneralSecurityException exception) {

            throw new IllegalStateException("Não foi possível calcular a chave do limite.", exception);
        }
    }

    private record Resultado(boolean permitido, long segundosRestantes) {

    }
}