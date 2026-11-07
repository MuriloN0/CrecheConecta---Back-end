package com.crecheconecta.dto;

import com.crecheconecta.entity.PrioridadeSolicitacaoMaterial;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AtualizarSolicitacaoMaterialRequest(
        @NotBlank @Size(max = 150) String titulo,
        @Size(max = 2000) String descricao,
        @NotNull PrioridadeSolicitacaoMaterial prioridade,
        @NotBlank @Size(max = 50) String sala,
        @NotEmpty @Size(max = 50) List<@Valid @NotNull ItemSolicitacaoMaterialRequest> itens,
        @NotNull Long versao
) {
}
