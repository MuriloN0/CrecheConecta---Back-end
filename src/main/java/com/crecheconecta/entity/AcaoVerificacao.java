package com.crecheconecta.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "acao_verificacao")
@Getter
@Setter
@NoArgsConstructor
public class AcaoVerificacao {

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FinalidadeAcao finalidade;

    @Column(name = "segredo_hash", nullable = false, length = 64)
    private String segredoHash;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    @Column(nullable = false)
    private int tentativas = 0;

    @Column(name = "consumido_em")
    private Instant consumidoEm;

    @Column(name = "invalidado_em")
    private Instant invalidadoEm;

    @Version
    private Long versao;
}
