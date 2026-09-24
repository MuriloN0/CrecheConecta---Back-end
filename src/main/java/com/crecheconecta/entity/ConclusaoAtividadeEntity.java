package com.crecheconecta.entity;

import com.crecheconecta.model.StatusConclusao;
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
import java.util.UUID;

@Entity
@Table(name = "conclusao_atividade")
@Getter
@Setter
@NoArgsConstructor
public class ConclusaoAtividadeEntity {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false)
    private UUID atividadeId;

    @Column(nullable = false)
    private UUID alunoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusConclusao status;

    @Column
    private Instant marcadaEm;
}