package com.crecheconecta.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConvidarProfessorRequest(@NotBlank @Size(max = 150) String nome, @NotBlank @Email @Size(max = 254) String email) {


}
