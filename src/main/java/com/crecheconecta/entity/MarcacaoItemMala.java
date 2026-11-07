package com.crecheconecta.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "marcacoes_itens_mala")
@Getter
@Setter
@NoArgsConstructor
public class MarcacaoItemMala {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "item_id", nullable = false, updatable = false)
    private UUID itemId;

    @Column(name = "aluno_id", nullable = false, updatable = false)
    private UUID alunoId;

    @Column(name = "enviado", nullable = false)
    private boolean enviado;

    @Column(name = "observacao", length = 500)
    private String observacao;

    @Column(name = "marcado_por", nullable = false)
    private UUID marcadoPor;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;
}
