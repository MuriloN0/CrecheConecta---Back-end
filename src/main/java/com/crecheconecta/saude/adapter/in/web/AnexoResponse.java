package com.crecheconecta.saude.adapter.in.web;

import com.crecheconecta.saude.domain.model.Anexo;

import java.util.UUID;

public record AnexoResponse (
    UUID id,
    String nomeArquivo,
    String tipoConteudo,
    long tamanhoBytes
) {
    public static AnexoResponse de(Anexo anexo) {
        return new AnexoResponse(
                anexo.id(), anexo.nomeArquivo(), anexo.tipoConteudo(), anexo.tamanhoBytes());
    }
}