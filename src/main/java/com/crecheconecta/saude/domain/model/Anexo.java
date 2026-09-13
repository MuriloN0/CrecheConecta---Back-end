package com.crecheconecta.saude.domain.model;

import com.crecheconecta.saude.application.exception.FichaSaudeInvalidaException;

import java.util.Objects;
import java.util.UUID;

public record Anexo (
        UUID id,
        String nomeArquivo,
        String chaveArmazenamento,
        String tipoConteudo,
        long tamanhoBytes
) {
    public Anexo {
        Objects.requireNonNull(id, "id é obrigatório");
        if (nomeArquivo == null || nomeArquivo.isBlank()) {
            throw new FichaSaudeInvalidaException("Anexo sem nome de arquivo.");
        }
        Objects.requireNonNull(chaveArmazenamento, "chaveArmazenamento é obrigatória");
        if (tamanhoBytes <= 0) {
            throw new FichaSaudeInvalidaException("Anexo vazio não é permitido.");
        }
    }
}
