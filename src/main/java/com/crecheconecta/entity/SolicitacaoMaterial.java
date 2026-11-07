package com.crecheconecta.entity;

import com.crecheconecta.exception.ConflitoVersaoException;
import com.crecheconecta.exception.RegraNegocioException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Entity
@Table(name = "solicitacoes_materiais")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SolicitacaoMaterial {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "professor_id", nullable = false, updatable = false)
    private UUID professorId;

    @Column(name = "sala", nullable = false, length = 50)
    private String sala;

    @Column(name = "titulo", nullable = false, length = 150)
    private String titulo;

    @Column(name = "descricao", length = 2000)
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(name = "prioridade", nullable = false, length = 10)
    private PrioridadeSolicitacaoMaterial prioridade;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusSolicitacaoMaterial status;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    @Column(name = "finalizado_em")
    private Instant finalizadoEm;

    @Version
    @Column(name = "versao", nullable = false)
    private Long versao;

    public SolicitacaoMaterial(
            UUID professorId,
            String sala,
            String titulo,
            String descricao,
            PrioridadeSolicitacaoMaterial prioridade,
            Instant agora
    ) {

        this.professorId = professorId;
        this.sala = sala.strip();
        this.titulo = titulo.strip();
        this.descricao = textoOpcional(descricao);
        this.prioridade = prioridade;
        this.status = StatusSolicitacaoMaterial.ABERTA;
        this.criadoEm = agora;
        this.atualizadoEm = agora;
    }

    public void atualizar(
            String sala,
            String titulo,
            String descricao,
            PrioridadeSolicitacaoMaterial prioridade,
            Instant agora
    ) {

        exigirAberta();

        this.sala = sala.strip();
        this.titulo = titulo.strip();
        this.descricao = textoOpcional(descricao);
        this.prioridade = prioridade;
        this.atualizadoEm = agora;
    }

    public MensagemSolicitacaoMaterial registrarMensagem(UUID autorId, String conteudo, Instant agora) {

        exigirAberta();

        this.atualizadoEm = agora;

        return new MensagemSolicitacaoMaterial(id, autorId, conteudo, agora);
    }

    public Optional<MensagemSolicitacaoMaterial> atender(UUID diretorId, String mensagem, Instant agora) {

        exigirAberta();

        Optional<MensagemSolicitacaoMaterial> resposta = Optional.ofNullable(textoOpcional(mensagem))
                .map(texto -> registrarMensagem(diretorId, texto, agora));

        finalizar(StatusSolicitacaoMaterial.ATENDIDA, agora);

        return resposta;
    }

    public MensagemSolicitacaoMaterial recusar(UUID diretorId, String motivo, Instant agora) {

        MensagemSolicitacaoMaterial mensagemDeRecusa = registrarMensagem(diretorId, motivo, agora);

        finalizar(StatusSolicitacaoMaterial.RECUSADA, agora);

        return mensagemDeRecusa;
    }

    public void cancelar(Instant agora) {

        exigirAberta();

        finalizar(StatusSolicitacaoMaterial.CANCELADA, agora);
    }

    public boolean pertenceAo(UUID professorId) {

        return this.professorId.equals(professorId);
    }

    public void exigirVersao(Long versaoEsperada) {

        if (!Objects.equals(this.versao, versaoEsperada)) {

            throw new ConflitoVersaoException();
        }
    }

    private void finalizar(StatusSolicitacaoMaterial novoStatus, Instant agora) {

        this.status = novoStatus;
        this.finalizadoEm = agora;
        this.atualizadoEm = agora;
    }

    private void exigirAberta() {

        if (status.isFinalizada()) {

            throw new RegraNegocioException("Esta solicitação já foi finalizada.");
        }
    }

    private static String textoOpcional(String texto) {

        return texto == null || texto.isBlank() ? null : texto.strip();
    }
}
