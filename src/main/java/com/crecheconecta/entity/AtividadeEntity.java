package com.crecheconecta.entity;

import com.crecheconecta.model.TipoAtividade;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "atividades")
@Getter
@Setter
@NoArgsConstructor
public class AtividadeEntity {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false)
    private UUID turmaId;

    @Column(nullable = false)
    private UUID professorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoAtividade tipo;

    @Column(nullable = false, length = 150)
    private String titulo;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @Column(nullable = false)
    private Instant dataCriacao = Instant.now();

    @Column
    private LocalDate prazoConclusao;
}