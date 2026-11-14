package com.crecheconecta.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "aceites_termo")
@Getter
@Setter
@NoArgsConstructor
public class AceiteTermo {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false)
    private UUID usuarioId;

    private UUID alunoId;

    @Column(nullable = false, length = 40)
    private String versaoTermo;

    @Column(nullable = false)
    private Instant dataAceite = Instant.now();
}