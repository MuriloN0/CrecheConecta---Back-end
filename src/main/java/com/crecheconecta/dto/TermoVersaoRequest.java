package com.crecheconecta.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Corpo para criar um rascunho de termos.
 * O identificador é o nome da versão que o usuário aceita (ex.: 2026-11-10-v3).
 */
public record TermoVersaoRequest(
        @NotBlank @Size(max = 40) @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "Use apenas letras, números, ponto, hífen e sublinhado.") String identificador,
        @NotBlank @Size(max = 20000) String termoUso,
        @NotBlank @Size(max = 20000) String politicaPrivacidade
) {
}
