package com.crecheconecta.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record ConfirmarRecuperacaoRequest(@NotNull UUID acaoId, @NotBlank @Pattern(regexp = "[0-9]{6}") String codigo) {

    @Override
    public String toString() {
        return "ConfirmarRecuperacaoRequest[dados omitidos]";
    }

}
