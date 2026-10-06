package com.crecheconecta.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record RedefinirSenhaRequest(@NotNull UUID redefinicaoId, @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String tokenRedefinicao, @NotBlank @Size(max = 72) String novaSenha, @NotBlank @Size(max = 72) String confirmacaoSenha) {

    @Override
    public String toString() {
        return "RedefinirSenhaRequest[dados omitidos]";
    }

}
