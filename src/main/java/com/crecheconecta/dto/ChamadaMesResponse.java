package com.crecheconecta.dto;

import com.crecheconecta.entity.Presenca;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Faltas e presenças do filho em um mês: o resumo (contagens) + os registros dia a dia.
 * Os dados vêm da tabela "presencas", a mesma que o professor preenche na chamada.
 * Registro "pendente" (aluno ainda não marcado, presente = null) não conta como presença nem como falta.
 */
public record ChamadaMesResponse(UUID alunoId, YearMonth mes, long presencas, long faltas, List<Registro> registros) {

    public record Registro(LocalDate data, boolean presente) {

        public static Registro de(Presenca presenca) {
            return new Registro(presenca.getData(), Boolean.TRUE.equals(situacao(presenca)));
        }
    }

    public static ChamadaMesResponse de(UUID alunoId, YearMonth mes, List<Presenca> presencas) {
        List<Presenca> marcadas = presencas.stream().filter(p -> situacao(p) != null).toList();
        long totalPresentes = marcadas.stream().filter(p -> Boolean.TRUE.equals(situacao(p))).count();
        long totalFaltas = marcadas.size() - totalPresentes;

        return new ChamadaMesResponse(alunoId, mes, totalPresentes, totalFaltas, marcadas.stream().map(Registro::de).toList());
    }

    // Boolean (e não boolean) de propósito: a coluna aceita nulo para "pendente".
    private static Boolean situacao(Presenca presenca) {
        return presenca.isPresente();
    }
}
