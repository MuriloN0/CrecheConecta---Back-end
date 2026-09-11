package com.crecheconecta.saude.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record AtualizarFichaSaudeRequest(
        @NotBlank @Size(max = 120) String nome,
        @Size(max = 5000) String observacoes,
        List<UUID> anexoMantidos
){
    public List<UUID> anexosMantidos() {
        return null;
    }
}
