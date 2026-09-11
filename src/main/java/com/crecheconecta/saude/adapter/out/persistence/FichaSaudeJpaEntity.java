package com.crecheconecta.saude.adapter.out.persistence;

import com.crecheconecta.saude.domain.model.Anexo;
import com.crecheconecta.saude.domain.model.FichaSaude;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "fichas_saude")
public class FichaSaudeJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID alunoId;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(columnDefinition = "text")
    private String observacoes;

    @Column(nullable = false)
    private Instant dataCriacao;

    @Column(nullable = false)
    private Instant dataAtualizacao;

    @OneToMany(mappedBy = "ficha", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AnexoJpaEntity> anexos = new ArrayList<>();

    protected FichaSaudeJpaEntity() {
    }

    public static FichaSaudeJpaEntity de(FichaSaude ficha) {
        FichaSaudeJpaEntity entity = new FichaSaudeJpaEntity();
        entity.id = ficha.id();
        entity.alunoId = ficha.alunoId();
        entity.nome = ficha.nome();
        entity.observacoes = ficha.observacoes();
        entity.dataCriacao = ficha.dataCriacao();
        entity.dataAtualizacao = ficha.dataAtualizacao();
        entity.anexos = new ArrayList<>();
        for (Anexo anexo : ficha.anexos()) {
            entity.anexos.add(new AnexoJpaEntity(anexo, entity));
        }
        return entity;
    }

    public FichaSaude toDomain() {
        List<Anexo> anexosDominio = new ArrayList<>();
        for (AnexoJpaEntity anexo : anexos) {
            anexosDominio.add(anexo.toDomain());
        }
        return new FichaSaude(
                id, alunoId, nome, observacoes, anexosDominio, dataCriacao, dataAtualizacao);
    }
}
