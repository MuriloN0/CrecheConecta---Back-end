package com.crecheconecta.dto;

import com.crecheconecta.entity.ItemSolicitacaoMaterial;
import com.crecheconecta.entity.PrioridadeSolicitacaoMaterial;
import com.crecheconecta.entity.SolicitacaoMaterial;
import com.crecheconecta.entity.StatusSolicitacaoMaterial;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SolicitacaoMaterialResponse(
        UUID id,
        UUID professorId,
        String professorNome,
        String sala,
        String titulo,
        String descricao,
        PrioridadeSolicitacaoMaterial prioridade,
        StatusSolicitacaoMaterial status,
        Instant criadoEm,
        Instant atualizadoEm,
        Instant finalizadoEm,
        Long versao,
        List<Item> itens
) {

    public record Item(
            UUID id,
            String nome,
            int quantidade
    ) {

        public static Item de(ItemSolicitacaoMaterial item) {

            return new Item(item.getId(), item.getNome(), item.getQuantidade());
        }
    }

    public static SolicitacaoMaterialResponse de(
            SolicitacaoMaterial solicitacao,
            List<ItemSolicitacaoMaterial> itensDaSolicitacao,
            String professorNome
    ) {

        List<Item> itens = itensDaSolicitacao.stream()
                .sorted(ItemSolicitacaoMaterial.POR_NOME)
                .map(Item::de)
                .toList();

        return new SolicitacaoMaterialResponse(
                solicitacao.getId(),
                solicitacao.getProfessorId(),
                professorNome,
                solicitacao.getSala(),
                solicitacao.getTitulo(),
                solicitacao.getDescricao(),
                solicitacao.getPrioridade(),
                solicitacao.getStatus(),
                solicitacao.getCriadoEm(),
                solicitacao.getAtualizadoEm(),
                solicitacao.getFinalizadoEm(),
                solicitacao.getVersao(),
                itens
        );
    }
}
