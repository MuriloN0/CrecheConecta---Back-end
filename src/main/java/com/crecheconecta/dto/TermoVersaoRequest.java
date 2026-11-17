package com.crecheconecta.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TermoVersaoRequest(@NotBlank @Size(max = 20000) String termoUso, @NotBlank @Size(max = 20000) String politicaPrivacidade) {

}