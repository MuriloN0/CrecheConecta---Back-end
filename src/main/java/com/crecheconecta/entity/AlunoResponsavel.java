package com.crecheconecta.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "alunos_responsaveis")
@Getter
@Setter
@NoArgsConstructor
public class AlunoResponsavel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "aluno_id", nullable = false)
    private UUID alunoId;

    @Column(name = "responsavel_id", nullable = false)
    private UUID responsavelId;

    @Column(nullable = false)
    private int posicao;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "encerrado_em")
    private Instant encerradoEm;
}