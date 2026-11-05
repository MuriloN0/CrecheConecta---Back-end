package com.crecheconecta.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Um item da Mala Virtual (ex.: "Fralda", 3 unidades, já enviado?).
 */
@Entity
@Table(name = "itens_mala")
@Getter
@Setter
@NoArgsConstructor
public class ItemMala {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "mala_id", nullable = false)
    private UUID malaId;

    @Column(name = "nome", nullable = false, length = 100)
    private String nome;

    @Column(name = "quantidade", nullable = false)
    private int quantidade;

    @Column(name = "enviado", nullable = false)
    private boolean enviado;
}
