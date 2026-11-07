package com.crecheconecta.dto;

import com.crecheconecta.entity.ItemMala;
import com.crecheconecta.entity.MalaVirtual;
import com.crecheconecta.entity.MarcacaoItemMala;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record MalaVirtualResponse(UUID id, String titulo, LocalDate data, String observacao, boolean podeMarcar, long itensPendentes, List<Item> itens) {

    public record Item(UUID id, String nome, int quantidade, boolean enviado, String observacao) {

        public static Item de(ItemMala item, MarcacaoItemMala marcacao) {
            return marcacao == null
                    ? new Item(item.getId(), item.getNome(), item.getQuantidade(), false, null)
                    : new Item(item.getId(), item.getNome(), item.getQuantidade(), marcacao.isEnviado(), marcacao.getObservacao());
        }
    }

    public static MalaVirtualResponse de(MalaVirtual mala, List<ItemMala> itensDaMala, Map<UUID, MarcacaoItemMala> marcacoesPorItem, LocalDate hoje) {
        var itens = itensDaMala.stream().sorted(ItemMala.POR_NOME).map(item -> Item.de(item, marcacoesPorItem.get(item.getId()))).toList();
        long pendentes = itens.stream().filter(item -> !item.enviado()).count();

        return new MalaVirtualResponse(mala.getId(), mala.getTitulo(), mala.getData(), mala.getObservacao(), !mala.getData().isBefore(hoje), pendentes, itens);
    }
}
