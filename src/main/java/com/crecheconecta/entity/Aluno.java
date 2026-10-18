package com.crecheconecta.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "alunos")
@Getter
@Setter
@NoArgsConstructor
public class Aluno {

    @Id
    private UUID id = UUID.randomUUID();

    // Responsavel (perfil PAIS) dono deste aluno.
    // E a peca-chave da seguranca: o pai so enxerga os alunos
    // cujo responsavelId for o id dele (anti-IDOR).
    @Column(name = "responsavel_id", nullable = false)
    private UUID responsavelId;

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(name = "data_nascimento", nullable = false)
    private LocalDate dataNascimento;

    @Column(length = 80)
    private String turma;

    @Column(name = "tipo_sanguineo", length = 5)
    private String tipoSanguineo;

    @Column(name = "como_se_acalma", length = 500)
    private String comoSeAcalma;

    @Column(length = 500)
    private String medos;

    @Column(name = "data_criacao", nullable = false)
    private Instant dataCriacao;

    @Column(name = "data_atualizacao", nullable = false)
    private Instant dataAtualizacao;

    @Version
    private Long versao;
}
