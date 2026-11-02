package com.crecheconecta.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "convites_cadastro")
@Getter
@Setter
@NoArgsConstructor
public class ConviteCadastro {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(nullable = false, length = 254)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Perfil perfil;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "criado_por", nullable = false)
    private Usuario criadoPor;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    @Column(name = "consumido_em")
    private Instant consumidoEm;

    @Column(name = "cancelado_em")
    private Instant canceladoEm;

    @Version
    @Column(nullable = false)
    private Long versao;

    public boolean estaDisponivel(Instant agora) {

        return consumidoEm == null && canceladoEm == null && expiraEm != null && expiraEm.isAfter(agora);

    }
}