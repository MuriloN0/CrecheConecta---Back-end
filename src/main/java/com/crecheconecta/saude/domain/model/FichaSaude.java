package com.crecheconecta.saude.domain.model;

import com.crecheconecta.saude.application.exception.FichaSaudeInvalidaException;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record FichaSaude (
        UUID id,
        UUID alunoId,
        String nome,
        String observacoes,
        List<Anexo> anexos,
        Instant dataCriacao,
        Instant dataAtualizacao
) {
    private static final int NOME_MAX = 120;
    private static final int OBS_MAX = 5000;
    private static final int MAX_ANEXOS = 10;

    public FichaSaude {
        Objects.requireNonNull(id, "id é obrigatória");
        Objects.requireNonNull(alunoId, "alunoId é obrigatória");
        Objects.requireNonNull(dataCriacao, "dataCriacao é obrigatória");
        Objects.requireNonNull(dataAtualizacao, "dataAtualizacao é obrigatória");

        if (nome == null || nome.isBlank()) {
            throw new FichaSaudeInvalidaException("A ficha precisa de um nome.");
        }
        nome = nome.trim();
        if (nome.length() > NOME_MAX) {
            throw new FichaSaudeInvalidaException(
                    "O nome da ficha deve ter até " + NOME_MAX + " caracteres.");
        }

        if (observacoes != null) {
            observacoes = observacoes.trim();
            if (observacoes.length() > OBS_MAX) {
                throw new FichaSaudeInvalidaException(
                        "As observações devem ter até " + OBS_MAX + " caracteres.");
            }
        }
        anexos = anexos == null ? List.of() : List.copyOf(anexos);
        if (anexos.size() > MAX_ANEXOS) {
            throw new FichaSaudeInvalidaException(
                    "A ficha aceita no máximo " + MAX_ANEXOS + " anexos.");
            }
        }

        public static FichaSaude criar(
                UUID alunoId, String nome, String observacoes,
                List<Anexo> anexos, Instant agora
            ) {
                return  new FichaSaude(
                        UUID.randomUUID(), alunoId, nome, observacoes,
                        anexos, agora, agora
                );
    }

    public FichaSaude comAlteracoes(
            String novoNome, String novasObservacoes,
            List<Anexo> novosAnexos, Instant agora
    ){
        return new FichaSaude(
                this.id, this.alunoId, novoNome, novasObservacoes,
                novosAnexos, this.dataCriacao, agora
        );
    }

    public boolean pertenceAo(UUID alunoIdEsperado) {
        return this.alunoId.equals(alunoIdEsperado);
    }
}
