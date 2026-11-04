package com.crecheconecta.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Registro de chamada (presença/falta) de um aluno em um dia.
 */
@Entity
@Table(name = "chamadas")
@Getter
@Setter
@NoArgsConstructor
public class Chamada {

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

    @Column(name = "presente", nullable = false)
    private boolean presente;

    @Column(name = "justificativa", length = 500)
    private String justificativa;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Version
    @Column(name = "versao", nullable = false)
    private Long versao;
}
