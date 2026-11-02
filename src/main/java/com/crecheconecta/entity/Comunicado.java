package com.crecheconecta.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "comunicados")
@Getter
@Setter
@NoArgsConstructor
public class Comunicado {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 150)
    private String titulo;

    @Column(nullable = false, length = 5000)
    private String conteudo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "criado_por", nullable = false, updatable = false)
    private Usuario criadoPor;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    @Column(nullable = false)
    private boolean ativo = true;

    @Version
    @Column(nullable = false)
    private Long versao;
}