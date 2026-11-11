package com.crecheconecta.dto;

import jakarta.validation.constraints.NotNull;

public record CancelarSolicitacaoMaterialRequest(
        @NotNull Long versao
) {
}
