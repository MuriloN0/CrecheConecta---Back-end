package com.crecheconecta.entity;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "fichas_saude_anexos")
@Getter
@Setter
@NoArgsConstructor
public class AnexoSaude {

    @Id private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ficha_id", nullable = false)
    private FichaSaude ficha;

    @Column(nullable = false, length = 255)
    private String nomeArquivo;

    @Column(nullable = false, length = 500)
    private String chaveArmazenamento;

    @Column(nullable = false, length = 100)
    private String tipoConteudo;

    @Column(nullable = false)
    private long tamanhoBytes;
}