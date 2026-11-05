package com.crecheconecta.dto;

import com.crecheconecta.entity.Chamada;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Faltas e presenças do filho em um mês: o resumo (contagens) + os registros dia a dia.
 */
public record ChamadaMesResponse(UUID alunoId, YearMonth mes, long presencas, long faltas, long faltasJustificadas, List<Registro> registros) {

    public record Registro(LocalDate data, boolean presente, String justificativa) {

        public static Registro de(Chamada chamada) {
            return new Registro(chamada.getData(), chamada.isPresente(), chamada.getJustificativa());
        }
    }

    public static ChamadaMesResponse de(UUID alunoId, YearMonth mes, List<Chamada> chamadas) {
        long presencas = chamadas.stream().filter(Chamada::isPresente).count();
        long faltas = chamadas.size() - presencas;
        long faltasJustificadas = chamadas.stream().filter(c -> !c.isPresente() && c.getJustificativa() != null).count();

        return new ChamadaMesResponse(alunoId, mes, presencas, faltas, faltasJustificadas, chamadas.stream().map(Registro::de).toList());
    }
}
