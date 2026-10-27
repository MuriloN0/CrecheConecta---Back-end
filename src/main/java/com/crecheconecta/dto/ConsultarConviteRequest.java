package com.crecheconecta.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ConsultarConviteRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String token) {

    @Override
    public String toString() {
        return "ConsultarConviteRequest[dados omitidos]";
    }

}