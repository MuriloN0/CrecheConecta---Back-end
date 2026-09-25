package com.crecheconecta.repository;

import com.crecheconecta.entity.EventoAuditoria;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditoriaRepository extends JpaRepository<EventoAuditoria, UUID> {}