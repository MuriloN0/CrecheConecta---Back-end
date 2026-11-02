package com.crecheconecta.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CriarComunicadoRequest(@NotBlank @Size(max = 150) String titulo, @NotBlank @Size(max = 5000) String conteudo) {
}