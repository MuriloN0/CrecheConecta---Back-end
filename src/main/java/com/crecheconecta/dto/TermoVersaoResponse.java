package com.crecheconecta.dto;

import com.crecheconecta.entity.StatusTermo;
import com.crecheconecta.entity.TermoVersao;

import java.time.Instant;
import java.util.UUID;

/**
 * Versão dos termos como a DIRECAO vê. "aceites" = quantos usuários diferentes já aceitaram esta versão.
 */
public record TermoVersaoResponse(
        UUID id,
        String identificador,
        StatusTermo status,
        String termoUso,
        String politicaPrivacidade,
        Instant criadoEm,
        Instant atualizadoEm,
        Instant publicadoEm,
        long aceites,
        Long versao
) {

    public static TermoVersaoResponse de(TermoVersao termo, long aceites) {
        return new TermoVersaoResponse(
                termo.getId(),
                termo.getIdentificador(),
                termo.getStatus(),
                termo.getTermoUso(),
                termo.getPoliticaPrivacidade(),
                termo.getCriadoEm(),
                termo.getAtualizadoEm(),
                termo.getPublicadoEm(),
                aceites,
                termo.getVersao()
        );
    }
}
