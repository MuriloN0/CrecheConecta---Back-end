package com.crecheconecta.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Corpo para editar um rascunho. "versao" evita sobrescrever a edição de outra pessoa. */
public record AtualizarTermoVersaoRequest(
        @NotBlank @Size(max = 40) @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "Use apenas letras, números, ponto, hífen e sublinhado.") String identificador,
        @NotBlank @Size(max = 20000) String termoUso,
        @NotBlank @Size(max = 20000) String politicaPrivacidade,
        @NotNull @PositiveOrZero Long versao
) {
}
