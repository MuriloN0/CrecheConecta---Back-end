package com.crecheconecta.service;

import com.crecheconecta.entity.EventoAuditoria;
import com.crecheconecta.repository.AuditoriaRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

import static com.crecheconecta.service.GerenciadorTransacoesTest.transacoes;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuditoriaServiceTest {

    private final AuditoriaRepository repository =
            mock(AuditoriaRepository.class);

    private final PlatformTransactionManager manager =
            transacoes();

    private final AuditoriaService service =
            new AuditoriaService(repository, manager);

    private final UUID usuarioId = UUID.randomUUID();
    private final UUID recursoId = UUID.randomUUID();

    @Test
    void deveRegistrarEventoEmTransacaoIndependente() {
        service.registrar("ACAO_TESTE", usuarioId, recursoId);

        var captor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(repository).save(captor.capture());

        assertEquals("ACAO_TESTE", captor.getValue().getAcao());
        assertEquals(usuarioId, captor.getValue().getUsuarioId());
        assertEquals(recursoId, captor.getValue().getRecursoId());

        verify(manager).getTransaction(argThat(definicao ->
                definicao.getPropagationBehavior()
                        == TransactionDefinition.PROPAGATION_REQUIRES_NEW
        ));
    }

    @Test
    void deveRegistrarImediatamenteSemSincronizacaoAtiva() {
        service.aposCommit("ACAO_TESTE", usuarioId, recursoId);

        verify(repository).save(any(EventoAuditoria.class));
    }

    @Test
    void deveAguardarCallbackDeCommit() {
        TransactionSynchronizationManager.initSynchronization();

        try {
            service.aposCommit("ACAO_TESTE", usuarioId, recursoId);

            verifyNoInteractions(repository);

            for (var sincronizacao :
                    TransactionSynchronizationManager.getSynchronizations()) {
                sincronizacao.afterCommit();
            }

            verify(repository).save(any(EventoAuditoria.class));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void naoDeveRegistrarSucessoNoCallbackDeRollback() {
        TransactionSynchronizationManager.initSynchronization();

        try {
            service.aposCommit("ACAO_TESTE", usuarioId, recursoId);

            for (var sincronizacao :
                    TransactionSynchronizationManager.getSynchronizations()) {
                sincronizacao.afterCompletion(
                        TransactionSynchronization.STATUS_ROLLED_BACK
                );
            }

            verifyNoInteractions(repository);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}