package com.crecheconecta.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AtenderSolicitacaoMaterialRequest(
        @Size(max = 2000) String mensagem,
        @NotNull Long versao
) {
}
