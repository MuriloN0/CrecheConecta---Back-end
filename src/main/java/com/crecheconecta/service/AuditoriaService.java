package com.crecheconecta.service;

import com.crecheconecta.entity.EventoAuditoria;
import com.crecheconecta.repository.AuditoriaRepository;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Slf4j
public class AuditoriaService {

    private final AuditoriaRepository repository;
    private final TransactionTemplate independente;
    private final TransactionTemplate negocio;

    public AuditoriaService(AuditoriaRepository repository, PlatformTransactionManager manager) {
        this.repository = repository;
        this.independente = new TransactionTemplate(manager);
        this.independente.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.negocio = new TransactionTemplate(manager);
        this.negocio.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
    }

    public void registrar(String acao, UUID usuario, UUID recurso) {
        independente.executeWithoutResult(status -> salvar(acao, usuario, recurso));
        log.info("auditoria acao={} usuario={} recurso={}", acao, usuario, recurso);
    }

    public void aposCommit(String acao, UUID usuario, UUID recurso) {
        negocio.executeWithoutResult(status -> salvar(acao, usuario, recurso));
    }

    private void salvar(String acao, UUID usuario, UUID recurso) {
        EventoAuditoria evento = new EventoAuditoria();
        evento.setAcao(acao);
        evento.setUsuarioId(usuario);
        evento.setRecursoId(recurso);
        repository.save(evento);
    }
}