package com.crecheconecta.atividades.adapter.out.persistence;

import com.crecheconecta.atividades.domain.model.Atividade;
import com.crecheconecta.atividades.domain.model.TipoAtividade;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "atividades")
public class AtividadeEntity {

    @Id
    private UUID id;

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
    private Instant dataCriacao;

    @Column
    private LocalDate prazoConclusao;

    protected AtividadeEntity() {}

    public AtividadeEntity(Atividade atividade) {
        this.id = atividade.id();
        this.turmaId = atividade.turmaId();
        this.professorId = atividade.professorId();
        this.tipo = atividade.tipo();
        this.titulo = atividade.titulo();
        this.descricao = atividade.descricao();
        this.dataCriacao = atividade.dataCriacao();
        this.prazoConclusao = atividade.prazoConclusao();
    }

    public Atividade toDomain() {
        return new Atividade(
                this.id,
                this.turmaId,
                this.professorId,
                this.tipo,
                this.titulo,
                this.descricao,
                this.dataCriacao,
                this.prazoConclusao
        );
    }
}