package com.crecheconecta.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "mensagens_solicitacao_material")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MensagemSolicitacaoMaterial {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "solicitacao_id", nullable = false, updatable = false)
    private UUID solicitacaoId;

    @Column(name = "autor_id", nullable = false, updatable = false)
    private UUID autorId;

    @Column(name = "conteudo", nullable = false, length = 2000, updatable = false)
    private String conteudo;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    MensagemSolicitacaoMaterial(UUID solicitacaoId, UUID autorId, String conteudo, Instant criadoEm) {

        this.solicitacaoId = solicitacaoId;
        this.autorId = autorId;
        this.conteudo = conteudo.strip();
        this.criadoEm = criadoEm;
    }
}
