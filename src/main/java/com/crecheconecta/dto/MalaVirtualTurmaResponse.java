package com.crecheconecta.dto;

import com.crecheconecta.entity.ItemMala;
import com.crecheconecta.entity.MalaVirtual;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record MalaVirtualTurmaResponse(UUID id, UUID turmaId, UUID professorId, String titulo, LocalDate data, String observacao, Instant criadoEm, Instant atualizadoEm, Long versao, List<Item> itens) {

    public record Item(UUID id, String nome, int quantidade) {

        public static Item de(ItemMala item) {
            return new Item(item.getId(), item.getNome(), item.getQuantidade());
        }
    }

    public static MalaVirtualTurmaResponse de(MalaVirtual mala, List<ItemMala> itensDaMala) {
        var itens = itensDaMala.stream().sorted(ItemMala.POR_NOME).map(Item::de).toList();

        return new MalaVirtualTurmaResponse(mala.getId(), mala.getTurmaId(), mala.getProfessorId(), mala.getTitulo(), mala.getData(), mala.getObservacao(), mala.getCriadoEm(), mala.getAtualizadoEm(), mala.getVersao(), itens);
    }
}
