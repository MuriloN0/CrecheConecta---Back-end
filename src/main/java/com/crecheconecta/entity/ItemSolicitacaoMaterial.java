package com.crecheconecta.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Comparator;
import java.util.UUID;

@Entity
@Table(name = "itens_solicitacao_material")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItemSolicitacaoMaterial {

    public static final Comparator<ItemSolicitacaoMaterial> POR_NOME =
            Comparator.comparing(ItemSolicitacaoMaterial::getNome, String.CASE_INSENSITIVE_ORDER);

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "solicitacao_id", nullable = false, updatable = false)
    private UUID solicitacaoId;

    @Column(name = "nome", nullable = false, length = 100)
    private String nome;

    @Column(name = "quantidade", nullable = false)
    private int quantidade;

    public ItemSolicitacaoMaterial(UUID solicitacaoId, String nome, int quantidade) {

        this.solicitacaoId = solicitacaoId;
        this.nome = nome.strip();
        this.quantidade = quantidade;
    }
}
