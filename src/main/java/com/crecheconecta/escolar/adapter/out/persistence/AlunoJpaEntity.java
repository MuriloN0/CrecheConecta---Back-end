package com.crecheconecta.escolar.adapter.out.persistence;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "alunos")
@NoArgsConstructor
public class AlunoJpaEntity {
    @Id
    private UUID id;

    @Column(name = "nome", nullable = false, length = 150)
    private String nome;

    @Column(name = "endereco", length = 300)
    private String endereco;

    @Column(name = "email_contato", nullable = false, length = 254)
    private String emailContato;

    @Column(name = "telefone_contato", nullable = false, length = 15)
    private String telefoneContato;

    @Version
    @Column(name = "versao", nullable = false)
    private Long versao;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "aluno_responsaveis",
            joinColumns = @JoinColumn(name = "aluno_id")
    )
    @OrderColumn(name = "ordem")
    private List<ContatoResponsavelEmbeddable> responsaveis = new ArrayList<>();

    @Column(name = "ativo", nullable = false)
    private boolean ativo;
}
