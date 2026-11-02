package com.crecheconecta.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConvidarDirecaoRequest(@NotBlank @Size(max = 150) String nome, @NotBlank @Email @Size(max = 254) String email) {
}