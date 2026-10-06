package com.crecheconecta.dto;

import com.crecheconecta.entity.FichaSaude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FichaSaudeResponse(
        UUID id,
        UUID alunoId,
        String nome,
        String observacoes,
        Long versao,
        Instant dataCriacao,
        Instant dataAtualizacao,
        List<AnexoResponse> anexos) {

    public record AnexoResponse(UUID id, String nomeArquivo, String tipoConteudo, long tamanhoBytes) {
    }

    public static FichaSaudeResponse de(FichaSaude ficha, String observacoesDecifradas) {
        List<AnexoResponse> anexos =
                ficha.getAnexos().stream()
                        .map(a -> new AnexoResponse(a.getId(), a.getNomeArquivo(), a.getTipoConteudo(), a.getTamanhoBytes()))
                        .toList();
        return new FichaSaudeResponse(
                ficha.getId(),
                ficha.getAlunoId(),
                ficha.getNome(),
                observacoesDecifradas,
                ficha.getVersao(),
                ficha.getDataCriacao(),
                ficha.getDataAtualizacao(),
                anexos);
    }
}