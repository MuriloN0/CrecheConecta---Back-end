package com.crecheconecta.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MarcarItemMalaRequest(@NotNull Boolean enviado, @Size(max = 500) String observacao) {
}
