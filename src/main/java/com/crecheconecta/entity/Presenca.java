package com.crecheconecta.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "presencas")
@Getter
@Setter
@NoArgsConstructor
public class Presenca {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "turma_id", nullable = false, updatable = false)
    private UUID turmaId;

    @Column(name = "aluno_id", nullable = false, updatable = false)
    private UUID alunoId;

    @Column(nullable = false, updatable = false)
    private LocalDate data;

    private Boolean presente;

    @Column(name = "registrado_por", nullable = false)
    private UUID registradoPor;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    @Version
    @Column(nullable = false)
    private Long versao;

    public Boolean isPresente() {
        return presente;
    }
}