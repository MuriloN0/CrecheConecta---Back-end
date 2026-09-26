package com.crecheconecta.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import lombok.*;

@Entity
@Table(name = "fichas_saude")
@Getter
@Setter
@NoArgsConstructor
public class FichaSaude {

    @Id private UUID id = UUID.randomUUID();

    @Column(nullable = false)
    private UUID alunoId;

    @Column(nullable = false, length = 120)
    private String nome;
    
    @Column(columnDefinition = "text")
    private String observacoes;

    @Column(nullable = false)
    private Instant dataCriacao;

    @Column(nullable = false)
    private Instant dataAtualizacao;

    @Version private Long versao;

    @OneToMany(
            mappedBy = "ficha",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.EAGER)
    private List<AnexoSaude> anexos = new ArrayList<>();

    public void adicionarAnexo(AnexoSaude anexo) {
        anexo.setFicha(this);
        this.anexos.add(anexo);
    }
}
