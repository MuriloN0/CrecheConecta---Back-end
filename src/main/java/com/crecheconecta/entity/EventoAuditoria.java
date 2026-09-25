package com.crecheconecta.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "auditoria")
@Getter
@Setter
@NoArgsConstructor
public class EventoAuditoria {

    @Id private UUID id = UUID.randomUUID();

    @Column(nullable = false)
    private Instant instante = Instant.now();

    private UUID usuarioId;

    @Column(nullable = false, length = 60)
    private String acao;

    private UUID recursoId;
}