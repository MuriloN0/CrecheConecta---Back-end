package com.crecheconecta.saude.adapter.out.persistence;

import com.crecheconecta.saude.domain.model.Anexo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "fichas_saude_anexos")
public class AnexoJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String nomeArquivo;

    @Column(nullable = false)
    private String chaveArmazenamento;

    @Column(nullable = false)
    private String tipoConteudo;

    @Column(nullable = false)
    private long tamanhoBytes;

    @ManyToOne
    @JoinColumn(name = "ficha_id", nullable = false)
    private FichaSaudeJpaEntity ficha;

    protected AnexoJpaEntity() {
    }

    public AnexoJpaEntity(Anexo anexo, FichaSaudeJpaEntity ficha) {
        this.id = anexo.id();
        this.nomeArquivo = anexo.nomeArquivo();
        this.chaveArmazenamento = anexo.chaveArmazenamento();
        this.tipoConteudo = anexo.tipoConteudo();
        this.tamanhoBytes = anexo.tamanhoBytes();
        this.ficha = ficha;
    }

    public Anexo toDomain() {
        return new Anexo(id, nomeArquivo, chaveArmazenamento, tipoConteudo, tamanhoBytes);
    }

    public void setFicha(FichaSaudeJpaEntity ficha) {
        this.ficha = ficha;
    }
}
