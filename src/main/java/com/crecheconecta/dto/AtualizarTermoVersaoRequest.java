package com.crecheconecta.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;


public record AtualizarTermoVersaoRequest(@NotBlank @Size(max = 20000) String termoUso, @NotBlank @Size(max = 20000) String politicaPrivacidade, @NotNull @PositiveOrZero Long versao) {

}