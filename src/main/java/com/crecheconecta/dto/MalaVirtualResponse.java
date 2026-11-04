package com.crecheconecta.dto;

import com.crecheconecta.entity.ItemMala;
import com.crecheconecta.entity.MalaVirtual;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Mala Virtual que o RESPONSAVEL vê: o que precisa mandar e quanto ainda falta enviar.
 */
public record MalaVirtualResponse(UUID id, LocalDate data, String observacao, long itensPendentes, List<Item> itens) {

    public record Item(UUID id, String nome, int quantidade, boolean enviado) {

        public static Item de(ItemMala item) {
            return new Item(item.getId(), item.getNome(), item.getQuantidade(), item.isEnviado());
        }
    }

    public static MalaVirtualResponse de(MalaVirtual mala, List<ItemMala> itensDaMala) {
        var itens = itensDaMala.stream().sorted(Comparator.comparing(ItemMala::getNome, String.CASE_INSENSITIVE_ORDER)).map(Item::de).toList();
        long pendentes = itens.stream().filter(item -> !item.enviado()).count();

        return new MalaVirtualResponse(mala.getId(), mala.getData(), mala.getObservacao(), pendentes, itens);
    }
}
