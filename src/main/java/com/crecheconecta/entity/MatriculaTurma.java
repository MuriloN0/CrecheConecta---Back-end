package com.crecheconecta.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "matriculas_turmas")
@Getter
@Setter
@NoArgsConstructor
public class MatriculaTurma {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "aluno_id", nullable = false)
    private UUID alunoId;

    @Column(name = "turma_id", nullable = false)
    private UUID turmaId;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "encerrado_em")
    private Instant encerradoEm;
}