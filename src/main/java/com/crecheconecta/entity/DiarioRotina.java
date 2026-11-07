package com.crecheconecta.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Diário de Rotina Digital: o resumo do dia de um aluno (alimentação, sono, evacuação, humor).
 */
@Entity
@Table(name = "diarios_rotina")
@Getter
@Setter
@NoArgsConstructor
public class DiarioRotina {

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

    @Enumerated(EnumType.STRING)
    @Column(name = "alimentacao", nullable = false, length = 20)
    private Alimentacao alimentacao;

    @Enumerated(EnumType.STRING)
    @Column(name = "sono", nullable = false, length = 20)
    private Sono sono;

    @Column(name = "evacuacao", nullable = false)
    private boolean evacuacao;

    @Enumerated(EnumType.STRING)
    @Column(name = "humor", nullable = false, length = 20)
    private Humor humor;

    @Enumerated(EnumType.STRING)
    @Column(name = "higiene", nullable = false, length = 20)
    private Higiene higiene;

    @Column(name = "observacao_professor", length = 1000)
    private String observacaoProfessor;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Version
    @Column(name = "versao", nullable = false)
    private Long versao;
}
