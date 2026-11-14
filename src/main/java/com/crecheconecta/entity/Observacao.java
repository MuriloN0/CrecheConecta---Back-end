package com.crecheconecta.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "observacoes")
@Getter
@Setter
@NoArgsConstructor
public class Observacao {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "aluno_id", nullable = false)
    private UUID alunoId;

    /** Professor que registrou a observação. Só ele edita ou exclui. */
    @Column(name = "autor_id", nullable = false)
    private UUID autorId;

    @Column(nullable = false, length = 120)
    private String titulo;
    
    @Column(columnDefinition = "text")
    private String conteudo;

    @Column(name = "data_criacao", nullable = false)
    private Instant dataCriacao;

    @Column(name = "data_atualizacao", nullable = false)
    private Instant dataAtualizacao;

    @Version
    private Long versao;
}
