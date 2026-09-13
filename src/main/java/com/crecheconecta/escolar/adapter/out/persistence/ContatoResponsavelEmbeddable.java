package com.crecheconecta.escolar.adapter.out.persistence;


import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class ContatoResponsavelEmbeddable {
    @Column(name = "nome", nullable = false, length = 150)
    private String nome;

    @Column(name = "parentesco", nullable = false, length = 50)
    private String parentesco;

    @Column(name = "email", length = 254)
    private String email;

    @Column(name = "telefone", nullable = false, length = 15)
    private String telefone;
}
