package com.crecheconecta.atividades.adapter.out.persistence;

import com.crecheconecta.atividades.domain.model.ConclusaoAtividade;
import com.crecheconecta.atividades.domain.model.StatusConclusao;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "conclusao_atividade")
public class ConclusaoAtividadeEntity {

    @Id
    private final UUID id;

    @Column(nullable = false)
    private final UUID atividadeId;

    @Column(nullable = false)
    private final UUID alunoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private final StatusConclusao status;

    @Column
    private final Instant marcadaEm;

    public ConclusaoAtividadeEntity(ConclusaoAtividade conclusao) {
        this.id = conclusao.id();
        this.atividadeId = conclusao.atividadeId();
        this.alunoId = conclusao.alunoId();
        this.status = conclusao.status();
        this.marcadaEm = conclusao.marcadaEm();
    }

    public ConclusaoAtividade toDomain() {
        return new ConclusaoAtividade(
                this.id,
                this.atividadeId,
                this.alunoId,
                this.status,
                this.marcadaEm
        );
    }
}