package com.crecheconecta.service;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class GerenciadorTransacoesTest {

    private GerenciadorTransacoesTest() {
    }

    static PlatformTransactionManager transacoes() {
        var manager = mock(PlatformTransactionManager.class);

        when(manager.getTransaction(any(TransactionDefinition.class)))
                .thenAnswer(invocation -> new SimpleTransactionStatus());

        return manager;
    }
}