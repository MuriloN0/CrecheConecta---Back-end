package com.crecheconecta.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Mala Virtual de um aluno em um dia. Os itens ficam em ItemMala (malaId).
 */
@Entity
@Table(name = "malas_virtuais")
@Getter
@Setter
@NoArgsConstructor
public class MalaVirtual {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "aluno_id", nullable = false)
    private UUID alunoId;

    @Column(name = "turma_id", nullable = false)
    private UUID turmaId;

    @Column(name = "professor_id", nullable = false)
    private UUID professorId;

    @Column(name = "data", nullable = false)
    private LocalDate data;

    @Column(name = "observacao", length = 1000)
    private String observacao;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Version
    @Column(name = "versao", nullable = false)
    private Long versao;
}
