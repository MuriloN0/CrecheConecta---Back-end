package com.crecheconecta.dto;

import com.crecheconecta.entity.Comunicado;

import java.time.Instant;
import java.util.UUID;

public record ComunicadoResponse(UUID id, String titulo, String conteudo, Instant criadoEm, Instant atualizadoEm, boolean ativo, Long versao) {

    public static ComunicadoResponse de(Comunicado comunicado) {

        return new ComunicadoResponse(
                comunicado.getId(),
                comunicado.getTitulo(),
                comunicado.getConteudo(),
                comunicado.getCriadoEm(),
                comunicado.getAtualizadoEm(),
                comunicado.isAtivo(),
                comunicado.getVersao()
        );
    }
}