package com.crecheconecta.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Comparator;
import java.util.UUID;

@Entity
@Table(name = "itens_mala")
@Getter
@Setter
@NoArgsConstructor
public class ItemMala {

    public static final Comparator<ItemMala> POR_NOME = Comparator.comparing(ItemMala::getNome, String.CASE_INSENSITIVE_ORDER);

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "mala_id", nullable = false, updatable = false)
    private UUID malaId;

    @Column(name = "nome", nullable = false, length = 100)
    private String nome;

    @Column(name = "quantidade", nullable = false)
    private int quantidade;
}
