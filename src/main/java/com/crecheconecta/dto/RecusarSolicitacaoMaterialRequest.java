package com.crecheconecta.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RecusarSolicitacaoMaterialRequest(
        @NotBlank @Size(max = 2000) String motivo,
        @NotNull Long versao
) {
}
