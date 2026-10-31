package com.crecheconecta.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record VincularProfessorTurmaRequest(@NotNull UUID professorId) {
}