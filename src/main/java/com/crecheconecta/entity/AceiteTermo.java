package com.crecheconecta.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "aceites_termo")
@Getter
@Setter
@NoArgsConstructor
public class AceiteTermo {

    @Id private UUID id = UUID.randomUUID();

    @Column(nullable = false)
    private UUID usuarioId;

    @Column(nullable = false, length = 40)
    private String versaoTermo;

    @Column(nullable = false)
    private Instant dataAceite = Instant.now();
}