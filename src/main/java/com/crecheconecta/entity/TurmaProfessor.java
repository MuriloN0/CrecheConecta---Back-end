package com.crecheconecta.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "turmas_professores")
@Getter
@Setter
@NoArgsConstructor
public class TurmaProfessor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "turma_id", nullable = false)
    private UUID turmaId;

    @Column(name = "professor_id", nullable = false)
    private UUID professorId;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "encerrado_em")
    private Instant encerradoEm;
}