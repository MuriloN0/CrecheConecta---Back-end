package com.crecheconecta.dto;

import com.crecheconecta.entity.Alimentacao;
import com.crecheconecta.entity.DiarioRotina;
import com.crecheconecta.entity.Higiene;
import com.crecheconecta.entity.Humor;
import com.crecheconecta.entity.Sono;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Diário de Rotina como o PROFESSOR vê. Traz a "versao" (para editar) e "editavel"
 * (só o professor que criou o diário pode alterá-lo ou excluí-lo).
 */
public record DiarioRegistroResponse(
        UUID id,
        UUID turmaId,
        UUID alunoId,
        LocalDate data,
        Alimentacao alimentacao,
        Sono sono,
        boolean evacuacao,
        Humor humor,
        Higiene higiene,
        String observacaoProfessor,
        boolean editavel,
        Long versao) {

    public static DiarioRegistroResponse de(DiarioRotina diario, UUID professorLogado) {
        return new DiarioRegistroResponse(
                diario.getId(),
                diario.getTurmaId(),
                diario.getAlunoId(),
                diario.getData(),
                diario.getAlimentacao(),
                diario.getSono(),
                diario.isEvacuacao(),
                diario.getHumor(),
                diario.getHigiene(),
                diario.getObservacaoProfessor(),
                diario.getProfessorId().equals(professorLogado),
                diario.getVersao()
        );
    }
}
