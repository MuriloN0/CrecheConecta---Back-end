package com.crecheconecta.security;

import com.crecheconecta.configuration.RetencaoAutenticacao;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(
        named = "RETENCAO_TEST_URL",
        matches = "jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/retencao_test"
)
class RetencaoAutenticacaoPostgresTest {

    private static final Instant AGORA =
            Instant.parse("2026-11-20T12:00:00Z");

    private static final Instant LIMITE =
            AGORA.minus(Duration.ofDays(7));

    private static final Clock CLOCK =
            Clock.fixed(AGORA, ZoneOffset.UTC);

    private static final String SCHEMA =
            "retencao_" + UUID.randomUUID().toString().replace("-", "");

    private static JdbcTemplate admin;
    private static DriverManagerDataSource fonte;
    private static JdbcTemplate jdbc;

    private final UUID usuario = UUID.randomUUID();

    @BeforeAll
    static void criarSchemaIsolado() throws Exception {
        String url = System.getenv("RETENCAO_TEST_URL");

        admin = new JdbcTemplate(
                new DriverManagerDataSource(url, "rate_test", "")
        );

        admin.execute("CREATE SCHEMA " + SCHEMA);

        fonte = new DriverManagerDataSource(
                url + "?currentSchema=" + SCHEMA,
                "rate_test",
                ""
        );

        jdbc = new JdbcTemplate(fonte);
        jdbc.execute("CREATE TABLE usuarios (id UUID PRIMARY KEY)");

        for (String arquivo : new String[]{
                "V2__criar_tabela_desafios_verificacao.sql",
                "V3__criar_tabela_sessoes.sql",
                "V5__criar_auditoria.sql",
                "V38__auditar_sessoes_e_indexar_eventos.sql",
                "V40__adicionar_renovacao_de_sessao.sql"
        }) {
            jdbc.execute(
                    new ClassPathResource("db/migration/" + arquivo)
                            .getContentAsString(StandardCharsets.UTF_8)
            );
        }
    }

    @AfterAll
    static void removerSchemaIsolado() {
        if (admin != null) {
            admin.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        }
    }

    @BeforeEach
    void preparar() {
        jdbc.execute(
                "TRUNCATE sessoes, acao_verificacao, auditoria, usuarios"
        );
        jdbc.update("INSERT INTO usuarios (id) VALUES (?)", usuario);
    }

    @Test
    void removeSomenteSessoesExpiradasHaMaisDeSeteDiasEPreservaAuditoria() {
        UUID antiga = sessao(LIMITE.minusSeconds(1), null, null);
        UUID fronteira = sessao(LIMITE, null, null);
        UUID recente = sessao(AGORA.minusSeconds(60), null, null);
        UUID ativa = sessao(
                AGORA.plusSeconds(3000),
                AGORA.plusSeconds(900),
                null
        );
        UUID renovavel = sessao(
                AGORA.plusSeconds(1800),
                AGORA.minusSeconds(60),
                null
        );
        UUID revogada = sessao(AGORA.plusSeconds(3000), null, AGORA);

        int auditoriaAntes = quantidade("auditoria");

        limpar(500, 10);

        assertFalse(existe("sessoes", antiga));

        for (UUID id : new UUID[]{
                fronteira, recente, ativa, renovavel, revogada
        }) {
            assertTrue(existe("sessoes", id));
        }

        assertEquals(auditoriaAntes, quantidade("auditoria"));

        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM auditoria WHERE recurso_id = ?",
                        Integer.class,
                        antiga
                )
        );
    }

    @Test
    void preservaCodigosValidosEContadoresRecentesDeTodasAsFinalidades() {
        for (String finalidade : new String[]{
                "LOGIN", "RECUPERACAO_SENHA", "REDEFINICAO_SENHA"
        }) {
            UUID antiga = acao(LIMITE.minusSeconds(1), finalidade, 0);
            UUID fronteira = acao(LIMITE, finalidade, 0);
            UUID recente = acao(AGORA.minusSeconds(60), finalidade, 5);
            UUID valida = acao(AGORA.plusSeconds(300), finalidade, 0);

            limpar(500, 10);

            assertFalse(existe("acao_verificacao", antiga));
            assertTrue(existe("acao_verificacao", fronteira));
            assertTrue(existe("acao_verificacao", recente));
            assertTrue(existe("acao_verificacao", valida));
        }

        assertEquals(
                15,
                jdbc.queryForObject(
                        "SELECT SUM(tentativas) FROM acao_verificacao",
                        Integer.class
                )
        );
    }

    @Test
    void removeAcoesAntigasConsumidasEInvalidadas() {
        UUID consumida = acao(LIMITE.minusSeconds(1), "LOGIN", 1);
        UUID invalidada = acao(
                LIMITE.minusSeconds(1),
                "RECUPERACAO_SENHA",
                5
        );

        jdbc.update(
                "UPDATE acao_verificacao SET consumido_em = ? WHERE id = ?",
                LIMITE.minusSeconds(60).atOffset(ZoneOffset.UTC),
                consumida
        );

        jdbc.update(
                "UPDATE acao_verificacao SET invalidado_em = ? WHERE id = ?",
                LIMITE.minusSeconds(60).atOffset(ZoneOffset.UTC),
                invalidada
        );

        limpar(500, 10);

        assertFalse(existe("acao_verificacao", consumida));
        assertFalse(existe("acao_verificacao", invalidada));
    }

    @Test
    void limitaQuantidadePorExecucaoEContinuaNoProximoCiclo() {
        for (int i = 0; i < 5; i++) {
            sessao(LIMITE.minusSeconds(1), null, null);
            acao(LIMITE.minusSeconds(1), "LOGIN", 0);
        }

        limpar(2, 2);

        assertEquals(1, quantidade("sessoes"));
        assertEquals(1, quantidade("acao_verificacao"));

        limpar(2, 2);

        assertEquals(0, quantidade("sessoes"));
        assertEquals(0, quantidade("acao_verificacao"));
    }

    @Test
    void ignoraRegistroBloqueadoPorOutraTransacao() throws Exception {
        UUID bloqueada = sessao(LIMITE.minusSeconds(1), null, null);
        UUID livre = sessao(LIMITE.minusSeconds(1), null, null);

        try (var conexao = fonte.getConnection()) {
            conexao.setAutoCommit(false);

            try (var consulta = conexao.prepareStatement(
                    "SELECT id FROM sessoes WHERE id = ? FOR UPDATE"
            )) {
                consulta.setObject(1, bloqueada);
                consulta.executeQuery().close();
            }

            limpar(500, 10);

            assertTrue(existe("sessoes", bloqueada));
            assertFalse(existe("sessoes", livre));

            conexao.rollback();
        }

        limpar(500, 10);

        assertFalse(existe("sessoes", bloqueada));
    }

    @Test
    void repeticaoSemRegistrosElegiveisNaoAlteraDados() {
        UUID ativa = sessao(AGORA.plusSeconds(3000), null, null);

        limpar(500, 10);
        limpar(500, 10);

        assertTrue(existe("sessoes", ativa));
        assertEquals(1, quantidade("auditoria"));
    }

    private void limpar(int lote, int lotes) {
        new RetencaoAutenticacao(
                jdbc, CLOCK, "P7D", lote, lotes
        ).limpar();
    }

    private UUID sessao(
            Instant expira,
            Instant acesso,
            Instant revogada
    ) {
        UUID id = UUID.randomUUID();

        jdbc.update("""
                INSERT INTO sessoes
                    (id, usuario_id, token_hash, renovacao_hash, criado_em,
                     acesso_expira_em, expira_em, revogado_em)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                usuario,
                hash(),
                acesso == null ? null : hash(),
                expira.minusSeconds(7200).atOffset(ZoneOffset.UTC),
                acesso == null ? null : acesso.atOffset(ZoneOffset.UTC),
                expira.atOffset(ZoneOffset.UTC),
                revogada == null ? null : revogada.atOffset(ZoneOffset.UTC)
        );

        return id;
    }

    private UUID acao(
            Instant expira,
            String finalidade,
            int tentativas
    ) {
        UUID id = UUID.randomUUID();

        jdbc.update("""
                INSERT INTO acao_verificacao
                    (id, usuario_id, finalidade, segredo_hash,
                     criado_em, expira_em, tentativas)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                usuario,
                finalidade,
                hash(),
                expira.minusSeconds(300).atOffset(ZoneOffset.UTC),
                expira.atOffset(ZoneOffset.UTC),
                tentativas
        );

        return id;
    }

    private String hash() {
        return UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
    }

    private boolean existe(String tabela, UUID id) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + tabela + " WHERE id = ?",
                Integer.class,
                id
        ) == 1;
    }

    private int quantidade(String tabela) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + tabela,
                Integer.class
        );
    }
}