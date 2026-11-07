package com.crecheconecta.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EnviarMensagemSolicitacaoMaterialRequest(
        @NotBlank @Size(max = 2000) String conteudo
) {
}
