package com.crecheconecta.service;

import com.crecheconecta.repository.AuditoriaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auditoria_atomica;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
})
@ActiveProfiles("test")
class AuditoriaAtomicaIntegrationTest {

    @Autowired private AuditoriaService auditoria;
    @Autowired private AuditoriaRepository eventos;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager manager;

    private final UUID recurso = UUID.randomUUID();

    @BeforeEach
    void preparar() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS negocio_auditoria_teste (id UUID PRIMARY KEY)");
    }

    @Test
    void confirmaNegocioEAuditoriaJuntos() {
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            inserirNegocio();
            auditoria.aposCommit("ACAO_TESTE", null, recurso);
        });

        assertEquals(1, negocios());
        assertEquals(1, eventosDoRecurso());
    }

    @Test
    void rollbackDoNegocioTambemRemoveAuditoria() {
        assertThrows(IllegalStateException.class, () ->
                new TransactionTemplate(manager).executeWithoutResult(status -> {
                    inserirNegocio();
                    auditoria.aposCommit("ACAO_TESTE", null, recurso);
                    eventos.flush();
                    throw new IllegalStateException("Falha simulada de negocio");
                }));

        assertEquals(0, negocios());
        assertEquals(0, eventosDoRecurso());
    }

    @Test
    void falhaNaAuditoriaImpedeCommitDoNegocio() {
        assertThrows(RuntimeException.class, () ->
                new TransactionTemplate(manager).executeWithoutResult(status -> {
                    inserirNegocio();
                    auditoria.aposCommit("A".repeat(61), null, recurso);
                    eventos.flush();
                }));

        assertEquals(0, negocios());
        assertEquals(0, eventosDoRecurso());
    }

    @Test
    void registraSemTransacaoExterna() {
        auditoria.aposCommit("ACAO_TESTE", null, recurso);

        assertEquals(1, eventosDoRecurso());
    }

    @Test
    void registroIndependentePermaneceAposRollbackExterno() {
        assertThrows(IllegalStateException.class, () ->
                new TransactionTemplate(manager).executeWithoutResult(status -> {
                    inserirNegocio();
                    auditoria.registrar("ACAO_TESTE", null, recurso);
                    throw new IllegalStateException("Falha simulada de negocio");
                }));

        assertEquals(0, negocios());
        assertEquals(1, eventosDoRecurso());
    }

    private void inserirNegocio() {
        jdbc.update("INSERT INTO negocio_auditoria_teste (id) VALUES (?)", recurso);
    }

    private int negocios() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM negocio_auditoria_teste WHERE id = ?",
                Integer.class,
                recurso
        );
    }

    private int eventosDoRecurso() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM auditoria WHERE recurso_id = ?",
                Integer.class,
                recurso
        );
    }
}