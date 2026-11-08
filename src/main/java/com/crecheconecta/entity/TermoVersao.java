package com.crecheconecta.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Uma versão dos Termos de Uso + Política de Privacidade.
 * O "identificador" (ex.: 2026-09-26-v2) é o que o usuário aceita (aceites_termo.versao_termo).
 */
@Entity
@Table(name = "termos_versoes")
@Getter
@Setter
@NoArgsConstructor
public class TermoVersao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "identificador", nullable = false, length = 40)
    private String identificador;

    @Column(name = "termo_uso", nullable = false, length = 20000)
    private String termoUso;

    @Column(name = "politica_privacidade", nullable = false, length = 20000)
    private String politicaPrivacidade;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusTermo status;

    @Column(name = "criado_por")
    private UUID criadoPor;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    @Column(name = "publicado_em")
    private Instant publicadoEm;

    @Version
    @Column(name = "versao", nullable = false)
    private Long versao;
}
