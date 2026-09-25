package com.crecheconecta.dto;

import com.crecheconecta.entity.Perfil;

import java.util.UUID;

public record UsuarioLogadoResponse(UUID id, String nome, String email, Perfil perfil) {
}
