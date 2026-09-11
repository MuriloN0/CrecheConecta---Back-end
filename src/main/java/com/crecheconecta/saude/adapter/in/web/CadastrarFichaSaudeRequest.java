package com.crecheconecta.saude.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CadastrarFichaSaudeRequest(
        @NotBlank @Size(max = 120) String nome,
        @Size(max = 5000) String observacoes
){ }
