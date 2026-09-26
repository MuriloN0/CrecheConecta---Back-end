package com.crecheconecta.service;

import com.crecheconecta.entity.EventoAuditoria;
import com.crecheconecta.repository.AuditoriaRepository;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Slf4j
public class AuditoriaService {

    private final AuditoriaRepository repository;
    private final TransactionTemplate transaction;

    public AuditoriaService(AuditoriaRepository repository, PlatformTransactionManager manager) {
        this.repository = repository;
        this.transaction = new TransactionTemplate(manager);

        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void registrar(String acao, UUID usuario, UUID recurso) {
        transaction.executeWithoutResult(
                status -> {
                    EventoAuditoria evento = new EventoAuditoria();
                    evento.setAcao(acao);
                    evento.setUsuarioId(usuario);
                    evento.setRecursoId(recurso);
                    repository.save(evento);
                });
        log.info("auditoria acao={} usuario={} recurso={}", acao, usuario, recurso);
    }

    public void aposCommit(String acao, UUID usuario, UUID recurso) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            registrar(acao, usuario, recurso);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        registrar(acao, usuario, recurso);
                    }
                });
    }
}
        