package com.crecheconecta.service;

import com.crecheconecta.entity.ItemMala;
import com.crecheconecta.entity.MalaVirtual;
import com.crecheconecta.entity.MarcacaoItemMala;
import com.crecheconecta.repository.ItemMalaRepository;
import com.crecheconecta.repository.MarcacaoItemMalaRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class MalaVirtualSuporte {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    private final ItemMalaRepository itens;
    private final MarcacaoItemMalaRepository marcacoes;
    private final Clock clock;

    public MalaVirtualSuporte(ItemMalaRepository itens, MarcacaoItemMalaRepository marcacoes, Clock clock) {

        this.itens = itens;
        this.marcacoes = marcacoes;
        this.clock = clock;
    }

    public Map<UUID, List<ItemMala>> itensPorMala(List<MalaVirtual> malas) {

        if (malas.isEmpty()) {

            return Map.of();
        }

        return itens.findByMalaIdIn(malas.stream().map(MalaVirtual::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(ItemMala::getMalaId));
    }

    public Map<UUID, Map<UUID, MarcacaoItemMala>> marcacoesPorAluno(List<ItemMala> itensDaMala) {

        if (itensDaMala.isEmpty()) {

            return Map.of();
        }

        return marcacoes.findByItemIdIn(idsDos(itensDaMala))
                .stream()
                .collect(Collectors.groupingBy(
                        MarcacaoItemMala::getAlunoId,
                        Collectors.toMap(MarcacaoItemMala::getItemId, Function.identity())
                ));
    }

    public Map<UUID, MarcacaoItemMala> marcacoesDoAluno(UUID alunoId, List<ItemMala> itensDaMala) {

        if (itensDaMala.isEmpty()) {

            return Map.of();
        }

        return marcacoes.findByAlunoIdAndItemIdIn(alunoId, idsDos(itensDaMala))
                .stream()
                .collect(Collectors.toMap(MarcacaoItemMala::getItemId, Function.identity()));
    }

    public LocalDate hoje() {

        return LocalDate.ofInstant(clock.instant(), FUSO);
    }

    public static String textoOpcional(String texto) {

        return texto == null || texto.isBlank() ? null : texto.strip();
    }

    private List<UUID> idsDos(List<ItemMala> itensDaMala) {

        return itensDaMala.stream().map(ItemMala::getId).toList();
    }
}
