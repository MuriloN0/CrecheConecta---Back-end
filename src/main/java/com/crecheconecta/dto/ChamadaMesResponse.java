package com.crecheconecta.dto;

import com.crecheconecta.entity.Presenca;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Faltas e presenças do filho em um mês: o resumo (contagens) + os registros dia a dia.
 * Os dados vêm da tabela "presencas", a mesma que o professor preenche na chamada.
 */
public record ChamadaMesResponse(UUID alunoId, YearMonth mes, long presencas, long faltas, List<Registro> registros) {

    public record Registro(LocalDate data, boolean presente) {

        public static Registro de(Presenca presenca) {
            return new Registro(presenca.getData(), presenca.isPresente());
        }
    }

    public static ChamadaMesResponse de(UUID alunoId, YearMonth mes, List<Presenca> presencas) {
        long totalPresentes = presencas.stream().filter(Presenca::isPresente).count();
        long totalFaltas = presencas.size() - totalPresentes;

        return new ChamadaMesResponse(alunoId, mes, totalPresentes, totalFaltas, presencas.stream().map(Registro::de).toList());
    }
}
