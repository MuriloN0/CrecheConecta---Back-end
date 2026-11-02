package com.crecheconecta.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record VincularResponsavelRequest(@NotNull UUID responsavelId) {
}