package com.crecheconecta.dto;

import com.crecheconecta.entity.Alimentacao;
import com.crecheconecta.entity.DiarioRotina;
import com.crecheconecta.entity.Higiene;
import com.crecheconecta.entity.Humor;
import com.crecheconecta.entity.Sono;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Diário de Rotina que o RESPONSAVEL vê no portal.
 */
public record DiarioRotinaResponse(UUID id, UUID alunoId, LocalDate data, Alimentacao alimentacao, Sono sono, boolean evacuacao, Humor humor, Higiene higiene, String observacaoProfessor) {

    public static DiarioRotinaResponse de(DiarioRotina diario) {
        return new DiarioRotinaResponse(diario.getId(), diario.getAlunoId(), diario.getData(), diario.getAlimentacao(), diario.getSono(), diario.isEvacuacao(), diario.getHumor(), diario.getHigiene(), diario.getObservacaoProfessor());
    }
}
