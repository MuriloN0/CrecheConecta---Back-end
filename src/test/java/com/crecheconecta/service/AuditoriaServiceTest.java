package com.crecheconecta.service;

import com.crecheconecta.entity.EventoAuditoria;
import com.crecheconecta.repository.AuditoriaRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;

import java.util.UUID;

import static com.crecheconecta.service.GerenciadorTransacoesTest.transacoes;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuditoriaServiceTest {

    private final AuditoriaRepository repository = mock(AuditoriaRepository.class);
    private final PlatformTransactionManager manager = transacoes();
    private final AuditoriaService service = new AuditoriaService(repository, manager);
    private final UUID usuarioId = UUID.randomUUID();
    private final UUID recursoId = UUID.randomUUID();

    @Test
    void deveRegistrarEventoEmTransacaoIndependente() {
        service.registrar("ACAO_TESTE", usuarioId, recursoId);

        conferirEvento();
        verify(manager).getTransaction(argThat(definicao ->
                definicao.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW
        ));
    }

    @Test
    void deveParticiparDaTransacaoDeNegocio() {
        service.aposCommit("ACAO_TESTE", usuarioId, recursoId);

        conferirEvento();
        verify(manager).getTransaction(argThat(definicao ->
                definicao.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRED
        ));
    }

    @Test
    void devePropagarFalhaDaAuditoria() {
        when(repository.save(any(EventoAuditoria.class)))
                .thenThrow(new IllegalStateException("Falha simulada"));

        assertThrows(IllegalStateException.class,
                () -> service.aposCommit("ACAO_TESTE", usuarioId, recursoId));

        verify(manager).rollback(any());
    }

    private void conferirEvento() {
        var captor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(repository).save(captor.capture());
        assertEquals("ACAO_TESTE", captor.getValue().getAcao());
        assertEquals(usuarioId, captor.getValue().getUsuarioId());
        assertEquals(recursoId, captor.getValue().getRecursoId());
    }
}