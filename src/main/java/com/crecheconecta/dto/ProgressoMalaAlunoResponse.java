package com.crecheconecta.dto;

import com.crecheconecta.entity.Aluno;
import com.crecheconecta.entity.ItemMala;
import com.crecheconecta.entity.MarcacaoItemMala;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ProgressoMalaAlunoResponse(UUID alunoId, String nome, long itensEnviados, int totalItens, boolean completo, List<Item> itens) {

    public record Item(UUID itemId, String nome, int quantidade, boolean enviado, String observacao, Instant atualizadoEm) {

        static Item de(ItemMala item, MarcacaoItemMala marcacao) {
            return marcacao == null
                    ? new Item(item.getId(), item.getNome(), item.getQuantidade(), false, null, null)
                    : new Item(item.getId(), item.getNome(), item.getQuantidade(), marcacao.isEnviado(), marcacao.getObservacao(), marcacao.getAtualizadoEm());
        }
    }

    public static ProgressoMalaAlunoResponse de(Aluno aluno, List<ItemMala> itensDaMala, Map<UUID, MarcacaoItemMala> marcacoesPorItem) {
        var itens = itensDaMala.stream().sorted(ItemMala.POR_NOME).map(item -> Item.de(item, marcacoesPorItem.get(item.getId()))).toList();
        long enviados = itens.stream().filter(Item::enviado).count();

        return new ProgressoMalaAlunoResponse(aluno.getId(), aluno.getNome(), enviados, itens.size(), enviados == itens.size(), itens);
    }
}
